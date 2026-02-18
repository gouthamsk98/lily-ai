use chrono::{Datelike, NaiveDate};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sqlx::PgPool;
use uuid::Uuid;

use crate::errors::AppError;

/// Tool definitions for OpenAI Realtime function calling
pub fn tool_definitions() -> Vec<Value> {
    vec![
        json!({
            "type": "function",
            "name": "getRecentMeetings",
            "description": "Get recent meeting notes. Returns a list of meetings with titles, dates, durations, and transcript previews.",
            "parameters": {
                "type": "object",
                "properties": {
                    "limit": {
                        "type": "integer",
                        "description": "Number of recent meetings to return (default 5, max 10)"
                    }
                },
                "required": []
            }
        }),
        json!({
            "type": "function",
            "name": "getMeetingNotesByDate",
            "description": "Get meeting notes for a specific date.",
            "parameters": {
                "type": "object",
                "properties": {
                    "date": {
                        "type": "string",
                        "description": "Date in YYYY-MM-DD format"
                    }
                },
                "required": ["date"]
            }
        }),
        json!({
            "type": "function",
            "name": "searchMeetingNotes",
            "description": "Search meeting notes and transcripts by keyword.",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {
                        "type": "string",
                        "description": "Search keyword to find in meeting titles and transcripts"
                    }
                },
                "required": ["keyword"]
            }
        }),
        json!({
            "type": "function",
            "name": "getMeetingSummary",
            "description": "Get the full transcript of a specific meeting by its title or most recent match.",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": {
                        "type": "string",
                        "description": "Meeting title to search for"
                    }
                },
                "required": ["title"]
            }
        }),
        json!({
            "type": "function",
            "name": "getDailyBudget",
            "description": "Get today's budget status including spent amount, remaining budget, and carryover.",
            "parameters": {
                "type": "object",
                "properties": {
                    "date": {
                        "type": "string",
                        "description": "Date in YYYY-MM-DD format (defaults to today)"
                    }
                },
                "required": []
            }
        }),
        json!({
            "type": "function",
            "name": "getExpensesByCategory",
            "description": "Get expense breakdown by category for a time period.",
            "parameters": {
                "type": "object",
                "properties": {
                    "period": {
                        "type": "string",
                        "enum": ["today", "week", "month"],
                        "description": "Time period for the summary"
                    },
                    "category": {
                        "type": "string",
                        "enum": ["food", "entertainment", "travel", "bills", "shopping", "other"],
                        "description": "Optional specific category filter"
                    }
                },
                "required": ["period"]
            }
        }),
        json!({
            "type": "function",
            "name": "getRecentExpenses",
            "description": "Get recent expense entries with details.",
            "parameters": {
                "type": "object",
                "properties": {
                    "limit": {
                        "type": "integer",
                        "description": "Number of recent expenses to return (default 10)"
                    }
                },
                "required": []
            }
        }),
    ]
}

// -- Tool execution --

pub async fn execute_tool(
    pool: &PgPool,
    user_id: Uuid,
    tool_name: &str,
    args: &Value,
) -> Result<String, AppError> {
    let result = match tool_name {
        "getRecentMeetings" => get_recent_meetings(pool, user_id, args).await?,
        "getMeetingNotesByDate" => get_meeting_notes_by_date(pool, user_id, args).await?,
        "searchMeetingNotes" => search_meeting_notes(pool, user_id, args).await?,
        "getMeetingSummary" => get_meeting_summary(pool, user_id, args).await?,
        "getDailyBudget" => get_daily_budget(pool, user_id, args).await?,
        "getExpensesByCategory" => get_expenses_by_category(pool, user_id, args).await?,
        "getRecentExpenses" => get_recent_expenses(pool, user_id, args).await?,
        _ => json!({"error": "Unknown tool"}).to_string(),
    };
    Ok(result)
}

#[derive(sqlx::FromRow)]
struct MeetingRow {
    id: Uuid,
    meeting_title: String,
    transcript_text: Option<String>,
    transcription_status: String,
    duration: i32,
    created_at: chrono::DateTime<chrono::Utc>,
}

