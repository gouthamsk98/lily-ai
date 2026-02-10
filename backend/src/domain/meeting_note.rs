use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct MeetingNote {
    pub id: Uuid,
    pub user_id: Uuid,
    pub meeting_title: String,
    pub audio_file_url: Option<String>,
    pub audio_s3_key: Option<String>,
    pub transcript_text: Option<String>,
    pub duration_secs: i32,
    pub transcription_status: String,
    pub transcription_job_name: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize)]
pub struct CreateMeetingNote {
    pub meeting_title: Option<String>,
    pub duration_secs: i32,
}

#[derive(Debug, Deserialize)]
pub struct UpdateMeetingNote {
    pub meeting_title: Option<String>,
    pub transcript_text: Option<String>,
    pub transcription_status: Option<String>,
}
