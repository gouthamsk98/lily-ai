package com.lilyai.app.vadsensor

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

/**
 * UDP client that speaks the VAD-Sensor-Bridge binary protocol.
 *
 * - Sends sensor vector packets (data_type=2) to the server on the sensor port (default 9002)
 * - Listens for VadResponse packets (34 bytes) back from the server
 */
class VadBridgeClient {

    companion object {
        private const val TAG = "VadBridgeClient"
        private const val DEFAULT_HOST = "100.31.140.248"
        private const val DEFAULT_PORT = 9002
        private const val SENSOR_ID = 1L
        private const val RECEIVE_TIMEOUT_MS = 5000
        private const val RECEIVE_BUFFER_SIZE = 256
    }

    private var socket: DatagramSocket? = null
    private var serverAddress: InetAddress? = null
    private var serverPort = DEFAULT_PORT
    private var receiveJob: Job? = null
    private val seqCounter = AtomicLong(0)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastResponse = MutableStateFlow<VadResponse?>(null)
    val lastResponse: StateFlow<VadResponse?> = _lastResponse.asStateFlow()

    private val _stats = MutableStateFlow(BridgeStats())
    val stats: StateFlow<BridgeStats> = _stats.asStateFlow()

    data class BridgeStats(
        val packetsSent: Long = 0,
        val packetsReceived: Long = 0,
        val errors: Long = 0,
        val lastSendTimeMs: Long = 0,
        val lastRecvTimeMs: Long = 0
    )

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    /**
     * Connect to the VAD bridge server.
     */
    fun connect(host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT, scope: CoroutineScope) {
        _connectionState.value = ConnectionState.CONNECTING
        serverPort = port

        scope.launch(Dispatchers.IO) {
            try {
                serverAddress = InetAddress.getByName(host)
                socket = DatagramSocket().apply {
                    soTimeout = RECEIVE_TIMEOUT_MS
                }
                _connectionState.value = ConnectionState.CONNECTED
                Log.i(TAG, "Connected to $host:$port")

                // Start receive loop
                startReceiveLoop(scope)
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
                _stats.value = _stats.value.copy(errors = _stats.value.errors + 1)
            }
        }
    }

    /**
     * Disconnect and clean up.
     */
    fun disconnect() {
        receiveJob?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
        serverAddress = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "Disconnected")
    }

    /**
     * Send a sensor vector to the server as a binary packet.
     */
    suspend fun sendSensorVector(vector: SensorVector) {
        val sock = socket ?: return
        val addr = serverAddress ?: return
        if (_connectionState.value != ConnectionState.CONNECTED) return

        withContext(Dispatchers.IO) {
            try {
                val payload = vector.toBytes()
                val packet = SensorPacket(
                    sensorId = SENSOR_ID,
                    timestampUs = System.nanoTime() / 1000,
                    dataType = SensorPacket.DATA_TYPE_SENSOR_VECTOR,
                    seq = seqCounter.getAndIncrement(),
                    payload = payload
                )

                val bytes = packet.toBytes()
                val dgram = DatagramPacket(bytes, bytes.size, addr, serverPort)
                sock.send(dgram)

                _stats.value = _stats.value.copy(
                    packetsSent = _stats.value.packetsSent + 1,
                    lastSendTimeMs = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
                _stats.value = _stats.value.copy(errors = _stats.value.errors + 1)
            }
        }
    }

    private fun startReceiveLoop(scope: CoroutineScope) {
        receiveJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
            val dgram = DatagramPacket(buffer, buffer.size)

            while (isActive && socket != null) {
                try {
                    socket?.receive(dgram)
                    val data = buffer.copyOf(dgram.length)

                    val response = VadResponse.fromBytes(data)
                    if (response != null) {
                        _lastResponse.value = response
                        _stats.value = _stats.value.copy(
                            packetsReceived = _stats.value.packetsReceived + 1,
                            lastRecvTimeMs = System.currentTimeMillis()
                        )
                    } else {
                        Log.w(TAG, "Invalid response packet (${dgram.length} bytes)")
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Normal timeout, just continue
                } catch (e: java.net.SocketException) {
                    if (isActive) {
                        Log.e(TAG, "Socket error in receive: ${e.message}")
                        _connectionState.value = ConnectionState.ERROR
                    }
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Receive error: ${e.message}")
                    _stats.value = _stats.value.copy(errors = _stats.value.errors + 1)
                }
            }
        }
    }
}
