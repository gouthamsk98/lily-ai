# API Reference

Base URL: `http://localhost:8080/api`

All endpoints except `/health` require a valid AWS Cognito JWT token in the `Authorization: Bearer <token>` header.

---

## Authentication

Auth is handled by AWS Cognito. The backend validates Cognito JWTs. Users are auto-provisioned on first API request.

### GET /auth/me

Returns the current user's profile. Creates the user record if it doesn't exist.

**Response:**
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "name": "John Doe",
  "notification_time": "20:00:00",
  "created_at": "2026-01-01T00:00:00Z"
}
```

---

## User Profile

### PUT /users/profile

Update user profile settings.

**Request:**
```json
{
  "name": "New Name",
  "notification_time": "21:00:00"
}
```

---

## Expenses

### POST /expenses

Create a new expense. Automatically marks the day as submitted.

**Request:**
```json
{
  "amount": 25.50,
  "category": "food",
  "note": "Lunch with colleagues",
  "expense_date": "2026-02-10"
}
```

**Categories:** `food`, `entertainment`, `travel`, `bills`, `shopping`, `other`

**Response:** `201 Created` with the created expense object.

### GET /expenses

List expenses with optional filters.

**Query Parameters:**
- `start_date` (optional): Filter from date (YYYY-MM-DD)
- `end_date` (optional): Filter to date (YYYY-MM-DD)
- `category` (optional): Filter by category
- `page` (optional, default: 1): Page number
- `per_page` (optional, default: 20, max: 100): Items per page

**Response:**
```json
[
  {
    "id": "uuid",
    "user_id": "uuid",
    "amount": "25.50",
    "category": "food",
    "note": "Lunch",
    "expense_date": "2026-02-10",
    "created_at": "2026-02-10T12:00:00Z",
    "updated_at": "2026-02-10T12:00:00Z"
  }
]
```

### GET /expenses/:id

Get a single expense by ID.

### PUT /expenses/:id

Update an expense. All fields are optional.

**Request:**
```json
{
  "amount": 30.00,
  "category": "entertainment",
  "note": "Updated note"
}
```

### DELETE /expenses/:id

Delete an expense. Returns `204 No Content`.

---

## Analytics

### GET /analytics/daily

Daily expense summary.

**Query:** `?date=2026-02-10` (optional, defaults to today)

**Response:**
```json
{
  "total": "45.50",
  "count": 3,
  "by_category": [
    { "category": "food", "total": "25.50", "count": 2 },
    { "category": "travel", "total": "20.00", "count": 1 }
  ]
}
```

### GET /analytics/weekly

Weekly summary. Query: `?date=2026-02-10` (any day in the target week).

### GET /analytics/monthly

Monthly summary. Query: `?date=2026-02-10` (any day in the target month).

### GET /analytics/category

Category breakdown with date range.

**Query:** `?start_date=2026-02-01&end_date=2026-02-28`

---

## Daily Status

### GET /daily-status

Check if the user has submitted expenses for today.

**Response:**
```json
{
  "submitted": false,
  "date": "2026-02-10"
}
```

### POST /daily-status/submit

Mark a day as submitted (e.g., for zero-expense days).

**Request:**
```json
{
  "date": "2026-02-10"
}
```

---

## Notifications

### POST /notifications/register

Register a device for push notifications via AWS SNS.

**Request:**
```json
{
  "device_token": "firebase-device-token"
}
```

**Response:**
```json
{
  "endpoint_arn": "arn:aws:sns:..."
}
```

---

## Health Check

### GET /health

Returns `OK`. No authentication required.
