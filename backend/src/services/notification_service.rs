use sqlx::PgPool;
use uuid::Uuid;

use crate::config::Config;
use crate::errors::AppError;
use crate::infrastructure::user_repo;
use crate::services::daily_check_service;

pub async fn register_device(
    pool: &PgPool,
    config: &Config,
    user_id: Uuid,
    device_token: &str,
) -> Result<String, AppError> {
    let arn = match &config.sns_platform_application_arn {
        Some(arn) => arn,
        None => return Err(AppError::Internal("SNS not configured".to_string())),
    };

    let aws_config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
    let sns_client = aws_sdk_sns::Client::new(&aws_config);

    let result = sns_client
        .create_platform_endpoint()
        .platform_application_arn(arn)
        .token(device_token)
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("SNS registration failed: {}", e)))?;

    let endpoint_arn = result.endpoint_arn().unwrap_or_default().to_string();

    user_repo::update_sns_endpoint(pool, user_id, &endpoint_arn).await?;

    Ok(endpoint_arn)
}

pub async fn send_reminder(config: &Config, endpoint_arn: &str) -> Result<(), AppError> {
    if config.sns_platform_application_arn.is_none() {
        return Ok(());
    }

    let aws_config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
    let sns_client = aws_sdk_sns::Client::new(&aws_config);

    let message = serde_json::json!({
        "GCM": serde_json::json!({
            "notification": {
                "title": "Budget Tracker",
                "body": "Don't forget to log your expenses today!"
            }
        }).to_string()
    });

    sns_client
        .publish()
        .target_arn(endpoint_arn)
        .message(message.to_string())
        .message_structure("json")
        .send()
        .await
        .map_err(|e| {
            tracing::error!("Failed to send SNS notification: {}", e);
            AppError::Internal(format!("Failed to send notification: {}", e))
        })?;

    Ok(())
}

pub async fn send_daily_reminders(pool: &PgPool, config: &Config) -> Result<(), AppError> {
    let user_ids = daily_check_service::get_users_without_submission(pool).await?;

    for user_id in user_ids {
        if let Ok(Some(user)) = user_repo::find_by_id(pool, user_id).await {
            if let Some(ref arn) = user.sns_endpoint_arn {
                if let Err(e) = send_reminder(config, arn).await {
                    tracing::error!("Failed to send reminder to user {}: {}", user_id, e);
                }
            }
        }
    }

    Ok(())
}