async fn get_recent_meetings(pool: &PgPool, user_id: Uuid, args: &Value) -> Result<String, AppError> {
    let limit = args.get("limit").and_then(|v| v.as_i64()).unwrap_or(5).min(10) as i32;
    let rows: Vec<MeetingRow> = sqlx::query_as(
        "SELECT id, meeting_title, transcript_text, transcription_status, duration, created_at \
         FROM meeting_notes WHERE user_id = $1 ORDER BY created_at DESC LIMIT $2"
    )
    .bind(user_id).bind(limit)
    .fetch_all(pool).await?;

    let results: Vec<Value> = rows.iter().map(|r| {
        let preview = r.transcript_text.as_deref().unwrap_or("No transcript")
            .chars().take(200).collect::<String>();
        json!({
            "title": r.meeting_title,
            "date": r.created_at.format("%Y-%m-%d %H:%M").to_string(),
            "duration_minutes": r.duration / 60,
            "status": r.transcription_status,
            "transcript_preview": preview,
        })
    }).collect();
    Ok(json!({"meetings": results, "count": results.len()}).to_string())
}

async fn get_meeting_notes_by_date(pool: &PgPool, user_id: Uuid, args: &Value) -> Result<String, AppError> {
    let date = args.get("date").and_then(|v| v.as_str()).unwrap_or("");
    let rows: Vec<MeetingRow> = sqlx::query_as(
        "SELECT id, meeting_title, transcript_text, transcription_status, duration, created_at \
         FROM meeting_notes WHERE user_id = $1 AND created_at::date = $2::date ORDER BY created_at DESC"
    )
    .bind(user_id).bind(date)
    .fetch_all(pool).await?;

    let results: Vec<Value> = rows.iter().map(|r| {
        json!({
            "title": r.meeting_title,
            "date": r.created_at.format("%Y-%m-%d %H:%M").to_string(),
            "duration_minutes": r.duration / 60,
            "transcript": r.transcript_text.as_deref().unwrap_or("No transcript"),
        })
    }).collect();
    Ok(json!({"meetings": results, "count": results.len()}).to_string())
}

async fn search_meeting_notes(pool: &PgPool, user_id: Uuid, args: &Value) -> Result<String, AppError> {
    let keyword = args.get("keyword").and_then(|v| v.as_str()).unwrap_or("");
    let pattern = format!("%{}%", keyword);
    let rows: Vec<MeetingRow> = sqlx::query_as(
        "SELECT id, meeting_title, transcript_text, transcription_status, duration, created_at \
         FROM meeting_notes WHERE user_id = $1 AND \
         (meeting_title ILIKE $2 OR transcript_text ILIKE $2) ORDER BY created_at DESC LIMIT 10"
    )
    .bind(user_id).bind(&pattern)
    .fetch_all(pool).await?;

    let results: Vec<Value> = rows.iter().map(|r| {
        let preview = r.transcript_text.as_deref().unwrap_or("")
            .chars().take(300).collect::<String>();
        json!({
            "title": r.meeting_title,
            "date": r.created_at.format("%Y-%m-%d %H:%M").to_string(),
            "transcript_preview": preview,
        })
    }).collect();
    Ok(json!({"meetings": results, "count": results.len()}).to_string())
}

async fn get_meeting_summary(pool: &PgPool, user_id: Uuid, args: &Value) -> Result<String, AppError> {
    let title = args.get("title").and_then(|v| v.as_str()).unwrap_or("");
    let pattern = format!("%{}%", title);
    let row: Option<MeetingRow> = sqlx::query_as(
        "SELECT id, meeting_title, transcript_text, transcription_status, duration, created_at \
         FROM meeting_notes WHERE user_id = $1 AND meeting_title ILIKE $2 \
         ORDER BY created_at DESC LIMIT 1"
    )
    .bind(user_id).bind(&pattern)
    .fetch_optional(pool).await?;

    match row {
        Some(r) => Ok(json!({
            "title": r.meeting_title,
            "date": r.created_at.format("%Y-%m-%d %H:%M").to_string(),
            "duration_minutes": r.duration / 60,
            "full_transcript": r.transcript_text.as_deref().unwrap_or("No transcript available"),
        }).to_string()),
        None => Ok(json!({"error": "No meeting found matching that title"}).to_string()),
    }
}

#[derive(sqlx::FromRow)]
struct BudgetSettingRow {
    daily_budget: Decimal,
}

