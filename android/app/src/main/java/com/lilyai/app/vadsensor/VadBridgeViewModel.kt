package com.lilyai.app.vadsensor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lilyai.app.vadsensor.face.FaceProcessor
import com.lilyai.app.vadsensor.face.KnownFaceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class VadBridgeViewModel @Inject constructor(
    application: Application,
    knownFaceStore: KnownFaceStore
) : AndroidViewModel(application) {

    private val sensorCollector = SensorCollector(application.applicationContext)
    private val bridgeClient = VadBridgeClient()

    // Face detection (on-device, edge)
    val faceProcessor = FaceProcessor(knownFaceStore)
    val faceResult: StateFlow<FaceProcessor.FaceResult> = faceProcessor.result

    private val _faceDetectionEnabled = MutableStateFlow(false)
    val faceDetectionEnabled: StateFlow<Boolean> = _faceDetectionEnabled.asStateFlow()

    fun toggleFaceDetection() {
        _faceDetectionEnabled.value = !_faceDetectionEnabled.value
    }

    // Server config
    private val _serverHost = MutableStateFlow("100.31.140.248")
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    private val _serverPort = MutableStateFlow(9002)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _sendRateHz = MutableStateFlow(10) // packets per second
    val sendRateHz: StateFlow<Int> = _sendRateHz.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    // Exposed sensor data — the final merged vector (sensors + face)
    private val _mergedVector = MutableStateFlow(SensorVector())
    val sensorVector: StateFlow<SensorVector> = _mergedVector.asStateFlow()
    val rawReadings: StateFlow<SensorCollector.RawSensorReadings> = sensorCollector.rawReadings

    // Bridge state
    val connectionState: StateFlow<VadBridgeClient.ConnectionState> = bridgeClient.connectionState
    val lastVadResponse: StateFlow<VadResponse?> = bridgeClient.lastResponse
    val bridgeStats: StateFlow<VadBridgeClient.BridgeStats> = bridgeClient.stats

    private var streamingJob: Job? = null

    fun updateServerHost(host: String) {
        _serverHost.value = host
    }

    fun updateServerPort(port: Int) {
        _serverPort.value = port
    }

    fun updateSendRate(hz: Int) {
        _sendRateHz.value = hz.coerceIn(1, 50)
    }

    /**
     * Merge hardware-sensor vector with face-detection results.
     */
    private fun buildMergedVector(): SensorVector {
        val base = sensorCollector.sensorVector.value
        if (!_faceDetectionEnabled.value) return base

        val face = faceProcessor.result.value
        return base.copy(
            peopleCount = (face.peopleCount.toFloat() / 5f).coerceIn(0f, 1f),
            knownFace = face.knownFaceConfidence,
            unknownFace = face.unknownFaceConfidence
        )
    }

    /**
     * Start sensor collection and UDP streaming.
     */
    fun startStreaming() {
        if (_isStreaming.value) return

        // Start sensor collection
        sensorCollector.start(viewModelScope)

        // Connect to server
        bridgeClient.connect(
            host = _serverHost.value,
            port = _serverPort.value,
            scope = viewModelScope
        )

        // Start sending loop
        streamingJob = viewModelScope.launch {
            // Wait for connection
            bridgeClient.connectionState.first {
                it == VadBridgeClient.ConnectionState.CONNECTED ||
                        it == VadBridgeClient.ConnectionState.ERROR
            }

            if (bridgeClient.connectionState.value == VadBridgeClient.ConnectionState.CONNECTED) {
                _isStreaming.value = true

                while (isActive && _isStreaming.value) {
                    val vector = buildMergedVector()
                    _mergedVector.value = vector
                    bridgeClient.sendSensorVector(vector)
                    delay(1000L / _sendRateHz.value)
                }
            }
        }
    }

    /**
     * Stop streaming and disconnect.
     */
    fun stopStreaming() {
        _isStreaming.value = false
        streamingJob?.cancel()
        sensorCollector.stop()
        bridgeClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
        faceProcessor.close()
    }
}
