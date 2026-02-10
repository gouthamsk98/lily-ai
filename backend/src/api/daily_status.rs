use axum::{extract::State, Extension, Json};
use serde::{Deserialize, Serialize};
use sqlx::PgPool;

use crate::errors::AppError;
use crate::middleware::auth::AuthenticatedUser;
use crate::services::{daily_check_service, notification_service};
use crate::config::Config;

#[derive(Debug, Serialize)]
pub struct DailyStatusResponse {
    pub submitted: bool,
    pub date: String,
}

#[derive(Debug, Deserialize)]
pub struct SubmitRequest {
    pub date: Option<chrono::NaiveDate>,
}

#[derive(Debug, Deserialize)]
pub struct RegisterDeviceRequest {
    pub device_token: String,
}

pub async fn check_status(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
) -> Result<Json<DailyStatusResponse>, AppError> {
    let submitted = daily_check_service::check_submitted_today(&pool, user.id).await?;
    let today = chrono::Utc::now().date_naive();
    Ok(Json(DailyStatusResponse {
        submitted,
        date: today.to_string(),
    }))
}

pub async fn submit_day(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Json(input): Json<SubmitRequest>,
) -> Result<Json<DailyStatusResponse>, AppError> {
    daily_check_service::mark_submitted(&pool, user.id, input.date).await?;
    let date = input.date.unwrap_or_else(|| chrono::Utc::now().date_naive());
    Ok(Json(DailyStatusResponse {
        submitted: true,
        date: date.to_string(),
    }))
}

pub async fn register_device(
    State((pool, config)): State<(PgPool, Config)>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Json(input): Json<RegisterDeviceRequest>,
) -> Result<Json<serde_json::Value>, AppError> {
    let endpoint_arn = notification_service::register_device(
        &pool,
        &config,
        user.id,
        &input.device_token,
    )
    .await?;
    Ok(Json(serde_json::json!({ "endpoint_arn": endpoint_arn })))
}