async fn get_daily_budget(pool: &PgPool, user_id: Uuid, args: &Value) -> Result<String, AppError> {
    let date_str = args.get("date").and_then(|v| v.as_str())
        .unwrap_or(&chrono::Utc::now().format("%Y-%m-%d").to_string())
        .to_string();

    let setting: Option<BudgetSettingRow> = sqlx::query_as(
        "SELECT daily_budget FROM user_budget_settings WHERE user_id = $1"
    ).bind(user_id).fetch_optional(pool).await?;

    let daily_budget = setting.map(|s| s.daily_budget).unwrap_or(Decimal::ZERO);

    let spent: (Decimal,) = sqlx::query_as(
        "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE user_id = $1 AND expense_date = $2"
    ).bind(user_id).bind(&date_str).fetch_one(pool).await?;

    Ok(json!({
        "date": date_str,
        "daily_budget": daily_budget.to_string(),
        "spent": spent.0.to_string(),
        "remaining": (daily_budget - spent.0).to_string(),
        "currency": "INR"
    }).to_string())
}

async fn get_expenses_by_category(pool: &PgPool, user_id: Uuid, args: &Value) -> Result<String, AppError> {
    let period = args.get("period").and_then(|v| v.as_str()).unwrap_or("today");
    let category = args.get("category").and_then(|v| v.as_str());

    let today = chrono::Utc::now().date_naive();
    let (start, end) = match period {
        "week" => {
            let start = today - chrono::Duration::days(today.weekday().num_days_from_monday() as i64);
            (start, today)
        }
        "month" => {
            let start = NaiveDate::from_ymd_opt(today.year(), today.month(), 1).unwrap();
            (start, today)
        }
        _ => (today, today),
    };

    let start_str = start.format("%Y-%m-%d").to_string();
    let end_str = end.format("%Y-%m-%d").to_string();

    #[derive(sqlx::FromRow)]
    struct CatRow { category: String, total: Decimal, count: i64 }

    let rows: Vec<CatRow> = if let Some(cat) = category {
        sqlx::query_as(
            "SELECT category, COALESCE(SUM(amount), 0) as total, COUNT(*) as count \
             FROM expenses WHERE user_id = $1 AND expense_date >= $2 AND expense_date <= $3 AND category = $4 \
             GROUP BY category"
        ).bind(user_id).bind(&start_str).bind(&end_str).bind(cat)
        .fetch_all(pool).await?
    } else {
        sqlx::query_as(
            "SELECT category, COALESCE(SUM(amount), 0) as total, COUNT(*) as count \
             FROM expenses WHERE user_id = $1 AND expense_date >= $2 AND expense_date <= $3 \
             GROUP BY category ORDER BY total DESC"
        ).bind(user_id).bind(&start_str).bind(&end_str)
        .fetch_all(pool).await?
    };

    let total: Decimal = rows.iter().map(|r| r.total).sum();
    let categories: Vec<Value> = rows.iter().map(|r| json!({
        "category": r.category,
        "amount": r.total.to_string(),
        "count": r.count,
    })).collect();

    Ok(json!({
        "period": period,
        "start_date": start_str,
        "end_date": end_str,
        "total": total.to_string(),
        "categories": categories,
        "currency": "INR"
    }).to_string())
}

#[derive(sqlx::FromRow)]
struct ExpenseRow {
    amount: Decimal,
    category: String,
    note: Option<String>,
    expense_date: String,
}

async fn get_recent_expenses(pool: &PgPool, user_id: Uuid, args: &Value) -> Result<String, AppError> {
    let limit = args.get("limit").and_then(|v| v.as_i64()).unwrap_or(10).min(20) as i32;
    let rows: Vec<ExpenseRow> = sqlx::query_as(
        "SELECT amount, category, note, expense_date FROM expenses \
         WHERE user_id = $1 ORDER BY expense_date DESC, created_at DESC LIMIT $2"
    ).bind(user_id).bind(limit)
    .fetch_all(pool).await?;

    let results: Vec<Value> = rows.iter().map(|r| json!({
        "amount": r.amount.to_string(),
        "category": r.category,
        "note": r.note,
        "date": r.expense_date,
        "currency": "INR"
    })).collect();
    Ok(json!({"expenses": results, "count": results.len()}).to_string())
}
