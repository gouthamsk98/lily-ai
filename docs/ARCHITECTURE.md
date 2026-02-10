# Architecture

## Overview

Lily AI follows **clean architecture** principles across all three components (backend, web, Android). Each component has clearly separated layers with dependencies flowing inward.

```
┌─────────────────────────────────────────────────────┐
│                    Clients                          │
│  ┌──────────────┐           ┌──────────────────┐    │
│  │ Android App  │           │    Web App        │    │
│  │ (Kotlin/     │           │    (React/Vite)   │    │
│  │  Compose)    │           │                   │    │
│  └──────┬───────┘           └────────┬──────────┘    │
│         │          HTTPS             │               │
│         └──────────┬─────────────────┘               │
│                    ▼                                 │
│  ┌─────────────────────────────────────────────┐     │
│  │         AWS Application Load Balancer        │     │
│  └──────────────────┬──────────────────────────┘     │
│                     ▼                                │
│  ┌─────────────────────────────────────────────┐     │
│  │         Backend (Rust/Axum)                  │     │
│  │   ECS Fargate Container                      │     │
│  └──────────────────┬──────────────────────────┘     │
│                     ▼                                │
│  ┌──────────────┐  ┌──────────────┐                  │
│  │ PostgreSQL   │  │  AWS SNS     │                  │
│  │ (RDS)        │  │  (Push)      │                  │
│  └──────────────┘  └──────────────┘                  │
│                                                      │
│              AWS Cognito (Auth)                       │
└─────────────────────────────────────────────────────┘
```

## Backend Architecture (Rust)

```
src/
├── main.rs              # Entry point, router setup
├── config.rs            # Environment configuration
├── errors.rs            # Error types and HTTP mapping
├── domain/              # Layer 1: Domain Models
│   ├── user.rs          # User model
│   └── expense.rs       # Expense, Category, Summary models
├── infrastructure/      # Layer 2: Data Access
│   ├── db.rs            # Database connection pool
│   ├── user_repo.rs     # User CRUD operations
│   └── expense_repo.rs  # Expense CRUD + analytics queries
├── services/            # Layer 3: Business Logic
│   ├── auth_service.rs  # Cognito JWT validation + JWKS caching
│   ├── expense_service.rs    # Expense business rules
│   ├── analytics_service.rs  # Summary calculations
│   ├── daily_check_service.rs # Daily submission tracking
│   └── notification_service.rs # SNS push notifications
├── api/                 # Layer 4: HTTP Handlers
│   ├── auth.rs          # GET /auth/me
│   ├── expenses.rs      # Expense CRUD endpoints
│   ├── analytics.rs     # Analytics endpoints
│   ├── users.rs         # Profile management
│   └── daily_status.rs  # Daily status + notifications
└── middleware/
    └── auth.rs          # JWT validation middleware
```

**Key Design Decisions:**
- **No ORM**: SQLx with raw SQL for maximum control and compile-time query checking
- **Auto-provisioning**: Users are created on first API call from Cognito JWT claims
- **Stateless**: No server-side sessions; JWT validation on every request

## Web Frontend Architecture (React)

```
src/
├── api/           # API client layer (Axios + Cognito token interceptor)
├── components/    # Reusable UI components
├── pages/         # Route-level page components
├── hooks/         # Custom React hooks
├── context/       # React Context (Auth)
├── types/         # TypeScript interfaces
└── utils/         # Formatting utilities
```

**Auth Flow:**
1. User clicks "Sign in with Google"
2. AWS Amplify redirects to Cognito Hosted UI
3. Cognito handles Google OAuth and issues JWT tokens
4. Amplify stores tokens and attaches them to API requests

## Android Architecture (Kotlin)

```
com.lilyai/
├── domain/              # Layer 1: Domain
│   ├── model/           # Data classes (Expense, User, Category)
│   └── repository/      # Repository interfaces
├── data/                # Layer 2: Data
│   ├── local/           # Room database (offline cache)
│   ├── remote/          # Retrofit API service + DTOs
│   └── repository/      # Repository implementations
├── di/                  # Hilt dependency injection
├── ui/                  # Layer 3: Presentation
│   ├── theme/           # Material Design 3 theme
│   ├── navigation/      # Navigation graph
│   ├── screens/         # Screen composables + ViewModels
│   └── components/      # Shared UI components
├── notification/        # Push notification workers
└── sync/                # Background sync workers
```

**Offline Strategy:**
- Room database as local cache (source of truth when offline)
- `SyncWorker` (WorkManager) syncs pending expenses when connectivity returns
- Conflict resolution: server timestamp wins

## Daily Expense Enforcement

The system enforces daily expense entry through multiple mechanisms:

1. **Backend**: `daily_submissions` table tracks per-user, per-date submission status
2. **Web**: Modal/banner shown on dashboard when today's entry is missing
3. **Android**: WorkManager sends daily push notification reminders
4. **Zero-expense**: Users can submit "0 expense" days as valid entries

## Authentication Flow (AWS Cognito)

```
User → "Sign in with Google" → Cognito Hosted UI → Google OAuth
  → Cognito issues JWT (id_token, access_token, refresh_token)
  → Client stores tokens (Amplify SDK)
  → API requests include JWT in Authorization header
  → Backend validates JWT against Cognito JWKS endpoint
  → Auto-creates user record from JWT claims (sub, email, name)
```

## Future Extension Points

The modular architecture supports easy expansion:

- **AI Insights**: Add `services/insights_service.rs` for spending optimization
- **Budget Goals**: Add `domain/budget.rs` + new repository + endpoints
- **Financial Integrations**: Add `infrastructure/plaid_client.rs` for bank sync
- **Multi-currency**: Extend `Expense` model with currency field
