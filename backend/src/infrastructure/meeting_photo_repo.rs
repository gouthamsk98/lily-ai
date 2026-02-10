use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::meeting_photo::MeetingNotePhoto;
use crate::errors::AppError;

pub async fn create(
    pool: &PgPool,
    meeting_note_id: Uuid,
    user_id: Uuid,
    s3_key: &str,
    photo_url: &str,
    caption: Option<&str>,
) -> Result<MeetingNotePhoto, AppError> {
    let row = sqlx::query_as::<_, MeetingNotePhoto>(
        r#"INSERT INTO meeting_note_photos (meeting_note_id, user_id, s3_key, photo_url, caption)
           VALUES ($1, $2, $3, $4, $5)
           RETURNING id, meeting_note_id, user_id, s3_key, photo_url, caption, created_at"#,
    )
    .bind(meeting_note_id)
    .bind(user_id)
    .bind(s3_key)
    .bind(photo_url)
    .bind(caption)
    .fetch_one(pool)
    .await?;
    Ok(row)
}

pub async fn find_by_meeting(
    pool: &PgPool,
    meeting_note_id: Uuid,
    user_id: Uuid,
) -> Result<Vec<MeetingNotePhoto>, AppError> {
    let rows = sqlx::query_as::<_, MeetingNotePhoto>(
        r#"SELECT id, meeting_note_id, user_id, s3_key, photo_url, caption, created_at
           FROM meeting_note_photos
           WHERE meeting_note_id = $1 AND user_id = $2
           ORDER BY created_at ASC"#,
    )
    .bind(meeting_note_id)
    .bind(user_id)
    .fetch_all(pool)
    .await?;
    Ok(rows)
}

pub async fn delete(pool: &PgPool, id: Uuid, user_id: Uuid) -> Result<Option<String>, AppError> {
    let row = sqlx::query_scalar::<_, String>(
        "DELETE FROM meeting_note_photos WHERE id = $1 AND user_id = $2 RETURNING s3_key",
    )
    .bind(id)
    .bind(user_id)
    .fetch_optional(pool)
    .await?;
    Ok(row)
}
