# Lily AI — Life Intelligence Logger Yourself

**Lily AI** is a personal, on-device AI application connected to your own private cloud infrastructure. It tracks your daily activities, finances, and meetings to help you understand your habits and boost productivity — all while keeping your data under your control.

> *Your life, intelligently logged. Your data, privately yours.*

## What is Lily AI?

Lily stands for **L**ife **I**ntelligence **L**ogger **Y**ourself — a personal productivity system that combines an Android app, a web dashboard, and a self-hosted cloud backend into one seamless ecosystem.

Unlike commercial productivity apps that store your data on third-party servers, Lily AI runs on **your own AWS infrastructure**. Every expense, meeting recording, transcript, and budget insight stays in your personal cloud — giving you full ownership and privacy.

### What It Does

- **📊 Smart Budget Tracking** — Log daily expenses across categories, set daily budget limits with automatic rolling carryover (underspend today = extra tomorrow, overspend today = less tomorrow), and view spending analytics with daily, weekly, and monthly breakdowns.

- **🎙️ Meeting Notes & Transcription** — Record meetings directly from your phone, automatically upload audio to your private S3 storage, and get AI-powered transcriptions via AWS Transcribe. Attach photos to meeting notes for complete context.

- **📱 Cross-Platform Sync** — Use the Android app on the go or the web dashboard at your desk. All data syncs in real-time through your personal backend API.

- **🔔 Daily Accountability** — Configurable reminders ensure you log expenses every day. The system enforces daily check-ins so you never lose track of spending habits.

- **📷 Offline-First** — The Android app works without internet. Expenses, recordings, and photos queue locally and sync automatically when connectivity returns.

- **🔒 Private by Design** — Your data lives in your own AWS account (RDS, S3, ECS). Authentication is handled by AWS Cognito with Google SSO. No third-party analytics, no data sharing.

### Why It Exists

Most productivity tools are fragmented — one app for expenses, another for notes, another for budgeting. Lily AI unifies these into a single system designed to evolve. The modular architecture is built so future features can plug in naturally:

- AI-powered spending optimization and budgeting insights
- Habit tracking and daily routine analysis
- Integration with calendars, health data, and financial tools
- On-device AI assistants that understand your personal patterns

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Backend | Rust + Axum + SQLx |
| Database | PostgreSQL (AWS RDS) |
| Storage | AWS S3 (audio, photos, transcripts) |
| Transcription | AWS Transcribe |
| Web Frontend | React + Vite + TypeScript |
| Android | Kotlin + Jetpack Compose + Hilt |
| Auth | AWS Cognito (Google SSO) |
| Push Notifications | AWS SNS |
| Infrastructure | AWS ECS Fargate + CloudFront + Terraform |

## Project Structure

```
lily-ai/
├── android/         # Kotlin Jetpack Compose mobile app
├── backend/         # Rust Axum REST API server
├── web/             # React Vite web dashboard
├── deployment/      # Docker, Terraform, deploy scripts
└── docs/            # Architecture & API documentation
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
cd lily-ai
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
DATABASE_URL=postgres://user:pass@localhost:5432/lily-ai
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

- **Daily Expense Tracking** — Log expenses with amount, category, date, and notes
- **Rolling Daily Budget** — Set a daily limit (e.g. ₹250); savings roll forward, overspending deducts from future days
- **Meeting Audio Recording** — Record meetings with foreground service, works in background
- **AI Transcription** — Automatic speech-to-text via AWS Transcribe
- **Photo Attachments** — Attach camera/gallery photos to meeting notes
- **Daily Entry Enforcement** — Reminders and UI prompts ensure daily logging
- **Analytics Dashboard** — Daily, weekly, monthly summaries with category breakdowns and budget progress
- **Cross-Platform Sync** — Data syncs between Android and Web via REST API
- **Offline Support** — Android app works offline with automatic sync
- **Push Notifications** — Daily reminders via AWS SNS

## Architecture

All components follow clean architecture with separated layers:
- **Domain** — Models, repository interfaces, business rules
- **Infrastructure** — Database access, API clients, cloud services
- **Services** — Business logic, analytics, transcription workflows
- **Presentation** — UI components (Compose/React) and API handlers

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for details.

## API Documentation

See [docs/API.md](docs/API.md) for the full API reference.

## License

MIT
