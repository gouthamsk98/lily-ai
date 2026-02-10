package com.lilyai.app.data.remote.dto

import com.lilyai.app.domain.model.MeetingNote
import com.lilyai.app.domain.model.MeetingPhoto
import com.google.gson.annotations.SerializedName

data class MeetingNoteResponse(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("meeting_title") val meetingTitle: String,
    @SerializedName("audio_file_url") val audioFileUrl: String?,
    @SerializedName("transcript_text") val transcriptText: String?,
    @SerializedName("duration_secs") val durationSecs: Int,
    @SerializedName("transcription_status") val transcriptionStatus: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
) {
    fun toDomain(photos: List<MeetingPhoto> = emptyList()) = MeetingNote(
        id = id, userId = userId, meetingTitle = meetingTitle,
        audioFileUrl = audioFileUrl, transcriptText = transcriptText,
        durationSecs = durationSecs, transcriptionStatus = transcriptionStatus,
        createdAt = createdAt, updatedAt = updatedAt, photos = photos,
    )
}

data class CreateMeetingNoteRequest(
    @SerializedName("meeting_title") val meetingTitle: String?,
    @SerializedName("duration_secs") val durationSecs: Int,
)

data class MeetingPhotoResponse(
    val id: String,
    @SerializedName("meeting_note_id") val meetingNoteId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("s3_key") val s3Key: String,
    @SerializedName("photo_url") val photoUrl: String,
    val caption: String?,
    @SerializedName("created_at") val createdAt: String,
) {
    fun toDomain() = MeetingPhoto(
        id = id, meetingNoteId = meetingNoteId,
        photoUrl = photoUrl, caption = caption, createdAt = createdAt,
    )
}
