use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::meeting_note::*;
use crate::errors::AppError;

pub async fn create(
    pool: &PgPool,
    user_id: Uuid,
    note: &CreateMeetingNote,
) -> Result<MeetingNote, AppError> {
    let title = note.meeting_title.clone().unwrap_or_else(|| "Untitled Meeting".to_string());
    let row = sqlx::query_as::<_, MeetingNote>(
        r#"INSERT INTO meeting_notes (user_id, meeting_title, duration_secs, transcription_status)
           VALUES ($1, $2, $3, 'pending')
           RETURNING id, user_id, meeting_title, audio_file_url, audio_s3_key,
                     transcript_text, duration_secs, transcription_status,
                     transcription_job_name, created_at, updated_at"#,
    )
    .bind(user_id)
    .bind(&title)
    .bind(note.duration_secs)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn find_by_id(
    pool: &PgPool,
    id: Uuid,
    user_id: Uuid,
) -> Result<Option<MeetingNote>, AppError> {
    let row = sqlx::query_as::<_, MeetingNote>(
        r#"SELECT id, user_id, meeting_title, audio_file_url, audio_s3_key,
                  transcript_text, duration_secs, transcription_status,
                  transcription_job_name, created_at, updated_at
           FROM meeting_notes WHERE id = $1 AND user_id = $2"#,
    )
    .bind(id)
    .bind(user_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn find_all(
    pool: &PgPool,
    user_id: Uuid,
) -> Result<Vec<MeetingNote>, AppError> {
    let rows = sqlx::query_as::<_, MeetingNote>(
        r#"SELECT id, user_id, meeting_title, audio_file_url, audio_s3_key,
                  transcript_text, duration_secs, transcription_status,
                  transcription_job_name, created_at, updated_at
           FROM meeting_notes WHERE user_id = $1
           ORDER BY created_at DESC LIMIT 100"#,
    )
    .bind(user_id)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn update_audio(
    pool: &PgPool,
    id: Uuid,
    user_id: Uuid,
    s3_key: &str,
    audio_url: &str,
    status: &str,
) -> Result<Option<MeetingNote>, AppError> {
    let row = sqlx::query_as::<_, MeetingNote>(
        r#"UPDATE meeting_notes
           SET audio_s3_key = $3, audio_file_url = $4, transcription_status = $5, updated_at = NOW()
           WHERE id = $1 AND user_id = $2
           RETURNING id, user_id, meeting_title, audio_file_url, audio_s3_key,
                     transcript_text, duration_secs, transcription_status,
                     transcription_job_name, created_at, updated_at"#,
    )
    .bind(id)
    .bind(user_id)
    .bind(s3_key)
    .bind(audio_url)
    .bind(status)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn update_transcription(
    pool: &PgPool,
    id: Uuid,
    transcript: &str,
    status: &str,
    job_name: Option<&str>,
) -> Result<Option<MeetingNote>, AppError> {
    let row = sqlx::query_as::<_, MeetingNote>(
        r#"UPDATE meeting_notes
           SET transcript_text = $2, transcription_status = $3,
               transcription_job_name = $4, updated_at = NOW()
           WHERE id = $1
           RETURNING id, user_id, meeting_title, audio_file_url, audio_s3_key,
                     transcript_text, duration_secs, transcription_status,
                     transcription_job_name, created_at, updated_at"#,
    )
    .bind(id)
    .bind(transcript)
    .bind(status)
    .bind(job_name)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}

pub async fn delete(pool: &PgPool, id: Uuid, user_id: Uuid) -> Result<bool, AppError> {
    let result = sqlx::query("DELETE FROM meeting_notes WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(user_id)
        .execute(pool)
        .await?;
    Ok(result.rows_affected() > 0)
}
