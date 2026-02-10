#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_BACKEND="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/lily-ai-backend"
ECR_WEB="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/lily-ai-web"

echo "=== Lily AI Deployment ==="
echo "Region: ${AWS_REGION}"
echo "Account: ${AWS_ACCOUNT_ID}"

# Login to ECR
echo "Logging in to ECR..."
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# Build and push backend
echo "Building backend..."
docker build -t lily-ai-backend "$PROJECT_ROOT/backend"
docker tag lily-ai-backend:latest "$ECR_BACKEND:latest"
docker push "$ECR_BACKEND:latest"

# Build and push web
echo "Building web..."
docker build -t lily-ai-web "$PROJECT_ROOT/web"
docker tag lily-ai-web:latest "$ECR_WEB:latest"
docker push "$ECR_WEB:latest"

# Update ECS services
echo "Updating ECS services..."
aws ecs update-service \
  --cluster lily-ai-cluster \
  --service lily-ai-backend \
  --force-new-deployment \
  --region "$AWS_REGION" \
  --no-cli-pager

aws ecs update-service \
  --cluster lily-ai-cluster \
  --service lily-ai-web \
  --force-new-deployment \
  --region "$AWS_REGION" \
  --no-cli-pager

echo "=== Deployment complete ==="
echo "Services are being updated. Check AWS Console for status."
