use axum::{extract::State, Extension, Json};
use sqlx::PgPool;

use crate::domain::user::UpdateUserProfile;
use crate::errors::AppError;
use crate::infrastructure::user_repo;
use crate::middleware::auth::AuthenticatedUser;

pub async fn update_profile(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Json(input): Json<UpdateUserProfile>,
) -> Result<Json<serde_json::Value>, AppError> {
    let updated = user_repo::update_profile(&pool, user.id, &input).await?;
    Ok(Json(serde_json::json!({
        "id": updated.id,
        "email": updated.email,
        "name": updated.name,
        "notification_time": updated.notification_time.to_string(),
        "created_at": updated.created_at,
    })))
}
