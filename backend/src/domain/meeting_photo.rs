use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct MeetingNotePhoto {
    pub id: Uuid,
    pub meeting_note_id: Uuid,
    pub user_id: Uuid,
    pub s3_key: String,
    pub photo_url: String,
    pub caption: Option<String>,
    pub created_at: DateTime<Utc>,
}
