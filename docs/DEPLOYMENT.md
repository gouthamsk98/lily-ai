# Deployment Guide

## Prerequisites

- AWS CLI configured (`aws configure`)
- Docker installed
- Terraform 1.0+ installed
- A registered domain (optional, for HTTPS)
- Google OAuth credentials from [Google Cloud Console](https://console.cloud.google.com/apis/credentials)

## Step 1: Google OAuth Setup

1. Go to Google Cloud Console → APIs & Services → Credentials
2. Create an OAuth 2.0 Client ID (Web application)
3. Add authorized redirect URIs:
   - `https://<your-cognito-domain>.auth.<region>.amazoncognito.com/oauth2/idpresponse`
4. Note the Client ID and Client Secret

## Step 2: Terraform Infrastructure

```bash
cd deployment/terraform

# Create variables file
cat > terraform.tfvars <<EOF
aws_region           = "us-east-1"
db_password          = "your-secure-password"
google_client_id     = "your-google-client-id"
google_client_secret = "your-google-client-secret"
backend_image        = "placeholder"  # Updated after first ECR push
web_image            = "placeholder"  # Updated after first ECR push
EOF

# Initialize and apply
terraform init
terraform plan
terraform apply
```

Note the outputs:
- `cognito_user_pool_id`
- `cognito_app_client_id`
- `cognito_domain`
- `ecr_backend_repo`
- `ecr_web_repo`
- `alb_dns_name`

## Step 3: Build and Push Docker Images

```bash
# Login to ECR
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION=us-east-1
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com

# Build and push backend
cd backend
docker build -t budget-tracker-backend .
docker tag budget-tracker-backend:latest $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/budget-tracker-backend:latest
docker push $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/budget-tracker-backend:latest

# Build web with production environment
cd ../web
cat > .env <<EOF
VITE_API_URL=http://<alb-dns-name>/api
VITE_COGNITO_USER_POOL_ID=<from-terraform-output>
VITE_COGNITO_APP_CLIENT_ID=<from-terraform-output>
VITE_COGNITO_REGION=us-east-1
VITE_COGNITO_DOMAIN=<from-terraform-output>
VITE_REDIRECT_SIGN_IN=http://<alb-dns-name>
VITE_REDIRECT_SIGN_OUT=http://<alb-dns-name>
EOF
docker build -t budget-tracker-web .
docker tag budget-tracker-web:latest $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/budget-tracker-web:latest
docker push $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/budget-tracker-web:latest
```

## Step 4: Update Terraform with Real Images

```bash
cd deployment/terraform

# Update terraform.tfvars with real ECR image URIs
# backend_image = "<account>.dkr.ecr.<region>.amazonaws.com/budget-tracker-backend:latest"
# web_image = "<account>.dkr.ecr.<region>.amazonaws.com/budget-tracker-web:latest"

terraform apply
```

## Step 5: Verify

```bash
# Check ECS services
aws ecs describe-services --cluster budget-tracker-cluster \
  --services budget-tracker-backend budget-tracker-web \
  --query 'services[].{name:serviceName,status:status,running:runningCount}' \
  --no-cli-pager

# Test health endpoint
curl http://<alb-dns-name>/health

# Test API (with a valid Cognito token)
curl -H "Authorization: Bearer <token>" http://<alb-dns-name>/api/auth/me
```

## Step 6: Android Configuration

1. Copy Cognito outputs into `amplifyconfiguration.json`
2. Update `API_BASE_URL` in `app/build.gradle.kts` to point to ALB DNS
3. Build and distribute APK

## Subsequent Deployments

Use the deploy script:

```bash
cd deployment/scripts
./deploy.sh
```

## HTTPS Setup (Optional)

1. Register a domain and create a Route 53 hosted zone
2. Request an ACM certificate:
   ```bash
   aws acm request-certificate --domain-name yourdomain.com --validation-method DNS
   ```
3. Add HTTPS listener to ALB in `alb.tf`
4. Update Cognito callback URLs with HTTPS domain
5. `terraform apply`

## Monitoring

- **Logs**: CloudWatch Log Groups `/ecs/budget-tracker-backend` and `/ecs/budget-tracker-web`
- **Metrics**: ECS service metrics in CloudWatch
- **Database**: RDS Performance Insights (enable in console)

## Cost Estimate (Small Scale)

| Resource | Estimated Monthly Cost |
|----------|----------------------|
| ECS Fargate (2 tasks) | ~$15 |
| RDS PostgreSQL (db.t3.micro) | ~$15 |
| ALB | ~$16 |
| NAT Gateway | ~$32 |
| ECR, CloudWatch, SNS | ~$2 |
| **Total** | **~$80/month** |

> To reduce costs, consider removing the NAT Gateway and using public subnets for ECS tasks during development.
