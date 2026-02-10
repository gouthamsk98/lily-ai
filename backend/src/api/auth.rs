use axum::{Extension, Json};

use crate::errors::AppError;
use crate::middleware::auth::AuthenticatedUser;

pub async fn me(
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
) -> Result<Json<serde_json::Value>, AppError> {
    Ok(Json(serde_json::json!({
        "id": user.id,
        "email": user.email,
        "name": user.name,
        "notification_time": user.notification_time.to_string(),
        "created_at": user.created_at,
    })))
}
