use aws_sdk_s3::primitives::ByteStream;
use aws_sdk_transcribe::types::{LanguageCode, Media, MediaFormat};
use sqlx::PgPool;
use uuid::Uuid;

use crate::errors::AppError;
use crate::infrastructure::meeting_note_repo;

#[derive(Clone)]
pub struct MeetingService {
    s3_client: aws_sdk_s3::Client,
    transcribe_client: aws_sdk_transcribe::Client,
    translate_client: aws_sdk_translate::Client,
    bucket: String,
    region: String,
}

impl MeetingService {
    pub async fn new(bucket: String, region: String) -> Self {
        let config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
        let s3_client = aws_sdk_s3::Client::new(&config);
        let transcribe_client = aws_sdk_transcribe::Client::new(&config);
        let translate_client = aws_sdk_translate::Client::new(&config);
        Self { s3_client, transcribe_client, translate_client, bucket, region }
    }

    pub async fn upload_audio(
        &self,
        pool: &PgPool,
        note_id: Uuid,
        user_id: Uuid,
        audio_data: Vec<u8>,
        content_type: &str,
    ) -> Result<(), AppError> {
        let ext = if content_type.contains("mp4") || content_type.contains("aac") { "m4a" } else { "mp3" };
        let s3_key = format!("meetings/{}/{}.{}", user_id, note_id, ext);
        let audio_url = format!(
            "https://{}.s3.{}.amazonaws.com/{}",
            self.bucket, self.region, s3_key
        );

        self.s3_client
            .put_object()
            .bucket(&self.bucket)
            .key(&s3_key)
            .body(ByteStream::from(audio_data))
            .content_type(content_type)
            .send()
            .await
            .map_err(|e| AppError::Internal(format!("S3 upload failed: {}", e)))?;

        meeting_note_repo::update_audio(pool, note_id, user_id, &s3_key, &audio_url, "transcribing")
            .await?;

        // Start transcription with automatic language identification
        let job_name = format!("lily-meeting-{}", note_id);
        let media_uri = format!("s3://{}/{}", self.bucket, s3_key);
        let media_format = if ext == "m4a" { MediaFormat::Mp4 } else { MediaFormat::Mp3 };

        let result = self.transcribe_client
            .start_transcription_job()
            .transcription_job_name(&job_name)
            .identify_language(true)
            .language_options(LanguageCode::EnUs)
            .language_options(LanguageCode::HiIn)
            .language_options(LanguageCode::KnIn)
            .language_options(LanguageCode::MlIn)
            .media(
                Media::builder()
                    .media_file_uri(&media_uri)
                    .build(),
            )
            .media_format(media_format)
            .output_bucket_name(&self.bucket)
            .output_key(format!("transcripts/{}.json", note_id))
            .send()
            .await;

        match result {
            Ok(_) => {
                tracing::info!("Transcription job started with multi-language detection: {}", job_name);
            }
            Err(e) => {
                tracing::error!("Failed to start transcription: {}", e);
                meeting_note_repo::update_transcription(
                    pool, note_id, "", "failed", Some(&job_name),
                ).await?;
            }
        }

        Ok(())
    }

