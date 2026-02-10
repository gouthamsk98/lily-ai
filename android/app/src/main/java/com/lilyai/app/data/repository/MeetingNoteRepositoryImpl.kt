package com.lilyai.app.data.repository

import com.lilyai.app.data.local.MeetingNoteDao
import com.lilyai.app.data.local.MeetingNoteEntity
import com.lilyai.app.data.remote.ApiService
import com.lilyai.app.data.remote.dto.CreateMeetingNoteRequest
import com.lilyai.app.domain.model.MeetingNote
import com.lilyai.app.domain.repository.MeetingNoteRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingNoteRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val dao: MeetingNoteDao,
) : MeetingNoteRepository {

    override suspend fun createMeetingNote(
        title: String?, durationSecs: Int, localAudioPath: String?
    ): MeetingNote {
        val request = CreateMeetingNoteRequest(title, durationSecs)
        return try {
            val response = apiService.createMeetingNote(request)
            dao.insert(response.toEntity(localAudioPath))
            response.toDomain()
        } catch (e: Exception) {
            val localId = java.util.UUID.randomUUID().toString()
            val now = java.time.Instant.now().toString()
            val entity = MeetingNoteEntity(
                id = localId, userId = "", meetingTitle = title ?: "Untitled Meeting",
                localAudioPath = localAudioPath, audioFileUrl = null,
                transcriptText = null, durationSecs = durationSecs,
                transcriptionStatus = "pending", createdAt = now, updatedAt = now,
                uploaded = false,
            )
            dao.insert(entity)
            MeetingNote(localId, "", title ?: "Untitled Meeting", null, null, durationSecs, "pending", now, now)
        }
    }

    override suspend fun getMeetingNotes(): List<MeetingNote> {
        return try {
            val response = apiService.getMeetingNotes()
            val entities = response.map { it.toEntity(null) }
            dao.insertAll(entities)
            response.map { it.toDomain() }
        } catch (e: Exception) {
            dao.getAll().map { it.toDomain() }
        }
    }

    override suspend fun getMeetingNote(id: String): MeetingNote {
        return try {
            apiService.getMeetingNote(id).toDomain()
        } catch (e: Exception) {
            dao.getById(id)?.toDomain() ?: throw e
        }
    }

    override suspend fun deleteMeetingNote(id: String) {
        try { apiService.deleteMeetingNote(id) } catch (_: Exception) {}
        dao.delete(id)
    }

    override suspend fun uploadAudio(id: String, audioFile: File): MeetingNote {
        val mediaType = "audio/mp4".toMediaTypeOrNull()
        val requestBody = audioFile.asRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("audio", audioFile.name, requestBody)
        val response = apiService.uploadMeetingAudio(id, part)
        dao.markUploaded(id)
        return response.toDomain()
    }

    override suspend fun checkTranscription(id: String): MeetingNote {
        val response = apiService.checkTranscription(id)
        if (response.transcriptionStatus == "completed" && response.transcriptText != null) {
            dao.updateTranscription(id, response.transcriptText, "completed")
        }
        return response.toDomain()
    }

    override suspend fun getPendingUploads(): List<MeetingNote> {
        return dao.getPendingUploads().map { it.toDomain() }
    }

    private fun com.lilyai.app.data.remote.dto.MeetingNoteResponse.toEntity(localPath: String?) =
        MeetingNoteEntity(
            id = id, userId = userId, meetingTitle = meetingTitle,
            localAudioPath = localPath, audioFileUrl = audioFileUrl,
            transcriptText = transcriptText, durationSecs = durationSecs,
            transcriptionStatus = transcriptionStatus,
            createdAt = createdAt, updatedAt = updatedAt,
            uploaded = audioFileUrl != null,
        )

    private fun MeetingNoteEntity.toDomain() = MeetingNote(
        id = id, userId = userId, meetingTitle = meetingTitle,
        audioFileUrl = audioFileUrl ?: localAudioPath,
        transcriptText = transcriptText, durationSecs = durationSecs,
        transcriptionStatus = transcriptionStatus,
        createdAt = createdAt, updatedAt = updatedAt,
    )
}
