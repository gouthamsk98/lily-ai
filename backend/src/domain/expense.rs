use chrono::{DateTime, NaiveDate, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, sqlx::Type)]
#[sqlx(type_name = "varchar", rename_all = "lowercase")]
#[serde(rename_all = "lowercase")]
pub enum Category {
    Food,
    Entertainment,
    Travel,
    Bills,
    Shopping,
    Other,
}

impl std::fmt::Display for Category {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Category::Food => write!(f, "food"),
            Category::Entertainment => write!(f, "entertainment"),
            Category::Travel => write!(f, "travel"),
            Category::Bills => write!(f, "bills"),
            Category::Shopping => write!(f, "shopping"),
            Category::Other => write!(f, "other"),
        }
    }
}

impl std::str::FromStr for Category {
    type Err = String;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "food" => Ok(Category::Food),
            "entertainment" => Ok(Category::Entertainment),
            "travel" => Ok(Category::Travel),
            "bills" => Ok(Category::Bills),
            "shopping" => Ok(Category::Shopping),
            "other" => Ok(Category::Other),
            _ => Err(format!("Invalid category: {}", s)),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Expense {
    pub id: Uuid,
    pub user_id: Uuid,
    pub amount: Decimal,
    pub category: String,
    pub note: Option<String>,
    pub expense_date: NaiveDate,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize)]
pub struct CreateExpense {
    pub amount: Decimal,
    pub category: Category,
    pub note: Option<String>,
    pub expense_date: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateExpense {
    pub amount: Option<Decimal>,
    pub category: Option<Category>,
    pub note: Option<String>,
    pub expense_date: Option<NaiveDate>,
}

#[derive(Debug, Deserialize)]
pub struct ExpenseFilter {
    pub start_date: Option<NaiveDate>,
    pub end_date: Option<NaiveDate>,
    pub category: Option<Category>,
    pub page: Option<i64>,
    pub per_page: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct DailySubmission {
    pub id: Uuid,
    pub user_id: Uuid,
    pub submission_date: NaiveDate,
    pub submitted_at: DateTime<Utc>,
}

#[derive(Debug, Serialize)]
pub struct ExpenseSummary {
    pub total: Decimal,
    pub count: i64,
    pub by_category: Vec<CategorySummary>,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct CategorySummary {
    pub category: String,
    pub total: Decimal,
    pub count: i64,
}
