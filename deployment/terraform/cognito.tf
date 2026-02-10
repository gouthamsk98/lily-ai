resource "aws_cognito_user_pool" "main" {
  name = "${var.project_name}-users"

  auto_verified_attributes = ["email"]
  username_attributes      = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_symbols   = false
    require_uppercase = true
  }

  schema {
    name                = "email"
    attribute_data_type = "String"
    required            = true
    mutable             = true
  }

  schema {
    name                = "name"
    attribute_data_type = "String"
    required            = true
    mutable             = true
  }

  tags = { Name = "${var.project_name}-user-pool" }
}

resource "aws_cognito_identity_provider" "google" {
  user_pool_id  = aws_cognito_user_pool.main.id
  provider_name = "Google"
  provider_type = "Google"

  provider_details = {
    client_id        = var.google_client_id
    client_secret    = var.google_client_secret
    authorize_scopes = "openid email profile"
  }

  attribute_mapping = {
    email    = "email"
    name     = "name"
    username = "sub"
  }
}

resource "aws_cognito_user_pool_domain" "main" {
  domain       = var.project_name
  user_pool_id = aws_cognito_user_pool.main.id
}

resource "aws_cognito_user_pool_client" "main" {
  name         = "${var.project_name}-app"
  user_pool_id = aws_cognito_user_pool.main.id

  supported_identity_providers = ["Google"]
  allowed_oauth_flows          = ["code"]
  allowed_oauth_scopes         = ["openid", "email", "profile"]
  allowed_oauth_flows_user_pool_client = true

  callback_urls = [
    "http://localhost:3000",
    "https://${aws_cloudfront_distribution.main.domain_name}",
    "lilyai://callback",
  ]

  logout_urls = [
    "http://localhost:3000",
    "https://${aws_cloudfront_distribution.main.domain_name}",
  ]

  generate_secret = false

  depends_on = [aws_cognito_identity_provider.google]
}
