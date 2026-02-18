use axum::{
    extract::{ws, State, WebSocketUpgrade},
    response::IntoResponse,
    Extension,
};
use futures::{SinkExt, StreamExt};
use serde_json::{json, Value};
use sqlx::PgPool;
use tokio::sync::mpsc;
use tokio_tungstenite::{connect_async, tungstenite};

use crate::errors::AppError;
use crate::middleware::auth::AuthenticatedUser;
use crate::services::lily_tools;

const OPENAI_REALTIME_URL: &str = "wss://api.openai.com/v1/realtime?model=gpt-4o-mini-realtime-preview";

const LILY_INSTRUCTIONS: &str = r#"You are "Lily poo", a friendly personal AI assistant. You help the user track their budget, review meeting notes, and stay productive.

Behavior:
- Be warm, concise, and helpful. Use a casual but professional tone.
- When asked about meetings, use the meeting tools to fetch real data, then summarize the key points conversationally. Do NOT read full transcripts verbatim — summarize them.
- When asked about spending or budget, use the budget tools to fetch real data, then explain it clearly.
- If a query is unclear, ask for clarification.
- Always respond in English.
- Amounts are in Indian Rupees (₹ / INR).
- When summarizing meetings, highlight action items, decisions, and key topics.
- Keep voice responses short and natural — under 30 seconds ideally."#;

pub async fn ws_handler(
    ws: WebSocketUpgrade,
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
) -> impl IntoResponse {
    let user_id = user.id;
    ws.on_upgrade(move |socket| handle_session(socket, pool, user_id))
}

