use axum::{
    extract::{Path, Query, State},
    Extension, Json,
};
use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::expense::*;
use crate::errors::AppError;
use crate::middleware::auth::AuthenticatedUser;
use crate::services::expense_service;

pub async fn create_expense(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Json(input): Json<CreateExpense>,
) -> Result<(axum::http::StatusCode, Json<Expense>), AppError> {
    let expense = expense_service::create_expense(&pool, user.id, &input).await?;
    Ok((axum::http::StatusCode::CREATED, Json(expense)))
}

pub async fn get_expense(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path(id): Path<Uuid>,
) -> Result<Json<Expense>, AppError> {
    let expense = expense_service::get_expense(&pool, id, user.id).await?;
    Ok(Json(expense))
}

pub async fn list_expenses(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Query(filter): Query<ExpenseFilter>,
) -> Result<Json<Vec<Expense>>, AppError> {
    let expenses = expense_service::list_expenses(&pool, user.id, &filter).await?;
    Ok(Json(expenses))
}

pub async fn update_expense(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path(id): Path<Uuid>,
    Json(input): Json<UpdateExpense>,
) -> Result<Json<Expense>, AppError> {
    let expense = expense_service::update_expense(&pool, id, user.id, &input).await?;
    Ok(Json(expense))
}

pub async fn delete_expense(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path(id): Path<Uuid>,
) -> Result<axum::http::StatusCode, AppError> {
    expense_service::delete_expense(&pool, id, user.id).await?;
    Ok(axum::http::StatusCode::NO_CONTENT)
}
