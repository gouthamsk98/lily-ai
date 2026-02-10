package com.lilyai.app.data.local

import androidx.room.*

@Entity(tableName = "meeting_notes")
data class MeetingNoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val meetingTitle: String,
    val localAudioPath: String?,
    val audioFileUrl: String?,
    val transcriptText: String?,
    val durationSecs: Int,
    val transcriptionStatus: String,
    val createdAt: String,
    val updatedAt: String,
    val uploaded: Boolean = false,
)

@Dao
interface MeetingNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: MeetingNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<MeetingNoteEntity>)

    @Query("SELECT * FROM meeting_notes ORDER BY createdAt DESC")
    suspend fun getAll(): List<MeetingNoteEntity>

    @Query("SELECT * FROM meeting_notes WHERE id = :id")
    suspend fun getById(id: String): MeetingNoteEntity?

    @Query("SELECT * FROM meeting_notes WHERE uploaded = 0 AND localAudioPath IS NOT NULL")
    suspend fun getPendingUploads(): List<MeetingNoteEntity>

    @Query("DELETE FROM meeting_notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE meeting_notes SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: String)

    @Query("UPDATE meeting_notes SET transcriptText = :text, transcriptionStatus = :status WHERE id = :id")
    suspend fun updateTranscription(id: String, text: String, status: String)
}
