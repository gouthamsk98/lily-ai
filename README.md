# Budget Tracker

A full-stack personal daily expense tracking system with Android, Web, and Backend components. Built with clean architecture principles and designed to evolve into a personal assistant system.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Backend | Rust + Axum + SQLx |
| Database | PostgreSQL (AWS RDS) |
| Web Frontend | React + Vite + TypeScript |
| Android | Kotlin + Jetpack Compose |
| Auth | AWS Cognito (Google social login) |
| Push Notifications | AWS SNS |
| Deployment | AWS ECS Fargate + Docker + Terraform |

## Project Structure

```
budget_tracker/
├── backend/         # Rust Axum REST API
├── web/             # React Vite SPA
├── android/         # Kotlin Jetpack Compose app
├── deployment/      # Docker, Terraform, deploy scripts
└── docs/            # Documentation
```

## Quick Start

### Prerequisites

- Rust 1.75+ (`curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`)
- Node.js 18+ and npm
- Docker & Docker Compose
- AWS CLI configured
- Android Studio (for Android development)

### 1. Clone and Setup

```bash
git clone <repo-url>
cd budget_tracker
```

### 2. Start Database (Local Development)

```bash
cd deployment
docker compose up db -d
```

### 3. Run Backend

```bash
cd backend
cp .env.example .env
# Edit .env with your Cognito settings
cargo run
```

The backend will start on `http://localhost:8080`.

### 4. Run Web Frontend

```bash
cd web
cp .env.example .env
# Edit .env with your Cognito and API settings
npm install
npm run dev
```

The web app will start on `http://localhost:5173`.

### 5. Run Android App

1. Open the `android/` directory in Android Studio
2. Configure `amplifyconfiguration.json` with your Cognito settings
3. Build and run on emulator or device

## AWS Setup

### 1. Configure Cognito

1. Create a Google OAuth app at [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Set up Cognito via Terraform:

```bash
cd deployment/terraform
cp terraform.tfvars.example terraform.tfvars
# Edit with your values
terraform init
terraform plan
terraform apply
```

### 2. Deploy to AWS

```bash
cd deployment/scripts
./deploy.sh
```

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for detailed instructions.

## Environment Configuration

### Backend (.env)
```
DATABASE_URL=postgres://user:pass@localhost:5432/budget_tracker
COGNITO_USER_POOL_ID=us-east-1_XXXXX
COGNITO_REGION=us-east-1
COGNITO_APP_CLIENT_ID=your_client_id
```

### Web (.env)
```
VITE_API_URL=http://localhost:8080/api
VITE_COGNITO_USER_POOL_ID=us-east-1_XXXXX
VITE_COGNITO_APP_CLIENT_ID=your_client_id
VITE_COGNITO_REGION=us-east-1
VITE_COGNITO_DOMAIN=your-app.auth.us-east-1.amazoncognito.com
```

## Core Features

- **Daily Expense Tracking**: Log expenses with amount, category, date, and notes
- **Daily Entry Enforcement**: Reminders and UI prompts ensure daily logging
- **Analytics**: Daily, weekly, monthly summaries with category breakdowns
- **Cross-Platform Sync**: Data syncs between Android and Web via REST API
- **Offline Support**: Android app works offline with automatic sync
- **Push Notifications**: Daily reminders via AWS SNS

## Architecture

All components follow clean architecture with separated layers:
- **Domain**: Models and repository interfaces
- **Infrastructure**: Database access, API clients
- **Services**: Business logic and rules
- **Presentation**: UI components and API handlers

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for details.

## API Documentation

See [docs/API.md](docs/API.md) for the full API reference.

## License

MIT
