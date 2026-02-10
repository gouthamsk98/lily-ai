CREATE TABLE meeting_note_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_note_id UUID NOT NULL REFERENCES meeting_notes(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    s3_key TEXT NOT NULL,
    photo_url TEXT NOT NULL,
    caption TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_meeting_photos_note ON meeting_note_photos(meeting_note_id);
