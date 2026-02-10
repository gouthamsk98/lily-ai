package com.lilyai.app.ui.screens.meetings

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.lilyai.app.domain.model.MeetingNote
import com.lilyai.app.domain.repository.MeetingNoteRepository
import com.lilyai.app.recording.AudioRecorderService
import com.lilyai.app.recording.MeetingUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class MeetingNotesState(
    val notes: List<MeetingNote> = emptyList(),
    val selectedNote: MeetingNote? = null,
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingDurationSecs: Int = 0,
    val meetingTitle: String = "",
    val error: String? = null,
    val isUploadingPhoto: Boolean = false,
)

@HiltViewModel
class MeetingNotesViewModel @Inject constructor(
    application: Application,
    private val repository: MeetingNoteRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MeetingNotesState())
    val state: StateFlow<MeetingNotesState> = _state

    private var recorderService: AudioRecorderService? = null
    private var recordingFile: File? = null
    private var timerRunning = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            recorderService = (service as AudioRecorderService.RecorderBinder).getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            recorderService = null
        }
    }

    init {
        loadNotes()
        bindService()
    }

    private fun bindService() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioRecorderService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun loadNotes() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val notes = repository.getMeetingNotes()
                _state.value = _state.value.copy(notes = notes, isLoading = false)
                // Auto-poll transcription for any notes that are still transcribing
                pollTranscribingNotes(notes)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun pollTranscribingNotes(notes: List<MeetingNote>) {
        val transcribing = notes.filter { it.transcriptionStatus == "transcribing" }
        if (transcribing.isEmpty()) return
        viewModelScope.launch {
            for (note in transcribing) {
                try {
                    val updated = repository.checkTranscription(note.id)
                    if (updated.transcriptionStatus != "transcribing") {
                        // Refresh list to show updated status
                        val currentNotes = _state.value.notes.map {
                            if (it.id == note.id) updated else it
                        }
                        _state.value = _state.value.copy(notes = currentNotes)
                        if (_state.value.selectedNote?.id == note.id) {
                            _state.value = _state.value.copy(selectedNote = updated)
                        }
                    }
                } catch (_: Exception) {}
            }
            // If still transcribing, poll again after delay
            val stillTranscribing = _state.value.notes.any { it.transcriptionStatus == "transcribing" }
            if (stillTranscribing) {
                delay(10_000)
                loadNotes()
            }
        }
    }

    fun selectNote(note: MeetingNote) {
        _state.value = _state.value.copy(selectedNote = note)
        viewModelScope.launch {
            try {
                // Fetch full note with photos
                val full = repository.getMeetingNote(note.id)
                _state.value = _state.value.copy(selectedNote = full)
                // Check transcription if still processing
                if (full.transcriptionStatus == "transcribing") {
                    val updated = repository.checkTranscription(note.id)
                    val photos = try { repository.getPhotos(note.id) } catch (_: Exception) { full.photos }
                    _state.value = _state.value.copy(selectedNote = updated.copy(photos = photos))
                }
            } catch (_: Exception) {}
        }
    }

    fun clearSelectedNote() {
        _state.value = _state.value.copy(selectedNote = null)
    }

    fun updateTitle(title: String) {
        _state.value = _state.value.copy(meetingTitle = title)
    }

    fun startRecording() {
        val context = getApplication<Application>()
        val dir = File(context.filesDir, "meetings")
        dir.mkdirs()
        val file = File(dir, "meeting_${System.currentTimeMillis()}.m4a")
        recordingFile = file

        // Start foreground service
        val intent = Intent(context, AudioRecorderService::class.java)
        context.startForegroundService(intent)

        viewModelScope.launch {
            // Wait for service to bind
            delay(500)
            val success = recorderService?.startRecording(file) ?: false
            if (success) {
                _state.value = _state.value.copy(isRecording = true, isPaused = false, recordingDurationSecs = 0)
                startTimer()
            } else {
                _state.value = _state.value.copy(error = "Failed to start recording")
            }
        }
    }

    fun pauseRecording() {
        recorderService?.pauseRecording()
        _state.value = _state.value.copy(isPaused = true)
        timerRunning = false
    }

    fun resumeRecording() {
        recorderService?.resumeRecording()
        _state.value = _state.value.copy(isPaused = false)
        startTimer()
    }

    fun stopRecording() {
        timerRunning = false
        val audioFile = recorderService?.stopRecording()
        val duration = _state.value.recordingDurationSecs
        val title = _state.value.meetingTitle.ifBlank { null }

        _state.value = _state.value.copy(isRecording = false, isPaused = false)

        viewModelScope.launch {
            try {
                val note = repository.createMeetingNote(title, duration, audioFile?.absolutePath)
                if (audioFile != null && audioFile.exists()) {
                    enqueueUpload(note.id, audioFile.absolutePath)
                }
                _state.value = _state.value.copy(meetingTitle = "", recordingDurationSecs = 0)
                loadNotes()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to save: ${e.message}")
            }
        }
    }

    fun deleteMeetingNote(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteMeetingNote(id)
                _state.value = _state.value.copy(selectedNote = null)
                loadNotes()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun refreshTranscription(id: String) {
        viewModelScope.launch {
            try {
                val updated = repository.checkTranscription(id)
                _state.value = _state.value.copy(selectedNote = updated)
                loadNotes()
            } catch (_: Exception) {}
        }
    }

    fun uploadPhoto(meetingId: String, photoFile: File) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploadingPhoto = true)
            try {
                val photo = repository.uploadPhoto(meetingId, photoFile)
                val current = _state.value.selectedNote
                if (current != null && current.id == meetingId) {
                    _state.value = _state.value.copy(
                        selectedNote = current.copy(photos = current.photos + photo),
                        isUploadingPhoto = false,
                    )
                } else {
                    _state.value = _state.value.copy(isUploadingPhoto = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isUploadingPhoto = false,
                    error = "Photo upload failed: ${e.message}",
                )
            }
        }
    }

    fun deletePhoto(meetingId: String, photoId: String) {
        viewModelScope.launch {
            try {
                repository.deletePhoto(meetingId, photoId)
                val current = _state.value.selectedNote
                if (current != null && current.id == meetingId) {
                    _state.value = _state.value.copy(
                        selectedNote = current.copy(photos = current.photos.filter { it.id != photoId })
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Delete failed: ${e.message}")
            }
        }
    }

    private fun startTimer() {
        timerRunning = true
        viewModelScope.launch {
            while (timerRunning) {
                delay(1000)
                if (timerRunning) {
                    _state.value = _state.value.copy(
                        recordingDurationSecs = _state.value.recordingDurationSecs + 1
                    )
                }
            }
        }
    }

    private fun enqueueUpload(noteId: String, audioPath: String) {
        val context = getApplication<Application>()
        val data = workDataOf("note_id" to noteId, "audio_path" to audioPath)
        val request = OneTimeWorkRequestBuilder<MeetingUploadWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    override fun onCleared() {
        timerRunning = false
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (_: Exception) {}
        super.onCleared()
    }
}
