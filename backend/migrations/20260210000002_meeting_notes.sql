CREATE TABLE meeting_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    meeting_title VARCHAR(255) NOT NULL DEFAULT 'Untitled Meeting',
    audio_file_url TEXT,
    audio_s3_key TEXT,
    transcript_text TEXT,
    duration_secs INTEGER NOT NULL DEFAULT 0,
    transcription_status VARCHAR(50) NOT NULL DEFAULT 'pending'
        CHECK (transcription_status IN ('pending', 'uploading', 'transcribing', 'completed', 'failed')),
    transcription_job_name VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_meeting_notes_user ON meeting_notes(user_id, created_at DESC);
