use axum::{
    extract::{Multipart, Path, State},
    http::StatusCode,
    Extension, Json,
};
use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::meeting_note::*;
use crate::errors::AppError;
use crate::infrastructure::meeting_note_repo;
use crate::middleware::auth::AuthenticatedUser;
use crate::services::meeting_service::MeetingService;

pub async fn create_meeting_note(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Json(body): Json<CreateMeetingNote>,
) -> Result<(StatusCode, Json<MeetingNote>), AppError> {
    let note = meeting_note_repo::create(&pool, user.id, &body).await?;
    Ok((StatusCode::CREATED, Json(note)))
}

pub async fn list_meeting_notes(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
) -> Result<Json<Vec<MeetingNote>>, AppError> {
    let notes = meeting_note_repo::find_all(&pool, user.id).await?;
    Ok(Json(notes))
}

pub async fn get_meeting_note(
    State(pool): State<PgPool>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path(id): Path<Uuid>,
) -> Result<Json<MeetingNote>, AppError> {
    let note = meeting_note_repo::find_by_id(&pool, id, user.id)
        .await?
        .ok_or_else(|| AppError::NotFound("Meeting note not found".into()))?;
    Ok(Json(note))
}

pub async fn delete_meeting_note(
    State((pool, meeting_service)): State<(PgPool, Option<MeetingService>)>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, AppError> {
    // Delete audio from S3 if exists
    if let Some(service) = &meeting_service {
        if let Some(note) = meeting_note_repo::find_by_id(&pool, id, user.id).await? {
            if let Some(s3_key) = &note.audio_s3_key {
                let s3_config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
                let s3 = aws_sdk_s3::Client::new(&s3_config);
                let _ = s3.delete_object()
                    .bucket(service.bucket())
                    .key(s3_key)
                    .send()
                    .await;
            }
        }
    }

    let deleted = meeting_note_repo::delete(&pool, id, user.id).await?;
    if deleted {
        Ok(StatusCode::NO_CONTENT)
    } else {
        Err(AppError::NotFound("Meeting note not found".into()))
    }
}

pub async fn upload_audio(
    State((pool, meeting_service)): State<(PgPool, Option<MeetingService>)>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path(id): Path<Uuid>,
    mut multipart: Multipart,
) -> Result<Json<MeetingNote>, AppError> {
    let meeting_service = meeting_service
        .ok_or_else(|| AppError::Internal("Meeting audio storage not configured".into()))?;

    // Verify ownership
    meeting_note_repo::find_by_id(&pool, id, user.id)
        .await?
        .ok_or_else(|| AppError::NotFound("Meeting note not found".into()))?;

    let mut audio_data: Option<Vec<u8>> = None;
    let mut content_type = "audio/mp4".to_string();

    while let Some(field) = multipart.next_field().await
        .map_err(|e| AppError::Validation(format!("Multipart error: {}", e)))? {
        if field.name() == Some("audio") {
            content_type = field.content_type()
                .unwrap_or("audio/mp4")
                .to_string();
            audio_data = Some(
                field.bytes().await
                    .map_err(|e| AppError::Validation(format!("Failed to read audio: {}", e)))?
                    .to_vec()
            );
        }
    }

    let data = audio_data.ok_or_else(|| AppError::Validation("No audio field in upload".into()))?;

    meeting_service.upload_audio(&pool, id, user.id, data, &content_type).await?;

    let note = meeting_note_repo::find_by_id(&pool, id, user.id)
        .await?
        .ok_or_else(|| AppError::Internal("Note disappeared after upload".into()))?;

    Ok(Json(note))
}

pub async fn check_transcription(
    State((pool, meeting_service)): State<(PgPool, Option<MeetingService>)>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path(id): Path<Uuid>,
) -> Result<Json<MeetingNote>, AppError> {
    // Verify ownership
    let note = meeting_note_repo::find_by_id(&pool, id, user.id)
        .await?
        .ok_or_else(|| AppError::NotFound("Meeting note not found".into()))?;

    if note.transcription_status == "transcribing" {
        if let Some(service) = &meeting_service {
            let _ = service.check_transcription_status(&pool, id).await;
        }
    }

    // Re-fetch after potential update
    let note = meeting_note_repo::find_by_id(&pool, id, user.id)
        .await?
        .ok_or_else(|| AppError::Internal("Note disappeared".into()))?;

    Ok(Json(note))
}
