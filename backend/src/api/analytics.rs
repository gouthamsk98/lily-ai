use axum::{extract::State, Extension, Json};
use chrono::NaiveDate;
use serde::Deserialize;
use sqlx::PgPool;

use crate::domain::expense::ExpenseSummary;
use crate::errors::AppError;
use crate::middleware::auth::AuthenticatedUser;
use crate::services::analytics_service;

#[derive(Debug, Deserialize)]
pub struct DateQuery {
    pub date: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
pub struct DateRangeQuery {
    pub start_date: Option<NaiveDate>,
    pub end_date: Option<NaiveDate>,
}

pub async fn daily(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    axum::extract::Query(query): axum::extract::Query<DateQuery>,
) -> Result<Json<ExpenseSummary>, AppError> {
    let summary = analytics_service::daily_summary(&pool, user.id, query.date).await?;
    Ok(Json(summary))
}

pub async fn weekly(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    axum::extract::Query(query): axum::extract::Query<DateQuery>,
) -> Result<Json<ExpenseSummary>, AppError> {
    let summary = analytics_service::weekly_summary(&pool, user.id, query.date).await?;
    Ok(Json(summary))
}

pub async fn monthly(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    axum::extract::Query(query): axum::extract::Query<DateQuery>,
) -> Result<Json<ExpenseSummary>, AppError> {
    let summary = analytics_service::monthly_summary(&pool, user.id, query.date).await?;
    Ok(Json(summary))
}

pub async fn category(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    axum::extract::Query(query): axum::extract::Query<DateRangeQuery>,
) -> Result<Json<ExpenseSummary>, AppError> {
    let summary = analytics_service::category_summary(
        &pool,
        user.id,
        query.start_date,
        query.end_date,
    )
    .await?;
    Ok(Json(summary))
}
