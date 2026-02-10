use chrono::{DateTime, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct BudgetSetting {
    pub id: Uuid,
    pub user_id: Uuid,
    pub daily_budget: Decimal,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize)]
pub struct SetBudgetRequest {
    pub daily_budget: Decimal,
}

#[derive(Debug, Serialize)]
pub struct EffectiveBudget {
    pub daily_budget: Decimal,
    pub effective_budget_today: Decimal,
    pub carried_over: Decimal,
    pub spent_today: Decimal,
    pub remaining_today: Decimal,
}
