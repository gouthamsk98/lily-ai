use jsonwebtoken::{decode, decode_header, Algorithm, DecodingKey, Validation};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::RwLock;

use crate::config::Config;
use crate::errors::AppError;

#[derive(Debug, Serialize, Deserialize)]
pub struct CognitoClaims {
    pub sub: String,
    pub email: Option<String>,
    pub name: Option<String>,
    #[serde(rename = "cognito:username")]
    pub cognito_username: Option<String>,
    pub token_use: Option<String>,
    pub aud: Option<String>,
    pub iss: Option<String>,
    pub exp: Option<u64>,
}

#[derive(Debug, Deserialize)]
struct JwkSet {
    keys: Vec<Jwk>,
}

#[derive(Debug, Deserialize)]
struct Jwk {
    kid: String,
    n: String,
    e: String,
    kty: String,
}

#[derive(Clone)]
pub struct AuthService {
    config: Config,
    http_client: Client,
    jwks_cache: Arc<RwLock<HashMap<String, DecodingKey>>>,
}

impl AuthService {
    pub fn new(config: Config) -> Self {
        Self {
            config,
            http_client: Client::new(),
            jwks_cache: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub async fn validate_token(&self, token: &str) -> Result<CognitoClaims, AppError> {
        let header = decode_header(token).map_err(|_| AppError::Unauthorized)?;
        let kid = header.kid.ok_or(AppError::Unauthorized)?;

        let key = self.get_decoding_key(&kid).await?;

        let mut validation = Validation::new(Algorithm::RS256);
        validation.set_issuer(&[&self.config.cognito_issuer()]);
        validation.set_audience(&[&self.config.cognito_app_client_id]);

        let token_data = decode::<CognitoClaims>(token, &key, &validation)
            .map_err(|e| {
                tracing::debug!("JWT validation failed: {:?}", e);
                AppError::Unauthorized
            })?;

        Ok(token_data.claims)
    }

    async fn get_decoding_key(&self, kid: &str) -> Result<DecodingKey, AppError> {
        // Check cache first
        {
            let cache = self.jwks_cache.read().await;
            if let Some(key) = cache.get(kid) {
                return Ok(key.clone());
            }
        }

        // Fetch JWKS from Cognito
        let jwks_url = self.config.cognito_jwks_url();
        let jwk_set: JwkSet = self.http_client
            .get(&jwks_url)
            .send()
            .await
            .map_err(|e| AppError::Internal(format!("Failed to fetch JWKS: {}", e)))?
            .json()
            .await
            .map_err(|e| AppError::Internal(format!("Failed to parse JWKS: {}", e)))?;

        let mut cache = self.jwks_cache.write().await;
        for jwk in &jwk_set.keys {
            if jwk.kty == "RSA" {
                let key = DecodingKey::from_rsa_components(&jwk.n, &jwk.e)
                    .map_err(|e| AppError::Internal(format!("Invalid JWK: {}", e)))?;
                cache.insert(jwk.kid.clone(), key.clone());
            }
        }

        cache.get(kid).cloned().ok_or(AppError::Unauthorized)
    }
}
