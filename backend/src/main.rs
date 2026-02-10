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

    // Notification registration needs both pool and config
    let notification_routes = Router::new()
        .route(
            "/notifications/register",
            post(api::daily_status::register_device),
        )
        .with_state((pool.clone(), config.clone()));

    // Protected API routes (pool-only state)
    let api_routes = Router::new()
        .route("/auth/me", get(api::auth::me))
        .route("/users/profile", put(api::users::update_profile))
        .route("/expenses", post(api::expenses::create_expense))
        .route("/expenses", get(api::expenses::list_expenses))
        .route("/expenses/{id}", get(api::expenses::get_expense))
        .route("/expenses/{id}", put(api::expenses::update_expense))
        .route("/expenses/{id}", delete(api::expenses::delete_expense))
        .route("/analytics/daily", get(api::analytics::daily))
        .route("/analytics/weekly", get(api::analytics::weekly))
        .route("/analytics/monthly", get(api::analytics::monthly))
        .route("/analytics/category", get(api::analytics::category))
        .route("/daily-status", get(api::daily_status::check_status))
        .route("/daily-status/submit", post(api::daily_status::submit_day))
        .with_state(pool.clone())
        .merge(notification_routes)
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