    pub async fn check_transcription_status(
        &self,
        pool: &PgPool,
        note_id: Uuid,
    ) -> Result<String, AppError> {
        let job_name = format!("lily-meeting-{}", note_id);

        let result = self.transcribe_client
            .get_transcription_job()
            .transcription_job_name(&job_name)
            .send()
            .await
            .map_err(|e| AppError::Internal(format!("Transcribe status check failed: {}", e)))?;

        let job = result.transcription_job()
            .ok_or_else(|| AppError::Internal("No job found".to_string()))?;

        let status = job.transcription_job_status()
            .map(|s| format!("{:?}", s))
            .unwrap_or_else(|| "unknown".to_string());

        // Get detected language
        let detected_lang = job.language_code()
            .map(|l| format!("{:?}", l))
            .unwrap_or_else(|| "en-US".to_string());

        if status == "Completed" {
            let transcript_key = format!("transcripts/{}.json", note_id);
            match self.s3_client
                .get_object()
                .bucket(&self.bucket)
                .key(&transcript_key)
                .send()
                .await
            {
                Ok(resp) => {
                    let body = resp.body.collect().await
                        .map_err(|e| AppError::Internal(format!("Failed to read transcript: {}", e)))?;
                    let body_bytes = body.into_bytes();
                    let json_str = String::from_utf8_lossy(&body_bytes);
                    let raw_transcript = extract_transcript(&json_str);

                    // Translate to English if not already English
                    let final_transcript = if !detected_lang.contains("EnUs") && !raw_transcript.is_empty() {
                        tracing::info!("Detected language: {}, translating to English", detected_lang);
                        self.translate_to_english(&raw_transcript, &detected_lang).await
                            .unwrap_or_else(|e| {
                                tracing::error!("Translation failed: {}, using original", e);
                                raw_transcript.clone()
                            })
                    } else {
                        raw_transcript
                    };

                    meeting_note_repo::update_transcription(
                        pool, note_id, &final_transcript, "completed", Some(&job_name),
                    ).await?;
                }
                Err(e) => {
                    tracing::error!("Failed to fetch transcript from S3: {}", e);
                }
            }
        } else if status == "Failed" {
            meeting_note_repo::update_transcription(
                pool, note_id, "", "failed", Some(&job_name),
            ).await?;
        }

        Ok(status)
    }

    /// Translate text to English using AWS Translate
    async fn translate_to_english(&self, text: &str, source_lang: &str) -> Result<String, AppError> {
        // Map AWS Transcribe language codes to AWS Translate codes
        let source_code = match source_lang {
            s if s.contains("HiIn") => "hi",
            s if s.contains("KnIn") => "kn",
            s if s.contains("MlIn") => "ml",
            s if s.contains("TaIn") => "ta",
            s if s.contains("TeIn") => "te",
            _ => "auto", // auto-detect
        };

        // AWS Translate has 10000 byte limit per request, split if needed
        let mut translated_parts = Vec::new();
        for chunk in split_text(text, 9000) {
            let result = self.translate_client
                .translate_text()
                .text(&chunk)
                .source_language_code(source_code)
                .target_language_code("en")
                .send()
                .await
                .map_err(|e| AppError::Internal(format!("Translation failed: {}", e)))?;

            translated_parts.push(result.translated_text().to_string());
        }

        Ok(translated_parts.join(" "))
    }

    pub fn bucket(&self) -> &str {
        &self.bucket
    }

    pub fn region(&self) -> &str {
        &self.region
    }

    pub async fn presign_url(&self, s3_key: &str) -> Result<String, AppError> {
        use aws_sdk_s3::presigning::PresigningConfig;
        use std::time::Duration;

        let presigned = self.s3_client
            .get_object()
            .bucket(&self.bucket)
            .key(s3_key)
            .presigned(PresigningConfig::expires_in(Duration::from_secs(3600))
                .map_err(|e| AppError::Internal(format!("Presign config error: {}", e)))?)
            .await
            .map_err(|e| AppError::Internal(format!("Presign error: {}", e)))?;

        Ok(presigned.uri().to_string())
    }
}

fn extract_transcript(json_str: &str) -> String {
    serde_json::from_str::<serde_json::Value>(json_str)
        .ok()
        .and_then(|v| {
            v.get("results")?
                .get("transcripts")?
                .get(0)?
                .get("transcript")?
                .as_str()
                .map(|s| s.to_string())
        })
        .unwrap_or_default()
}

/// Split text into chunks respecting sentence boundaries
fn split_text(text: &str, max_bytes: usize) -> Vec<String> {
    if text.len() <= max_bytes {
        return vec![text.to_string()];
    }
    let mut chunks = Vec::new();
    let mut current = String::new();
    for sentence in text.split(". ") {
        if current.len() + sentence.len() + 2 > max_bytes {
            if !current.is_empty() {
                chunks.push(current.clone());
                current.clear();
            }
        }
        if !current.is_empty() {
            current.push_str(". ");
        }
        current.push_str(sentence);
    }
    if !current.is_empty() {
        chunks.push(current);
    }
    chunks
}