async fn handle_session(socket: ws::WebSocket, pool: PgPool, user_id: uuid::Uuid) {
    let api_key = std::env::var("OPENAI_API_KEY").unwrap_or_default();
    if api_key.is_empty() {
        tracing::error!("OPENAI_API_KEY not set");
        return;
    }

    // Connect to OpenAI Realtime API
    let request = tungstenite::http::Request::builder()
        .uri(OPENAI_REALTIME_URL)
        .header("Authorization", format!("Bearer {}", api_key))
        .header("OpenAI-Beta", "realtime=v1")
        .header("Host", "api.openai.com")
        .header("Connection", "Upgrade")
        .header("Upgrade", "websocket")
        .header("Sec-WebSocket-Version", "13")
        .header("Sec-WebSocket-Key", tungstenite::handshake::client::generate_key())
        .body(())
        .unwrap();

    let (openai_ws, _) = match connect_async(request).await {
        Ok(conn) => conn,
        Err(e) => {
            tracing::error!("Failed to connect to OpenAI Realtime: {}", e);
            return;
        }
    };

    tracing::info!("Connected to OpenAI Realtime API for user {}", user_id);

    let (mut openai_sink, mut openai_stream) = openai_ws.split();
    let (mut client_sink, mut client_stream) = socket.split();

    // Configure session with tools
    let tools = lily_tools::tool_definitions();
    let session_config = json!({
        "type": "session.update",
        "session": {
            "instructions": LILY_INSTRUCTIONS,
            "tools": tools,
            "tool_choice": "auto",
            "input_audio_format": "pcm16",
            "output_audio_format": "pcm16",
            "voice": "shimmer",
            "turn_detection": {
                "type": "server_vad",
                "threshold": 0.5,
                "prefix_padding_ms": 300,
                "silence_duration_ms": 500,
            },
        }
    });

    if let Err(e) = openai_sink.send(tungstenite::Message::Text(session_config.to_string())).await {
        tracing::error!("Failed to configure session: {}", e);
        return;
    }

    // Channel for tool call results to send back to OpenAI
    let (tool_tx, mut tool_rx) = mpsc::channel::<String>(32);

    let pool_clone = pool.clone();

    // Task: Forward client messages → OpenAI
    let client_to_openai = tokio::spawn(async move {
        let mut openai_sink = openai_sink;
        loop {
            tokio::select! {
                msg = client_stream.next() => {
                    match msg {
                        Some(Ok(ws::Message::Text(text))) => {
                            if let Err(e) = openai_sink.send(tungstenite::Message::Text(text.to_string())).await {
                                tracing::error!("Error forwarding to OpenAI: {}", e);
                                break;
                            }
                        }
                        Some(Ok(ws::Message::Binary(data))) => {
                            // Binary audio from client → forward as audio input event
                            let audio_b64 = base64::Engine::encode(
                                &base64::engine::general_purpose::STANDARD, &data
                            );
                            let event = json!({
                                "type": "input_audio_buffer.append",
                                "audio": audio_b64,
                            });
                            if let Err(e) = openai_sink.send(tungstenite::Message::Text(event.to_string())).await {
                                tracing::error!("Error sending audio to OpenAI: {}", e);
                                break;
                            }
                        }
                        Some(Ok(ws::Message::Close(_))) | None => break,
                        _ => {}
                    }
                }
                tool_result = tool_rx.recv() => {
                    if let Some(result_msg) = tool_result {
                        if let Err(e) = openai_sink.send(tungstenite::Message::Text(result_msg)).await {
                            tracing::error!("Error sending tool result to OpenAI: {}", e);
                            break;
                        }
                    }
                }
            }
        }
    });

    // Task: Forward OpenAI messages → client (and handle tool calls)
    let openai_to_client = tokio::spawn(async move {
        while let Some(msg) = openai_stream.next().await {
            match msg {
                Ok(tungstenite::Message::Text(text)) => {
                    if let Ok(event) = serde_json::from_str::<Value>(&text) {
                        let event_type = event.get("type").and_then(|t| t.as_str()).unwrap_or("");

                        match event_type {
                            // Tool call from OpenAI — execute and respond
                            "response.function_call_arguments.done" => {
                                let call_id = event.get("call_id").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let name = event.get("name").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                let arguments = event.get("arguments").and_then(|v| v.as_str()).unwrap_or("{}").to_string();

                                tracing::info!("Tool call: {} with args: {}", name, arguments);

                                let args: Value = serde_json::from_str(&arguments).unwrap_or(json!({}));
                                let result = lily_tools::execute_tool(&pool_clone, user_id, &name, &args).await
                                    .unwrap_or_else(|e| json!({"error": e.to_string()}).to_string());

                                // Send function call output back to OpenAI
                                let output_event = json!({
                                    "type": "conversation.item.create",
                                    "item": {
                                        "type": "function_call_output",
                                        "call_id": call_id,
                                        "output": result,
                                    }
                                });
                                let _ = tool_tx.send(output_event.to_string()).await;

                                // Tell OpenAI to generate a response
                                let respond_event = json!({
                                    "type": "response.create",
                                });
                                let _ = tool_tx.send(respond_event.to_string()).await;
                            }

                            // Audio delta — forward to client as binary
                            "response.audio.delta" | "response.output_audio.delta" => {
                                if let Some(audio_b64) = event.get("delta").and_then(|v| v.as_str()) {
                                    if let Ok(audio_bytes) = base64::Engine::decode(
                                        &base64::engine::general_purpose::STANDARD, audio_b64
                                    ) {
                                        let _ = client_sink.send(ws::Message::Binary(audio_bytes.into())).await;
                                    }
                                }
                            }

                            // Forward other events as text
                            _ => {
                                let _ = client_sink.send(ws::Message::Text(text.into())).await;
                            }
                        }
                    }
                }
                Ok(tungstenite::Message::Close(_)) => break,
                Err(e) => {
                    tracing::error!("OpenAI WS error: {}", e);
                    break;
                }
                _ => {}
            }
        }
    });

    // Wait for either task to finish
    tokio::select! {
        _ = client_to_openai => {},
        _ = openai_to_client => {},
    }

    tracing::info!("Lily session ended for user {}", user_id);
}
