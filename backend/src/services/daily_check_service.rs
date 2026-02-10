use chrono::NaiveDate;
use sqlx::PgPool;
use uuid::Uuid;

use crate::errors::AppError;
use crate::infrastructure::expense_repo;

pub async fn check_submitted_today(
    pool: &PgPool,
    user_id: Uuid,
) -> Result<bool, AppError> {
    let today = chrono::Utc::now().date_naive();
    expense_repo::check_daily_submission(pool, user_id, today).await
}

pub async fn mark_submitted(
    pool: &PgPool,
    user_id: Uuid,
    date: Option<NaiveDate>,
) -> Result<(), AppError> {
    let date = date.unwrap_or_else(|| chrono::Utc::now().date_naive());
    expense_repo::mark_daily_submission(pool, user_id, date).await?;
    Ok(())
}

pub async fn get_users_without_submission(
    pool: &PgPool,
) -> Result<Vec<Uuid>, AppError> {
    let today = chrono::Utc::now().date_naive();
    expense_repo::users_without_submission_today(pool, today).await
}
