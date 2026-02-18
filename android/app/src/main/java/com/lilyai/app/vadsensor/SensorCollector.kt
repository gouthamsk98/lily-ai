package com.lilyai.app.vadsensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Reads real Android sensors and maps them to the 10-channel SensorVector
 * used by the VAD-Sensor-Bridge protocol.
 *
 * Sensor mapping:
 *   0 battery_low   → BatteryManager (inverted: 0=full, 1=critical)
 *   1 people_count  → 0.0 (no camera analysis)
 *   2 known_face    → 0.0 (no face recognition)
 *   3 unknown_face  → 0.0 (no face recognition)
 *   4 fall_event    → Accelerometer sudden spike detection
 *   5 lifted        → Accelerometer Z-axis deviation from gravity
 *   6 idle_time     → Time since last significant motion (0→1)
 *   7 sound_energy  → Microphone RMS amplitude (normalised)
 *   8 voice_rate    → 0.0 (simplified)
 *   9 motion_energy → Accelerometer magnitude (normalised)
 */
class SensorCollector(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "SensorCollector"
        private const val GRAVITY = 9.81f
        private const val FALL_THRESHOLD = 25f  // m/s² — sudden acceleration spike
        private const val LIFTED_Z_THRESHOLD = 2f  // deviation from gravity
        private const val IDLE_MAX_SECONDS = 30f  // 30s of no motion → idle_time=1.0
        private const val MOTION_MAX = 20f  // normalise motion energy to this ceiling
        private const val AUDIO_SAMPLE_RATE = 16000
        private const val AUDIO_BUFFER_SIZE = 1600  // 100ms at 16kHz
        private const val SOUND_MAX_RMS = 5000f  // normalisation ceiling for mic RMS
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    // Raw sensor readings
    private var accelX = 0f; private var accelY = 0f; private var accelZ = GRAVITY
    private var gyroX = 0f; private var gyroY = 0f; private var gyroZ = 0f
    private var light = 0f
    private var proximity = 0f

    // Derived values
    private var lastMotionTime = System.currentTimeMillis()
    private var fallIntensity = 0f
    private var soundRms = 0f

    // Audio recording
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null
    private var isAudioRecording = false

    // Exposed state
    private val _sensorVector = MutableStateFlow(SensorVector())
    val sensorVector: StateFlow<SensorVector> = _sensorVector.asStateFlow()

    private val _rawReadings = MutableStateFlow(RawSensorReadings())
    val rawReadings: StateFlow<RawSensorReadings> = _rawReadings.asStateFlow()

    private var updateJob: Job? = null

    data class RawSensorReadings(
        val accelX: Float = 0f,
        val accelY: Float = 0f,
        val accelZ: Float = 0f,
        val gyroX: Float = 0f,
        val gyroY: Float = 0f,
        val gyroZ: Float = 0f,
        val light: Float = 0f,
        val proximity: Float = 0f,
        val batteryPct: Float = 100f,
        val soundRms: Float = 0f,
        val motionMagnitude: Float = 0f
    )

    /**
     * Start listening to sensors and updating the vector.
     */
    fun start(scope: CoroutineScope) {
        // Register hardware sensors
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val proxSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        accel?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        proxSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Start microphone reading
        startAudioCapture(scope)

        // Periodic update of the sensor vector (20 Hz)
        updateJob = scope.launch {
            while (isActive) {
                updateSensorVector()
                delay(50) // 20 Hz
            }
        }

        Log.i(TAG, "Sensor collection started")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        stopAudioCapture()
        updateJob?.cancel()
        Log.i(TAG, "Sensor collection stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]

                // Check for fall event (sudden acceleration spike)
                val magnitude = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
                if (magnitude > FALL_THRESHOLD) {
                    fallIntensity = ((magnitude - FALL_THRESHOLD) / FALL_THRESHOLD).coerceIn(0f, 1f)
                } else {
                    // Decay fall intensity
                    fallIntensity = (fallIntensity * 0.95f).coerceIn(0f, 1f)
                }

                // Track last motion time
                if (abs(magnitude - GRAVITY) > 1.5f) {
                    lastMotionTime = System.currentTimeMillis()
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
            }
            Sensor.TYPE_LIGHT -> {
                light = event.values[0]
            }
            Sensor.TYPE_PROXIMITY -> {
                proximity = event.values[0]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startAudioCapture(scope: CoroutineScope) {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(AUDIO_BUFFER_SIZE * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isAudioRecording = true

                audioJob = scope.launch(Dispatchers.IO) {
                    val buffer = ShortArray(AUDIO_BUFFER_SIZE)
                    while (isActive && isAudioRecording) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            // Compute RMS
                            var sum = 0.0
                            for (i in 0 until read) {
                                val s = buffer[i].toDouble()
                                sum += s * s
                            }
                            soundRms = sqrt(sum / read).toFloat()
                        }
                    }
                }
                Log.i(TAG, "Audio capture started")
            } else {
                Log.w(TAG, "AudioRecord failed to initialize")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No RECORD_AUDIO permission, sound_energy will be 0")
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture error: ${e.message}")
        }
    }

    private fun stopAudioCapture() {
        isAudioRecording = false
        audioJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio: ${e.message}")
        }
        audioRecord = null
    }

    private fun updateSensorVector() {
        // 0: battery_low (0=full, 1=critical)
        val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .coerceIn(0, 100)
        val batteryLow = (1f - batteryPct / 100f).coerceIn(0f, 1f)

        // 4: fall_event
        val fallEvent = fallIntensity

        // 5: lifted — Z-axis deviates from gravity significantly
        val zDeviation = abs(accelZ - GRAVITY)
        val lifted = (zDeviation / (GRAVITY * 0.5f)).coerceIn(0f, 1f)

        // 6: idle_time — seconds since last motion, normalised to 0-1
        val secondsIdle = (System.currentTimeMillis() - lastMotionTime) / 1000f
        val idleTime = (secondsIdle / IDLE_MAX_SECONDS).coerceIn(0f, 1f)

        // 7: sound_energy — microphone RMS normalised
        val soundEnergy = (soundRms / SOUND_MAX_RMS).coerceIn(0f, 1f)

        // 9: motion_energy — accelerometer total magnitude minus gravity, normalised
        val accelMag = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
        val motionEnergy = (abs(accelMag - GRAVITY) / MOTION_MAX).coerceIn(0f, 1f)

        val vector = SensorVector(
            batteryLow = batteryLow,
            peopleCount = 0f,   // No camera
            knownFace = 0f,     // No face recognition
            unknownFace = 0f,   // No face recognition
            fallEvent = fallEvent,
            lifted = lifted,
            idleTime = idleTime,
            soundEnergy = soundEnergy,
            voiceRate = 0f,     // Simplified
            motionEnergy = motionEnergy
        )

        _sensorVector.value = vector

        _rawReadings.value = RawSensorReadings(
            accelX = accelX,
            accelY = accelY,
            accelZ = accelZ,
            gyroX = gyroX,
            gyroY = gyroY,
            gyroZ = gyroZ,
            light = light,
            proximity = proximity,
            batteryPct = batteryPct.toFloat(),
            soundRms = soundRms,
            motionMagnitude = accelMag
        )
    }
}
