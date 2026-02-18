package com.lilyai.app.vadsensor.face

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.sqrt

/**
 * Locally persists known face feature vectors as JSON.
 * Provides matching via Euclidean distance on normalised landmark-distance features.
 */
class KnownFaceStore(private val context: Context) {

    companion object {
        private const val TAG = "KnownFaceStore"
        private const val FILE_NAME = "known_faces.json"

        fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return Float.MAX_VALUE
            var sum = 0f
            for (i in a.indices) {
                val d = a[i] - b[i]
                sum += d * d
            }
            return sqrt(sum)
        }
    }

    private val gson = Gson()
    private val file = File(context.filesDir, FILE_NAME)

    private val _faces = MutableStateFlow<List<KnownFace>>(emptyList())
    val faces: StateFlow<List<KnownFace>> = _faces.asStateFlow()

    init {
        load()
    }

    fun addFace(face: KnownFace) {
        _faces.value = _faces.value + face
        save()
        Log.i(TAG, "Saved face '${face.name}' (${face.features.size} features)")
    }

    fun removeFace(id: String) {
        val name = _faces.value.find { it.id == id }?.name
        _faces.value = _faces.value.filter { it.id != id }
        save()
        Log.i(TAG, "Removed face '$name'")
    }

    /**
     * Find the closest known face within the given Euclidean distance threshold.
     * Returns (KnownFace, distance) or null if no match.
     */
    fun findBestMatch(features: FloatArray, threshold: Float = 0.20f): Pair<KnownFace, Float>? {
        var bestMatch: KnownFace? = null
        var bestDistance = Float.MAX_VALUE

        for (face in _faces.value) {
            val dist = euclideanDistance(features, face.features)
            if (dist < bestDistance) {
                bestDistance = dist
                bestMatch = face
            }
        }

        return if (bestMatch != null && bestDistance < threshold) {
            Pair(bestMatch!!, bestDistance)
        } else null
    }

    private fun save() {
        try {
            val data = _faces.value.map {
                KnownFaceData(it.id, it.name, it.features.toList(), it.capturedAt)
            }
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save: ${e.message}")
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<List<KnownFaceData>>() {}.type
            val data: List<KnownFaceData> = gson.fromJson(file.readText(), type) ?: return
            _faces.value = data.map {
                KnownFace(it.id, it.name, it.features.toFloatArray(), it.capturedAt)
            }
            Log.i(TAG, "Loaded ${_faces.value.size} known faces")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load: ${e.message}")
        }
    }
}

data class KnownFace(
    val id: String,
    val name: String,
    val features: FloatArray,
    val capturedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnownFace) return false
        return id == other.id
    }

    override fun hashCode() = id.hashCode()
}

/** Gson-friendly serialisation wrapper (List<Float> instead of FloatArray). */
data class KnownFaceData(
    val id: String,
    val name: String,
    val features: List<Float>,
    val capturedAt: Long
)
