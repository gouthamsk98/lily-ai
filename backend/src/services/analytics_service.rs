use chrono::{Datelike, NaiveDate, Utc};
use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::expense::ExpenseSummary;
use crate::errors::AppError;
use crate::infrastructure::expense_repo;

pub async fn daily_summary(
    pool: &PgPool,
    user_id: Uuid,
    date: Option<NaiveDate>,
) -> Result<ExpenseSummary, AppError> {
    let date = date.unwrap_or_else(|| Utc::now().date_naive());
    expense_repo::daily_summary(pool, user_id, date).await
}

pub async fn weekly_summary(
    pool: &PgPool,
    user_id: Uuid,
    date: Option<NaiveDate>,
) -> Result<ExpenseSummary, AppError> {
    let date = date.unwrap_or_else(|| Utc::now().date_naive());
    let weekday = date.weekday().num_days_from_monday();
    let start = date - chrono::Duration::days(weekday as i64);
    let end = start + chrono::Duration::days(6);
    expense_repo::range_summary(pool, user_id, start, end).await
}

pub async fn monthly_summary(
    pool: &PgPool,
    user_id: Uuid,
    date: Option<NaiveDate>,
) -> Result<ExpenseSummary, AppError> {
    let date = date.unwrap_or_else(|| Utc::now().date_naive());
    let start = NaiveDate::from_ymd_opt(date.year(), date.month(), 1).unwrap();
    let end = if date.month() == 12 {
        NaiveDate::from_ymd_opt(date.year() + 1, 1, 1).unwrap()
    } else {
        NaiveDate::from_ymd_opt(date.year(), date.month() + 1, 1).unwrap()
    } - chrono::Duration::days(1);
    expense_repo::range_summary(pool, user_id, start, end).await
}

pub async fn category_summary(
    pool: &PgPool,
    user_id: Uuid,
    start_date: Option<NaiveDate>,
    end_date: Option<NaiveDate>,
) -> Result<ExpenseSummary, AppError> {
    let end = end_date.unwrap_or_else(|| Utc::now().date_naive());
    let start = start_date.unwrap_or_else(|| {
        NaiveDate::from_ymd_opt(end.year(), end.month(), 1).unwrap()
    });
    expense_repo::range_summary(pool, user_id, start, end).await
}
