use chrono::NaiveDate;
use rust_decimal::Decimal;
use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::expense::*;
use crate::errors::AppError;

pub async fn create(
    pool: &PgPool,
    user_id: Uuid,
    expense: &CreateExpense,
) -> Result<Expense, AppError> {
    let expense_date = expense.expense_date.unwrap_or_else(|| chrono::Utc::now().date_naive());
    let row = sqlx::query_as::<_, Expense>(
        r#"INSERT INTO expenses (user_id, amount, category, note, expense_date)
           VALUES ($1, $2, $3, $4, $5)
           RETURNING id, user_id, amount, category, note, expense_date, created_at, updated_at"#,
    )
    .bind(user_id)
    .bind(expense.amount)
    .bind(expense.category.to_string())
    .bind(&expense.note)
    .bind(expense_date)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn find_by_id(pool: &PgPool, id: Uuid, user_id: Uuid) -> Result<Option<Expense>, AppError> {
    let row = sqlx::query_as::<_, Expense>(
        "SELECT id, user_id, amount, category, note, expense_date, created_at, updated_at FROM expenses WHERE id = $1 AND user_id = $2"
    )
    .bind(id)
    .bind(user_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn find_all(
    pool: &PgPool,
    user_id: Uuid,
    filter: &ExpenseFilter,
) -> Result<Vec<Expense>, AppError> {
    let page = filter.page.unwrap_or(1).max(1);
    let per_page = filter.per_page.unwrap_or(20).clamp(1, 100);
    let offset = (page - 1) * per_page;

    let rows = sqlx::query_as::<_, Expense>(
        r#"SELECT id, user_id, amount, category, note, expense_date, created_at, updated_at
           FROM expenses
           WHERE user_id = $1
             AND ($2::date IS NULL OR expense_date >= $2)
             AND ($3::date IS NULL OR expense_date <= $3)
             AND ($4::text IS NULL OR category = $4)
           ORDER BY expense_date DESC, created_at DESC
           LIMIT $5 OFFSET $6"#,
    )
    .bind(user_id)
    .bind(filter.start_date)
    .bind(filter.end_date)
    .bind(filter.category.map(|c| c.to_string()))
    .bind(per_page)
    .bind(offset)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn update(
    pool: &PgPool,
    id: Uuid,
    user_id: Uuid,
    expense: &UpdateExpense,
) -> Result<Option<Expense>, AppError> {
    let row = sqlx::query_as::<_, Expense>(
        r#"UPDATE expenses
           SET amount = COALESCE($3, amount),
               category = COALESCE($4, category),
               note = COALESCE($5, note),
               expense_date = COALESCE($6, expense_date),
               updated_at = NOW()
           WHERE id = $1 AND user_id = $2
           RETURNING id, user_id, amount, category, note, expense_date, created_at, updated_at"#,
    )
    .bind(id)
    .bind(user_id)
    .bind(expense.amount)
    .bind(expense.category.map(|c| c.to_string()))
    .bind(&expense.note)
    .bind(expense.expense_date)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn delete(pool: &PgPool, id: Uuid, user_id: Uuid) -> Result<bool, AppError> {
    let result = sqlx::query("DELETE FROM expenses WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(user_id)
        .execute(pool)
        .await?;
    Ok(result.rows_affected() > 0)
}

// Analytics queries

pub async fn daily_summary(
    pool: &PgPool,
    user_id: Uuid,
    date: NaiveDate,
) -> Result<ExpenseSummary, AppError> {
    let by_category = sqlx::query_as::<_, CategorySummary>(
        r#"SELECT category, COALESCE(SUM(amount), 0) as total, COUNT(*) as count
           FROM expenses WHERE user_id = $1 AND expense_date = $2
           GROUP BY category ORDER BY total DESC"#,
    )
    .bind(user_id)
    .bind(date)
    .fetch_all(pool)
    .await?;

    let total: Decimal = by_category.iter().map(|c| c.total).sum();
    let count: i64 = by_category.iter().map(|c| c.count).sum();

    Ok(ExpenseSummary { total, count, by_category })
}

pub async fn range_summary(
    pool: &PgPool,
    user_id: Uuid,
    start_date: NaiveDate,
    end_date: NaiveDate,
) -> Result<ExpenseSummary, AppError> {
    let by_category = sqlx::query_as::<_, CategorySummary>(
        r#"SELECT category, COALESCE(SUM(amount), 0) as total, COUNT(*) as count
           FROM expenses WHERE user_id = $1 AND expense_date >= $2 AND expense_date <= $3
           GROUP BY category ORDER BY total DESC"#,
    )
    .bind(user_id)
    .bind(start_date)
    .bind(end_date)
    .fetch_all(pool)
    .await?;

    let total: Decimal = by_category.iter().map(|c| c.total).sum();
    let count: i64 = by_category.iter().map(|c| c.count).sum();

    Ok(ExpenseSummary { total, count, by_category })
}

// Daily submission tracking

pub async fn check_daily_submission(
    pool: &PgPool,
    user_id: Uuid,
    date: NaiveDate,
) -> Result<bool, AppError> {
    let row = sqlx::query_scalar::<_, bool>(
        "SELECT EXISTS(SELECT 1 FROM daily_submissions WHERE user_id = $1 AND submission_date = $2)"
    )
    .bind(user_id)
    .bind(date)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn mark_daily_submission(
    pool: &PgPool,
    user_id: Uuid,
    date: NaiveDate,
) -> Result<DailySubmission, AppError> {
    let row = sqlx::query_as::<_, DailySubmission>(
        r#"INSERT INTO daily_submissions (user_id, submission_date)
           VALUES ($1, $2)
           ON CONFLICT (user_id, submission_date) DO UPDATE SET submitted_at = NOW()
           RETURNING id, user_id, submission_date, submitted_at"#,
    )
    .bind(user_id)
    .bind(date)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn users_without_submission_today(
    pool: &PgPool,
    date: NaiveDate,
) -> Result<Vec<Uuid>, AppError> {
    let rows = sqlx::query_scalar::<_, Uuid>(
        r#"SELECT u.id FROM users u
           WHERE u.sns_endpoint_arn IS NOT NULL
           AND NOT EXISTS (
               SELECT 1 FROM daily_submissions ds
               WHERE ds.user_id = u.id AND ds.submission_date = $1
           )"#,
    )
    .bind(date)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}
