package com.lilyai.app.ui.screens.lily

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilyai.app.ui.screens.login.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class LilyState(
    val isConnected: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val transcript: String = "",
    val responseText: String = "",
    val error: String? = null,
)

@HiltViewModel
class LilyViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(LilyState())
    val state = _state.asStateFlow()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var isRecording = false

    // Single thread for all AudioTrack operations to avoid native thread-safety crashes
    private val audioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "lily-audio-playback").apply { isDaemon = true }
    }
    private val audioDispatcher = audioExecutor.asCoroutineDispatcher()
    private val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)

    private val sampleRate = 24000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun connect() {
        if (webSocket != null) disconnect()

        val token = TokenStore.getIdToken() ?: run {
            _state.value = _state.value.copy(error = "Not authenticated")
            return
        }

        val wsUrl = "${getWsUrl()}/api/lily/ws"

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        // Start playback consumer on the dedicated audio thread
        startPlaybackConsumer()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = _state.value.copy(isConnected = true, error = null)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                audioChannel.trySend(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                _state.value = _state.value.copy(isConnected = false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = _state.value.copy(
                    isConnected = false,
                    error = "Connection failed: ${t.message}"
                )
            }
        })
    }

    private fun startPlaybackConsumer() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch(audioDispatcher) {
            // Create AudioTrack on this thread
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat
            )
            val track = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(bufferSize * 4, 16384))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()

            try {
                // Consume audio data on the same thread that created the track
                for (data in audioChannel) {
                    if (track.state == AudioTrack.STATE_INITIALIZED &&
                        track.playState == AudioTrack.PLAYSTATE_PLAYING
                    ) {
                        track.write(data, 0, data.size)
                    }
                }
            } finally {
                track.stop()
                track.release()
            }
        }
    }

    fun disconnect() {
        stopListening()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        playbackJob?.cancel()
        playbackJob = null
        // Drain any remaining audio
        while (audioChannel.tryReceive().isSuccess) {}
        _state.value = LilyState()
    }

    fun startListening() {
        if (!_state.value.isConnected) return
        isRecording = true
        _state.value = _state.value.copy(isListening = true, responseText = "", transcript = "")

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelConfig, audioFormat, bufferSize * 2
        )

        audioRecord?.startRecording()

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    webSocket?.send(ByteString.of(*buffer.copyOf(read)))
                }
            }
        }
    }

    fun stopListening() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _state.value = _state.value.copy(isListening = false)

        val commitEvent = JSONObject().apply {
            put("type", "input_audio_buffer.commit")
        }
        webSocket?.send(commitEvent.toString())
    }

    private fun handleTextMessage(text: String) {
        try {
            val event = JSONObject(text)
            when (event.optString("type")) {
                "response.audio_transcript.delta",
                "response.output_audio_transcript.delta" -> {
                    val delta = event.optString("delta", "")
                    _state.value = _state.value.copy(
                        responseText = _state.value.responseText + delta,
                        isSpeaking = true,
                    )
                }
                "response.audio_transcript.done",
                "response.output_audio_transcript.done" -> {
                    _state.value = _state.value.copy(isSpeaking = false)
                }
                "input_audio_buffer.speech_started" -> {
                    _state.value = _state.value.copy(isListening = true)
                }
                "input_audio_buffer.speech_stopped" -> {}
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = event.optString("transcript", "")
                    _state.value = _state.value.copy(transcript = transcript)
                }
                "error" -> {
                    val err = event.optJSONObject("error")
                    _state.value = _state.value.copy(
                        error = err?.optString("message") ?: "Unknown error"
                    )
                }
                "response.done" -> {
                    _state.value = _state.value.copy(isSpeaking = false)
                }
            }
        } catch (_: Exception) {}
    }

    private fun getWsUrl(): String {
        return "ws://budget-tracker-alb-652156223.ap-south-1.elb.amazonaws.com"
    }

    override fun onCleared() {
        disconnect()
        audioDispatcher.close()
        super.onCleared()
    }
}
