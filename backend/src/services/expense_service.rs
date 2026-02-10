use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::expense::*;
use crate::errors::AppError;
use crate::infrastructure::expense_repo;

pub async fn create_expense(
    pool: &PgPool,
    user_id: Uuid,
    input: &CreateExpense,
) -> Result<Expense, AppError> {
    let expense = expense_repo::create(pool, user_id, input).await?;

    // Auto-mark daily submission when expense is created
    let date = input.expense_date.unwrap_or_else(|| chrono::Utc::now().date_naive());
    let _ = expense_repo::mark_daily_submission(pool, user_id, date).await;

    Ok(expense)
}

pub async fn get_expense(
    pool: &PgPool,
    id: Uuid,
    user_id: Uuid,
) -> Result<Expense, AppError> {
    expense_repo::find_by_id(pool, id, user_id)
        .await?
        .ok_or_else(|| AppError::NotFound("Expense not found".to_string()))
}

pub async fn list_expenses(
    pool: &PgPool,
    user_id: Uuid,
    filter: &ExpenseFilter,
) -> Result<Vec<Expense>, AppError> {
    expense_repo::find_all(pool, user_id, filter).await
}

pub async fn update_expense(
    pool: &PgPool,
    id: Uuid,
    user_id: Uuid,
    input: &UpdateExpense,
) -> Result<Expense, AppError> {
    expense_repo::update(pool, id, user_id, input)
        .await?
        .ok_or_else(|| AppError::NotFound("Expense not found".to_string()))
}

pub async fn delete_expense(
    pool: &PgPool,
    id: Uuid,
    user_id: Uuid,
) -> Result<(), AppError> {
    if !expense_repo::delete(pool, id, user_id).await? {
        return Err(AppError::NotFound("Expense not found".to_string()));
    }
    Ok(())
}
