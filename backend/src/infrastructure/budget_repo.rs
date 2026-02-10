use rust_decimal::Decimal;
use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::budget::BudgetSetting;
use crate::errors::AppError;

pub async fn get(pool: &PgPool, user_id: Uuid) -> Result<Option<BudgetSetting>, AppError> {
    let row = sqlx::query_as::<_, BudgetSetting>(
        r#"SELECT id, user_id, daily_budget, created_at, updated_at
           FROM user_budget_settings WHERE user_id = $1"#,
    )
    .bind(user_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn upsert(
    pool: &PgPool,
    user_id: Uuid,
    daily_budget: Decimal,
) -> Result<BudgetSetting, AppError> {
    let row = sqlx::query_as::<_, BudgetSetting>(
        r#"INSERT INTO user_budget_settings (user_id, daily_budget)
           VALUES ($1, $2)
           ON CONFLICT (user_id)
           DO UPDATE SET daily_budget = $2, updated_at = NOW()
           RETURNING id, user_id, daily_budget, created_at, updated_at"#,
    )
    .bind(user_id)
    .bind(daily_budget)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

/// Get total spent on a specific date
pub async fn spent_on_date(
    pool: &PgPool,
    user_id: Uuid,
    date: &str,
) -> Result<Decimal, AppError> {
    let row: (Decimal,) = sqlx::query_as(
        "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE user_id = $1 AND expense_date = $2",
    )
    .bind(user_id)
    .bind(date)
    .fetch_one(pool)
    .await?;
    Ok(row.0)
}

/// Get daily spending for a date range (for carryover calculation)
pub async fn daily_spending(
    pool: &PgPool,
    user_id: Uuid,
    start_date: &str,
    end_date: &str,
) -> Result<Vec<(String, Decimal)>, AppError> {
    let rows: Vec<(String, Decimal)> = sqlx::query_as(
        r#"SELECT expense_date::text, COALESCE(SUM(amount), 0)
           FROM expenses
           WHERE user_id = $1 AND expense_date >= $2 AND expense_date <= $3
           GROUP BY expense_date
           ORDER BY expense_date"#,
    )
    .bind(user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}
