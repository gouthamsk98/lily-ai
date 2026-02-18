package com.lilyai.app.vadsensor.face

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FaceRegistrationViewModel @Inject constructor(
    application: Application,
    private val knownFaceStore: KnownFaceStore
) : AndroidViewModel(application) {

    companion object {
        private const val CAPTURE_FRAMES = 5
    }

    val faceProcessor = FaceProcessor(knownFaceStore)
    val faceResult: StateFlow<FaceProcessor.FaceResult> = faceProcessor.result
    val knownFaces: StateFlow<List<KnownFace>> = knownFaceStore.faces

    // ── Registration state machine ───────────────────────────────────
    sealed class RegState {
        data object Idle : RegState()
        data class Capturing(val captured: Int, val total: Int) : RegState()
        data class NamingFace(val features: FloatArray) : RegState() {
            override fun equals(other: Any?) = this === other
            override fun hashCode() = features.contentHashCode()
        }
        data object Saved : RegState()
        data class Error(val message: String) : RegState()
    }

    private val _regState = MutableStateFlow<RegState>(RegState.Idle)
    val regState: StateFlow<RegState> = _regState.asStateFlow()

    private val capturedFeatures = mutableListOf<FloatArray>()

    /** Begin multi-frame capture. Must have exactly 1 face in view. */
    fun startCapture() {
        val currentFaces = faceProcessor.result.value
        if (currentFaces.peopleCount != 1) {
            _regState.value = RegState.Error("Position exactly 1 face in the camera")
            viewModelScope.launch { delay(2000); _regState.value = RegState.Idle }
            return
        }
        capturedFeatures.clear()
        _regState.value = RegState.Capturing(0, CAPTURE_FRAMES)
        captureNextFrame()
    }

    private fun captureNextFrame() {
        faceProcessor.requestCapture { features ->
            capturedFeatures.add(features)
            val count = capturedFeatures.size

            if (count >= CAPTURE_FRAMES) {
                val averaged = averageFeatures(capturedFeatures)
                _regState.value = RegState.NamingFace(averaged)
            } else {
                _regState.value = RegState.Capturing(count, CAPTURE_FRAMES)
                viewModelScope.launch {
                    delay(250) // wait a bit between captures
                    captureNextFrame()
                }
            }
        }
    }

    /** Save the face with a user-provided name. */
    fun saveFace(name: String, features: FloatArray) {
        if (name.isBlank()) return
        knownFaceStore.addFace(
            KnownFace(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                features = features
            )
        )
        _regState.value = RegState.Saved
        viewModelScope.launch {
            delay(1500)
            _regState.value = RegState.Idle
        }
    }

    fun cancelRegistration() {
        capturedFeatures.clear()
        _regState.value = RegState.Idle
    }

    fun deleteFace(id: String) {
        knownFaceStore.removeFace(id)
    }

    override fun onCleared() {
        super.onCleared()
        faceProcessor.close()
    }

    private fun averageFeatures(list: List<FloatArray>): FloatArray {
        if (list.isEmpty()) return FloatArray(FaceProcessor.FEATURE_SIZE)
        val size = list.first().size
        val avg = FloatArray(size)
        for (f in list) {
            for (i in f.indices) avg[i] += f[i]
        }
        val n = list.size.toFloat()
        for (i in avg.indices) avg[i] /= n
        return avg
    }
}
