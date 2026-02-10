package com.lilyai.app.domain.model

data class MeetingNote(
    val id: String,
    val userId: String,
    val meetingTitle: String,
    val audioFileUrl: String?,
    val transcriptText: String?,
    val durationSecs: Int,
    val transcriptionStatus: String,
    val createdAt: String,
    val updatedAt: String,
    val photos: List<MeetingPhoto> = emptyList(),
)

data class MeetingPhoto(
    val id: String,
    val meetingNoteId: String,
    val photoUrl: String,
    val caption: String?,
    val createdAt: String,
)
