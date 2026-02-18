package com.lilyai.app.vadsensor

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 10-channel environmental sensor vector for emotional VAD computation.
 * Each field is normalised to [0.0, 1.0].
 *
 * Channel mapping:
 *   0 battery_low   - Battery depleted (0=full, 1=critical)
 *   1 people_count  - Normalised nearby people count
 *   2 known_face    - Recognised face confidence
 *   3 unknown_face  - Unfamiliar face confidence
 *   4 fall_event    - Fall / impact intensity
 *   5 lifted        - Robot grabbed / lifted
 *   6 idle_time     - Time since last activity (0→1)
 *   7 sound_energy  - Ambient sound level
 *   8 voice_rate    - Speech cadence (conversation proxy)
 *   9 motion_energy - IMU / accelerometer motion energy
 */
data class SensorVector(
    val batteryLow: Float = 0f,
    val peopleCount: Float = 0f,
    val knownFace: Float = 0f,
    val unknownFace: Float = 0f,
    val fallEvent: Float = 0f,
    val lifted: Float = 0f,
    val idleTime: Float = 0f,
    val soundEnergy: Float = 0f,
    val voiceRate: Float = 0f,
    val motionEnergy: Float = 0f
) {
    /** Serialize to 40 bytes (10 × f32 LE) */
    fun toBytes(): ByteArray {
        val buf = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(batteryLow)
        buf.putFloat(peopleCount)
        buf.putFloat(knownFace)
        buf.putFloat(unknownFace)
        buf.putFloat(fallEvent)
        buf.putFloat(lifted)
        buf.putFloat(idleTime)
        buf.putFloat(soundEnergy)
        buf.putFloat(voiceRate)
        buf.putFloat(motionEnergy)
        return buf.array()
    }

    fun toArray(): FloatArray = floatArrayOf(
        batteryLow, peopleCount, knownFace, unknownFace, fallEvent,
        lifted, idleTime, soundEnergy, voiceRate, motionEnergy
    )
}

/**
 * Binary sensor packet matching the VAD-Sensor-Bridge wire format.
 *
 * Wire format (32-byte header + variable payload):
 *   [sensor_id: u32 LE][timestamp_us: u64 LE][data_type: u8][reserved: 3]
 *   [payload_len: u16 LE][reserved: 2][seq: u64 LE][padding: 4]
 *   [payload: N bytes]
 */
data class SensorPacket(
    val sensorId: Long = 1,
    val timestampUs: Long = System.nanoTime() / 1000,
    val dataType: Int = DATA_TYPE_SENSOR_VECTOR,
    val seq: Long = 0,
    val payload: ByteArray = ByteArray(0)
) {
    companion object {
        const val HEADER_SIZE = 32
        const val DATA_TYPE_AUDIO = 1
        const val DATA_TYPE_SENSOR_VECTOR = 2
    }

    fun toBytes(): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)

        // Offset 0: sensor_id (u32 LE)
        buf.putInt(sensorId.toInt())
        // Offset 4: timestamp_us (u64 LE)
        buf.putLong(timestampUs)
        // Offset 12: data_type (u8)
        buf.put(dataType.toByte())
        // Offset 13: reserved (3 bytes)
        buf.put(0); buf.put(0); buf.put(0)
        // Offset 16: payload_len (u16 LE)
        buf.putShort(payload.size.toShort())
        // Offset 18: reserved (2 bytes)
        buf.put(0); buf.put(0)
        // Offset 20: seq (u64 LE)
        buf.putLong(seq)
        // Offset 28: padding (4 bytes)
        buf.putInt(0)
        // Offset 32: payload
        buf.put(payload)

        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorPacket) return false
        return sensorId == other.sensorId && seq == other.seq &&
                dataType == other.dataType && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = sensorId.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + dataType
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * VAD response packet received from the server (34 bytes).
 *
 * Wire format:
 *   [sensor_id: u32 LE][seq: u64 LE][is_active: u8][kind: u8]
 *   [energy: f32 LE][threshold: f32 LE]
 *   [valence: f32 LE][arousal: f32 LE][dominance: f32 LE]
 */
data class VadResponse(
    val sensorId: Long = 0,
    val seq: Long = 0,
    val isActive: Boolean = false,
    val kind: VadKind = VadKind.EMOTIONAL,
    val energy: Float = 0f,
    val threshold: Float = 0f,
    val valence: Float = 0f,
    val arousal: Float = 0f,
    val dominance: Float = 0f
) {
    companion object {
        const val PACKET_SIZE = 34

        fun fromBytes(data: ByteArray): VadResponse? {
            if (data.size < PACKET_SIZE) return null
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            return VadResponse(
                sensorId = buf.int.toLong() and 0xFFFFFFFFL,
                seq = buf.long,
                isActive = buf.get().toInt() != 0,
                kind = if (buf.get().toInt() == 1) VadKind.AUDIO else VadKind.EMOTIONAL,
                energy = buf.float,
                threshold = buf.float,
                valence = buf.float,
                arousal = buf.float,
                dominance = buf.float
            )
        }
    }
}

enum class VadKind(val value: Int) {
    AUDIO(1),
    EMOTIONAL(2)
}
