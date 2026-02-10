mod api;
mod config;
mod domain;
mod errors;
mod infrastructure;
mod middleware;
mod services;

use axum::{
    middleware::from_fn_with_state,
    routing::{get, post, put, delete},
    Router,
};
use sqlx::PgPool;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;

use crate::config::Config;
use crate::services::auth_service::AuthService;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "lily_ai_backend=debug,tower_http=debug".into()),
        )
        .init();

    let config = Config::from_env().expect("Failed to load configuration");
    let pool = infrastructure::db::create_pool(&config.database_url)
        .await
        .expect("Failed to create database pool");

    // Run migrations
    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .expect("Failed to run migrations");

    let auth_service = AuthService::new(config.clone());
    let auth_state = (auth_service.clone(), pool.clone());

    // Initialize meeting service if S3 bucket configured
    let meeting_service: Option<services::meeting_service::MeetingService> = if let Some(bucket) = &config.meeting_audio_s3_bucket {
        Some(services::meeting_service::MeetingService::new(bucket.clone(), config.cognito_region.clone()).await)
    } else {
        tracing::warn!("MEETING_AUDIO_S3_BUCKET not set – meeting audio upload disabled");
        None
    };
    let meeting_state = (pool.clone(), meeting_service.clone());

    // Notification registration needs both pool and config
    let notification_routes = Router::new()
        .route(
            "/notifications/register",
            post(api::daily_status::register_device),
        )
        .with_state((pool.clone(), config.clone()));

    // Meeting notes routes (some need meeting_service for S3/Transcribe)
    let meeting_routes = Router::new()
        .route("/meeting-notes", post(api::meeting_notes::create_meeting_note).get(api::meeting_notes::list_meeting_notes))
        .with_state(pool.clone())
        .merge(
            Router::new()
                .route("/meeting-notes/:id", get(api::meeting_notes::get_meeting_note).delete(api::meeting_notes::delete_meeting_note))
                .route("/meeting-notes/:id/upload", post(api::meeting_notes::upload_audio))
                .route("/meeting-notes/:id/transcription", get(api::meeting_notes::check_transcription))
                .route("/meeting-notes/:id/photos", post(api::meeting_photos::upload_photo).get(api::meeting_photos::list_photos))
                .route("/meeting-notes/:meeting_id/photos/:photo_id", delete(api::meeting_photos::delete_photo))
                .with_state(meeting_state)
        );

    // Protected API routes (pool-only state)
    let api_routes = Router::new()
        .route("/auth/me", get(api::auth::me))
        .route("/users/profile", put(api::users::update_profile))
        .route("/expenses", post(api::expenses::create_expense))
        .route("/expenses", get(api::expenses::list_expenses))
        .route("/expenses/:id", get(api::expenses::get_expense))
        .route("/expenses/:id", put(api::expenses::update_expense))
        .route("/expenses/:id", delete(api::expenses::delete_expense))
        .route("/analytics/daily", get(api::analytics::daily))
        .route("/analytics/weekly", get(api::analytics::weekly))
        .route("/analytics/monthly", get(api::analytics::monthly))
        .route("/analytics/category", get(api::analytics::category))
        .route("/daily-status", get(api::daily_status::check_status))
        .route("/daily-status/submit", post(api::daily_status::submit_day))
        .route("/budget", get(api::budget::get_budget).put(api::budget::set_budget))
        .with_state(pool.clone())
        .merge(notification_routes)
        .merge(meeting_routes)
        .layer(from_fn_with_state(auth_state, middleware::auth::auth_middleware));

    let app = Router::new()
        .nest("/api", api_routes)
        .route("/health", get(|| async { "OK" }))
        .layer(
            CorsLayer::new()
                .allow_origin(Any)
                .allow_methods(Any)
                .allow_headers(Any),
        )
        .layer(TraceLayer::new_for_http());

    let addr = format!("{}:{}", config.host, config.port);
    tracing::info!("Starting server on {}", addr);

    let listener = tokio::net::TcpListener::bind(&addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
