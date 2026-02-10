use axum::{
    extract::{Multipart, Path, State},
    http::StatusCode,
    Extension, Json,
};
use sqlx::PgPool;
use uuid::Uuid;

use crate::domain::meeting_photo::MeetingNotePhoto;
use crate::errors::AppError;
use crate::infrastructure::{meeting_note_repo, meeting_photo_repo};
use crate::middleware::auth::AuthenticatedUser;
use crate::services::meeting_service::MeetingService;

pub async fn upload_photo(
    State((pool, meeting_service)): State<(PgPool, Option<MeetingService>)>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path(meeting_id): Path<Uuid>,
    mut multipart: Multipart,
) -> Result<(StatusCode, Json<MeetingNotePhoto>), AppError> {
    // Verify meeting note ownership
    meeting_note_repo::find_by_id(&pool, meeting_id, user.id)
        .await?
        .ok_or_else(|| AppError::NotFound("Meeting note not found".into()))?;

    let service = meeting_service
        .ok_or_else(|| AppError::Internal("Storage not configured".into()))?;

    let mut photo_data: Option<Vec<u8>> = None;
    let mut content_type = "image/jpeg".to_string();
    let mut caption: Option<String> = None;

    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| AppError::Validation(format!("Multipart error: {}", e)))?
    {
        match field.name() {
            Some("photo") => {
                content_type = field
                    .content_type()
                    .unwrap_or("image/jpeg")
                    .to_string();
                photo_data = Some(
                    field
                        .bytes()
                        .await
                        .map_err(|e| AppError::Validation(format!("Read error: {}", e)))?
                        .to_vec(),
                );
            }
            Some("caption") => {
                caption = Some(
                    field
                        .text()
                        .await
                        .map_err(|e| AppError::Validation(format!("Read error: {}", e)))?,
                );
            }
            _ => {}
        }
    }

    let data = photo_data.ok_or_else(|| AppError::Validation("No photo field in upload".into()))?;

    let ext = if content_type.contains("png") { "png" } else { "jpg" };
    let photo_id = Uuid::new_v4();
    let s3_key = format!("photos/{}/{}/{}.{}", user.id, meeting_id, photo_id, ext);

    // Generate presigned URL for access
    let photo_url = service.presign_url(&s3_key).await
        .unwrap_or_else(|_| format!(
            "https://{}.s3.{}.amazonaws.com/{}",
            service.bucket(), service.region(), s3_key
        ));

    // Upload to S3
    let s3_config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
    let s3 = aws_sdk_s3::Client::new(&s3_config);
    s3.put_object()
        .bucket(service.bucket())
        .key(&s3_key)
        .body(aws_sdk_s3::primitives::ByteStream::from(data))
        .content_type(&content_type)
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("S3 upload failed: {}", e)))?;

    let photo = meeting_photo_repo::create(
        &pool,
        meeting_id,
        user.id,
        &s3_key,
        &photo_url,
        caption.as_deref(),
    )
    .await?;

    Ok((StatusCode::CREATED, Json(photo)))
}

pub async fn list_photos(
    State((pool, meeting_service)): State<(PgPool, Option<MeetingService>)>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path(meeting_id): Path<Uuid>,
) -> Result<Json<Vec<MeetingNotePhoto>>, AppError> {
    // Verify ownership
    meeting_note_repo::find_by_id(&pool, meeting_id, user.id)
        .await?
        .ok_or_else(|| AppError::NotFound("Meeting note not found".into()))?;

    let mut photos = meeting_photo_repo::find_by_meeting(&pool, meeting_id, user.id).await?;

    // Replace photo_url with presigned URLs
    if let Some(service) = &meeting_service {
        for photo in photos.iter_mut() {
            if let Ok(url) = service.presign_url(&photo.s3_key).await {
                photo.photo_url = url;
            }
        }
    }

    Ok(Json(photos))
}

pub async fn delete_photo(
    State((pool, meeting_service)): State<(PgPool, Option<MeetingService>)>,
    Extension(AuthenticatedUser(user)): Extension<AuthenticatedUser>,
    Path((_meeting_id, photo_id)): Path<(Uuid, Uuid)>,
) -> Result<StatusCode, AppError> {
    if let Some(s3_key) = meeting_photo_repo::delete(&pool, photo_id, user.id).await? {
        if let Some(service) = &meeting_service {
            let s3_config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
            let s3 = aws_sdk_s3::Client::new(&s3_config);
            let _ = s3
                .delete_object()
                .bucket(service.bucket())
                .key(&s3_key)
                .send()
                .await;
        }
        Ok(StatusCode::NO_CONTENT)
    } else {
        Err(AppError::NotFound("Photo not found".into()))
    }
}
