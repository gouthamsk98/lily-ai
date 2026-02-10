use axum::{
    extract::Request,
    middleware::Next,
    response::Response,
};
use sqlx::PgPool;

use crate::domain::user::User;
use crate::errors::AppError;
use crate::infrastructure::user_repo;
use crate::services::auth_service::AuthService;

#[derive(Clone)]
pub struct AuthenticatedUser(pub User);

pub async fn auth_middleware(
    axum::extract::State((auth_service, pool)): axum::extract::State<(AuthService, PgPool)>,
    mut req: Request,
    next: Next,
) -> Result<Response, AppError> {
    let auth_header = req
        .headers()
        .get("Authorization")
        .and_then(|v| v.to_str().ok())
        .ok_or(AppError::Unauthorized)?;

    let token = auth_header
        .strip_prefix("Bearer ")
        .ok_or(AppError::Unauthorized)?;

    let claims = auth_service.validate_token(token).await?;

    // Auto-provision user on first request
    let user = match user_repo::find_by_cognito_sub(&pool, &claims.sub).await? {
        Some(user) => user,
        None => {
            let email = claims.email.unwrap_or_else(|| format!("{}@unknown", claims.sub));
            let name = claims.name
                .or(claims.cognito_username)
                .unwrap_or_else(|| "User".to_string());
            user_repo::create(&pool, &claims.sub, &email, &name).await?
        }
    };

    req.extensions_mut().insert(AuthenticatedUser(user));

    Ok(next.run(req).await)
}
