variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "budget-tracker"
}

variable "environment" {
  description = "Environment (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  default     = "budget_user"
}

variable "db_password" {
  description = "PostgreSQL master password"
  type        = string
  sensitive   = true
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "budget_tracker"
}

variable "domain_name" {
  description = "Domain name for the application (optional)"
  type        = string
  default     = ""
}

variable "google_client_id" {
  description = "Google OAuth Client ID for Cognito"
  type        = string
  default     = "placeholder"
}

variable "google_client_secret" {
  description = "Google OAuth Client Secret for Cognito"
  type        = string
  sensitive   = true
  default     = "placeholder"
}

variable "backend_image" {
  description = "ECR image URI for backend"
  type        = string
  default     = "public.ecr.aws/docker/library/nginx:alpine"
}

variable "web_image" {
  description = "ECR image URI for web frontend"
  type        = string
  default     = "public.ecr.aws/docker/library/nginx:alpine"
}
