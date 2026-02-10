use chrono::{DateTime, NaiveTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct User {
    pub id: Uuid,
    pub cognito_sub: String,
    pub email: String,
    pub name: String,
    pub notification_time: NaiveTime,
    pub sns_endpoint_arn: Option<String>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateUserProfile {
    pub name: Option<String>,
    pub notification_time: Option<NaiveTime>,
}
