package com.lilyai.app.vadsensor.face

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * CameraX ImageAnalysis.Analyzer that:
 *  1. Detects faces via ML Kit (on-device)
 *  2. Extracts a 15-dim feature vector from inter-landmark distances
 *  3. Matches features against [KnownFaceStore]
 *  4. Exposes live results via [result] StateFlow
 *
 * Also supports one-shot capture for face registration via [requestCapture].
 */
class FaceProcessor(
    private val knownFaceStore: KnownFaceStore
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "FaceProcessor"
        private const val MATCH_THRESHOLD = 0.20f
        private const val MIN_LANDMARKS = 5
        const val FEATURE_SIZE = 15

        /** Pairs of landmarks whose Euclidean distance forms one feature dimension. */
        private val LANDMARK_PAIRS = listOf(
            FaceLandmark.LEFT_EYE to FaceLandmark.RIGHT_EYE,
            FaceLandmark.LEFT_EYE to FaceLandmark.NOSE_BASE,
            FaceLandmark.RIGHT_EYE to FaceLandmark.NOSE_BASE,
            FaceLandmark.NOSE_BASE to FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.MOUTH_LEFT to FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.LEFT_EYE to FaceLandmark.LEFT_EAR,
            FaceLandmark.RIGHT_EYE to FaceLandmark.RIGHT_EAR,
            FaceLandmark.LEFT_CHEEK to FaceLandmark.RIGHT_CHEEK,
            FaceLandmark.LEFT_EYE to FaceLandmark.MOUTH_LEFT,
            FaceLandmark.RIGHT_EYE to FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.LEFT_CHEEK to FaceLandmark.NOSE_BASE,
            FaceLandmark.RIGHT_CHEEK to FaceLandmark.NOSE_BASE,
            FaceLandmark.LEFT_EYE to FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.RIGHT_EYE to FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.NOSE_BASE to FaceLandmark.LEFT_CHEEK,
        )
    }

    // ── ML Kit detector ──────────────────────────────────────────────
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    // ── Public result flow ───────────────────────────────────────────
    data class FaceResult(
        val peopleCount: Int = 0,
        val knownFaceConfidence: Float = 0f,
        val unknownFaceConfidence: Float = 0f,
        val faces: List<FaceInfo> = emptyList(),
        val bestMatchName: String? = null,
        val imageWidth: Int = 0,
        val imageHeight: Int = 0
    )

    data class FaceInfo(
        val bounds: android.graphics.RectF,
        val isKnown: Boolean,
        val name: String?,
        val confidence: Float,
        val trackingId: Int?
    )

    private val _result = MutableStateFlow(FaceResult())
    val result: StateFlow<FaceResult> = _result.asStateFlow()

    // ── One-shot capture for registration ────────────────────────────
    @Volatile
    private var captureCallback: ((FloatArray) -> Unit)? = null

    fun requestCapture(callback: (FloatArray) -> Unit) {
        captureCallback = callback
    }

    // ── Analyzer implementation ──────────────────────────────────────
    @Volatile
    private var isProcessing = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val imgW = image.width
        val imgH = image.height

        detector.process(image)
            .addOnSuccessListener { faces -> processFaces(faces, imgW, imgH) }
            .addOnFailureListener { e -> Log.e(TAG, "Detection failed: ${e.message}") }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    // ── Internal helpers ─────────────────────────────────────────────

    private fun processFaces(faces: List<Face>, imgW: Int, imgH: Int) {
        if (faces.isEmpty()) {
            _result.value = FaceResult(imageWidth = imgW, imageHeight = imgH)
            return
        }

        var bestKnownConfidence = 0f
        var bestMatchName: String? = null
        val faceInfoList = mutableListOf<FaceInfo>()

        for (face in faces) {
            val features = extractFeatures(face)

            // Handle one-shot capture (first valid face)
            if (features != null) {
                captureCallback?.let { cb ->
                    cb(features)
                    captureCallback = null
                }
            }

            var isKnown = false
            var matchName: String? = null
            var matchConfidence = 0f

            if (features != null) {
                val match = knownFaceStore.findBestMatch(features, MATCH_THRESHOLD)
                if (match != null) {
                    isKnown = true
                    matchName = match.first.name
                    matchConfidence = (1f - match.second / MATCH_THRESHOLD).coerceIn(0f, 1f)
                    if (matchConfidence > bestKnownConfidence) {
                        bestKnownConfidence = matchConfidence
                        bestMatchName = matchName
                    }
                }
            }

            val box = face.boundingBox
            faceInfoList.add(
                FaceInfo(
                    bounds = android.graphics.RectF(
                        box.left.toFloat(), box.top.toFloat(),
                        box.right.toFloat(), box.bottom.toFloat()
                    ),
                    isKnown = isKnown,
                    name = matchName,
                    confidence = matchConfidence,
                    trackingId = face.trackingId
                )
            )
        }

        val unknownCount = faceInfoList.count { !it.isKnown }
        val unknownConfidence = if (unknownCount > 0 && faceInfoList.isNotEmpty())
            unknownCount.toFloat() / faceInfoList.size else 0f

        _result.value = FaceResult(
            peopleCount = faces.size,
            knownFaceConfidence = bestKnownConfidence,
            unknownFaceConfidence = unknownConfidence,
            faces = faceInfoList,
            bestMatchName = bestMatchName,
            imageWidth = imgW,
            imageHeight = imgH
        )
    }

    /**
     * Build a 15-dimension feature vector from inter-landmark distances,
     * each normalised by the face bounding-box diagonal so it is scale-invariant.
     * Returns null when too few landmarks are detected.
     */
    fun extractFeatures(face: Face): FloatArray? {
        val box = face.boundingBox
        val diagonal = sqrt(
            box.width().toFloat() * box.width().toFloat() +
                    box.height().toFloat() * box.height().toFloat()
        )
        if (diagonal < 1f) return null

        val features = FloatArray(FEATURE_SIZE)
        var validCount = 0

        for ((idx, pair) in LANDMARK_PAIRS.withIndex()) {
            val lm1 = face.getLandmark(pair.first)
            val lm2 = face.getLandmark(pair.second)
            if (lm1 != null && lm2 != null) {
                val dx = lm1.position.x - lm2.position.x
                val dy = lm1.position.y - lm2.position.y
                features[idx] = sqrt(dx * dx + dy * dy) / diagonal
                validCount++
            }
        }

        return if (validCount >= MIN_LANDMARKS) features else null
    }

    fun close() {
        detector.close()
    }
}
