package com.lilyai.app.domain.repository

import com.lilyai.app.domain.model.MeetingNote
import java.io.File

interface MeetingNoteRepository {
    suspend fun createMeetingNote(title: String?, durationSecs: Int, localAudioPath: String?): MeetingNote
    suspend fun getMeetingNotes(): List<MeetingNote>
    suspend fun getMeetingNote(id: String): MeetingNote
    suspend fun deleteMeetingNote(id: String)
    suspend fun uploadAudio(id: String, audioFile: File): MeetingNote
    suspend fun checkTranscription(id: String): MeetingNote
    suspend fun getPendingUploads(): List<MeetingNote>
}
