package com.lilyai.app.recording

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lilyai.app.domain.repository.MeetingNoteRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class MeetingUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: MeetingNoteRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val noteId = inputData.getString("note_id") ?: return Result.failure()
        val audioPath = inputData.getString("audio_path") ?: return Result.failure()

        return try {
            val file = File(audioPath)
            if (!file.exists()) {
                Log.e(TAG, "Audio file not found: $audioPath")
                return Result.failure()
            }
            repository.uploadAudio(noteId, file)
            Log.i(TAG, "Upload successful for note: $noteId")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed for note: $noteId", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "MeetingUploadWorker"
    }
}
