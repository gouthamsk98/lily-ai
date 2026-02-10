use serde::Deserialize;

#[derive(Clone, Debug, Deserialize)]
pub struct Config {
    pub database_url: String,
    pub host: String,
    pub port: u16,
    pub cognito_user_pool_id: String,
    pub cognito_region: String,
    pub cognito_app_client_id: String,
    pub sns_platform_application_arn: Option<String>,
    pub meeting_audio_s3_bucket: Option<String>,
}

impl Config {
    pub fn from_env() -> Result<Self, dotenvy::Error> {
        dotenvy::dotenv().ok();
        Ok(Config {
            database_url: std::env::var("DATABASE_URL")
                .expect("DATABASE_URL must be set"),
            host: std::env::var("HOST").unwrap_or_else(|_| "0.0.0.0".to_string()),
            port: std::env::var("PORT")
                .unwrap_or_else(|_| "8080".to_string())
                .parse()
                .expect("PORT must be a valid number"),
            cognito_user_pool_id: std::env::var("COGNITO_USER_POOL_ID")
                .expect("COGNITO_USER_POOL_ID must be set"),
            cognito_region: std::env::var("COGNITO_REGION")
                .expect("COGNITO_REGION must be set"),
            cognito_app_client_id: std::env::var("COGNITO_APP_CLIENT_ID")
                .expect("COGNITO_APP_CLIENT_ID must be set"),
            sns_platform_application_arn: std::env::var("SNS_PLATFORM_APPLICATION_ARN").ok(),
            meeting_audio_s3_bucket: std::env::var("MEETING_AUDIO_S3_BUCKET").ok(),
        })
    }

    pub fn cognito_jwks_url(&self) -> String {
        format!(
            "https://cognito-idp.{}.amazonaws.com/{}/.well-known/jwks.json",
            self.cognito_region, self.cognito_user_pool_id
        )
    }

    pub fn cognito_issuer(&self) -> String {
        format!(
            "https://cognito-idp.{}.amazonaws.com/{}",
            self.cognito_region, self.cognito_user_pool_id
        )
    }
}
