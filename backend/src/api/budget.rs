use axum::{extract::{Query, State}, Extension, Json};
use chrono::{Duration, NaiveDate};
use rust_decimal::Decimal;
use serde::Deserialize;
use sqlx::PgPool;

use crate::domain::budget::*;
use crate::errors::AppError;
use crate::infrastructure::budget_repo;
use crate::middleware::auth::AuthenticatedUser;

#[derive(Debug, Deserialize)]
pub struct BudgetQuery {
    pub date: Option<NaiveDate>,
}

pub async fn get_budget(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Query(query): Query<BudgetQuery>,
) -> Result<Json<EffectiveBudget>, AppError> {
    let setting = budget_repo::get(&pool, user.id).await?;
    let daily_budget = setting.map(|s| s.daily_budget).unwrap_or(Decimal::ZERO);

    if daily_budget <= Decimal::ZERO {
        return Ok(Json(EffectiveBudget {
            daily_budget: Decimal::ZERO,
            effective_budget_today: Decimal::ZERO,
            carried_over: Decimal::ZERO,
            spent_today: Decimal::ZERO,
            remaining_today: Decimal::ZERO,
        }));
    }

    let today = query.date.unwrap_or_else(|| chrono::Utc::now().date_naive());
    let today_str = today.format("%Y-%m-%d").to_string();

    let start = today - Duration::days(30);
    let start_str = start.format("%Y-%m-%d").to_string();
    let yesterday_str = (today - Duration::days(1)).format("%Y-%m-%d").to_string();

    let daily_spending = budget_repo::daily_spending(&pool, user.id, &start_str, &yesterday_str).await?;

    let mut carryover = Decimal::ZERO;
    let mut date = start;
    while date < today {
        let date_str = date.format("%Y-%m-%d").to_string();
        let spent = daily_spending
            .iter()
            .find(|(d, _)| d == &date_str)
            .map(|(_, s)| *s)
            .unwrap_or(Decimal::ZERO);
        let diff = daily_budget - spent;
        carryover += diff;
        date += Duration::days(1);
    }

    let spent_today = budget_repo::spent_on_date(&pool, user.id, &today_str).await?;
    let effective = daily_budget + carryover;
    let remaining = effective - spent_today;

    Ok(Json(EffectiveBudget {
        daily_budget,
        effective_budget_today: effective,
        carried_over: carryover,
        spent_today,
        remaining_today: remaining,
    }))
}

pub async fn set_budget(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Json(body): Json<SetBudgetRequest>,
) -> Result<Json<BudgetSetting>, AppError> {
    if body.daily_budget < Decimal::ZERO {
        return Err(AppError::Validation("Budget cannot be negative".into()));
    }
    let setting = budget_repo::upsert(&pool, user.id, body.daily_budget).await?;
    Ok(Json(setting))
}
