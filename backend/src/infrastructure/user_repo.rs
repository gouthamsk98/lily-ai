use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::user::{User, UpdateUserProfile};
use crate::errors::AppError;

pub async fn find_by_cognito_sub(pool: &PgPool, cognito_sub: &str) -> Result<Option<User>, AppError> {
    let user = sqlx::query_as::<_, User>(
        "SELECT id, cognito_sub, email, name, notification_time, sns_endpoint_arn, created_at FROM users WHERE cognito_sub = $1"
    )
    .bind(cognito_sub)
    .fetch_optional(pool)
    .await?;
    Ok(user)
}

pub async fn create(
    pool: &PgPool,
    cognito_sub: &str,
    email: &str,
    name: &str,
) -> Result<User, AppError> {
    let user = sqlx::query_as::<_, User>(
        r#"INSERT INTO users (cognito_sub, email, name)
           VALUES ($1, $2, $3)
           RETURNING id, cognito_sub, email, name, notification_time, sns_endpoint_arn, created_at"#,
    )
    .bind(cognito_sub)
    .bind(email)
    .bind(name)
    .fetch_one(pool)
    .await?;
    Ok(user)
}

pub async fn find_by_id(pool: &PgPool, id: Uuid) -> Result<Option<User>, AppError> {
    let user = sqlx::query_as::<_, User>(
        "SELECT id, cognito_sub, email, name, notification_time, sns_endpoint_arn, created_at FROM users WHERE id = $1"
    )
    .bind(id)
    .fetch_optional(pool)
    .await?;
    Ok(user)
}

pub async fn update_profile(
    pool: &PgPool,
    user_id: Uuid,
    profile: &UpdateUserProfile,
) -> Result<User, AppError> {
    let user = sqlx::query_as::<_, User>(
        r#"UPDATE users
           SET name = COALESCE($2, name),
               notification_time = COALESCE($3, notification_time)
           WHERE id = $1
           RETURNING id, cognito_sub, email, name, notification_time, sns_endpoint_arn, created_at"#,
    )
    .bind(user_id)
    .bind(&profile.name)
    .bind(profile.notification_time)
    .fetch_one(pool)
    .await?;
    Ok(user)
}

pub async fn update_sns_endpoint(
    pool: &PgPool,
    user_id: Uuid,
    endpoint_arn: &str,
) -> Result<(), AppError> {
    sqlx::query("UPDATE users SET sns_endpoint_arn = $2 WHERE id = $1")
        .bind(user_id)
        .bind(endpoint_arn)
        .execute(pool)
        .await?;
    Ok(())
}

pub async fn find_all_with_sns(pool: &PgPool) -> Result<Vec<User>, AppError> {
    let users = sqlx::query_as::<_, User>(
        "SELECT id, cognito_sub, email, name, notification_time, sns_endpoint_arn, created_at FROM users WHERE sns_endpoint_arn IS NOT NULL"
    )
    .fetch_all(pool)
    .await?;
    Ok(users)
}
