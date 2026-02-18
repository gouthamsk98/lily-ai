package com.lilyai.app.vadsensor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lilyai.app.vadsensor.face.CameraFacePreview
import com.lilyai.app.vadsensor.face.FaceProcessor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VadBridgeScreen(
    onManageFaces: () -> Unit = {},
    viewModel: VadBridgeViewModel = hiltViewModel()
) {
    val sensorVector by viewModel.sensorVector.collectAsStateWithLifecycle()
    val rawReadings by viewModel.rawReadings.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val vadResponse by viewModel.lastVadResponse.collectAsStateWithLifecycle()
    val stats by viewModel.bridgeStats.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val serverHost by viewModel.serverHost.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
    val sendRate by viewModel.sendRateHz.collectAsStateWithLifecycle()
    val faceDetectionEnabled by viewModel.faceDetectionEnabled.collectAsStateWithLifecycle()
    val faceResult by viewModel.faceResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    var hostInput by remember { mutableStateOf(serverHost) }
    var portInput by remember { mutableStateOf(serverPort.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ──
        Text(
            "VAD Sensor Bridge",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // ── Connection Config ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Server Connection", fontWeight = FontWeight.SemiBold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = {
                            hostInput = it
                            viewModel.updateServerHost(it)
                        },
                        label = { Text("Host") },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        enabled = !isStreaming
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = {
                            portInput = it
                            it.toIntOrNull()?.let { p -> viewModel.updateServerPort(p) }
                        },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !isStreaming
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Send rate slider
                    Text("${sendRate} Hz", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = sendRate.toFloat(),
                        onValueChange = { viewModel.updateSendRate(it.toInt()) },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.weight(1f),
                        enabled = !isStreaming
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Connection status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val statusColor by animateColorAsState(
                            when (connectionState) {
                                VadBridgeClient.ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                VadBridgeClient.ConnectionState.CONNECTING -> Color(0xFFFFC107)
                                VadBridgeClient.ConnectionState.ERROR -> Color(0xFFF44336)
                                VadBridgeClient.ConnectionState.DISCONNECTED -> Color(0xFF9E9E9E)
                            }, label = "status"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            connectionState.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }

                    // Start/Stop button
                    Button(
                        onClick = {
                            if (isStreaming) viewModel.stopStreaming()
                            else viewModel.startStreaming()
                        },
                        colors = if (isStreaming) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Icon(
                            if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isStreaming) "Stop" else "Start")
                    }
                }
            }
        }

        // ── VAD Response (main display) ──
        VadResponseCard(vadResponse)

        // ── Face Detection ──
        FaceDetectionCard(
            faceDetectionEnabled = faceDetectionEnabled,
            faceResult = faceResult,
            hasCameraPermission = hasCameraPermission,
            faceProcessor = viewModel.faceProcessor,
            onToggle = {
                if (!hasCameraPermission) {
                    permLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    viewModel.toggleFaceDetection()
                }
            },
            onManageFaces = onManageFaces
        )

        // ── Sensor Vector (10 channels) ──
        SensorVectorCard(sensorVector)

        // ── Raw Sensor Readings ──
        RawReadingsCard(rawReadings)

        // ── Stats ──
        StatsCard(stats)
    }
}

@Composable
private fun VadResponseCard(response: VadResponse?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (response?.isActive == true)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Psychology, contentDescription = null)
                Text(
                    "VAD Response",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (response != null) {
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (response.isActive)
                            Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else
                            Color(0xFF9E9E9E).copy(alpha = 0.2f)
                    ) {
                        Text(
                            if (response.isActive) "ACTIVE" else "INACTIVE",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (response.isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                        )
                    }
                }
            }

            if (response != null) {
                // V / A / D bars
                VadBar("Valence", response.valence, Color(0xFF2196F3))
                VadBar("Arousal", response.arousal, Color(0xFFFF9800))
                VadBar("Dominance", response.dominance, Color(0xFF9C27B0))

                // Emotional state label
                val emotionalState = mapVadToEmotion(response.valence, response.arousal, response.dominance)
                Text(
                    "Emotional State: $emotionalState",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    "Kind: ${response.kind.name} | Seq: ${response.seq}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "No response yet — start streaming to receive VAD data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VadBar(label: String, value: Float, color: Color) {
    val animatedValue by animateFloatAsState(value, label = label)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "%.3f".format(value),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        LinearProgressIndicator(
            progress = { animatedValue.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun SensorVectorCard(vector: SensorVector) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Sensors, contentDescription = null)
                Text(
                    "Sensor Vector (10 ch)",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            val channels = listOf(
                "battery_low" to vector.batteryLow,
                "people_count" to vector.peopleCount,
                "known_face" to vector.knownFace,
                "unknown_face" to vector.unknownFace,
                "fall_event" to vector.fallEvent,
                "lifted" to vector.lifted,
                "idle_time" to vector.idleTime,
                "sound_energy" to vector.soundEnergy,
                "voice_rate" to vector.voiceRate,
                "motion_energy" to vector.motionEnergy
            )

            channels.forEachIndexed { idx, (name, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "[$idx] $name",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    LinearProgressIndicator(
                        progress = { value.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .width(80.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    Text(
                        "%.2f".format(value),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(44.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun RawReadingsCard(readings: SensorCollector.RawSensorReadings) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Speed, contentDescription = null)
                Text(
                    "Raw Sensor Readings",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            SensorRow("Accelerometer", "X=%.2f  Y=%.2f  Z=%.2f".format(
                readings.accelX, readings.accelY, readings.accelZ
            ))
            SensorRow("Gyroscope", "X=%.2f  Y=%.2f  Z=%.2f".format(
                readings.gyroX, readings.gyroY, readings.gyroZ
            ))
            SensorRow("Light", "%.1f lux".format(readings.light))
            SensorRow("Proximity", "%.1f cm".format(readings.proximity))
            SensorRow("Battery", "%.0f%%".format(readings.batteryPct))
            SensorRow("Sound RMS", "%.1f".format(readings.soundRms))
            SensorRow("Motion Mag", "%.2f m/s²".format(readings.motionMagnitude))
        }
    }
}

@Composable
private fun SensorRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun StatsCard(stats: VadBridgeClient.BridgeStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.BarChart, contentDescription = null)
                Text(
                    "Bridge Stats",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            SensorRow("Packets Sent", "${stats.packetsSent}")
            SensorRow("Packets Received", "${stats.packetsReceived}")
            SensorRow("Errors", "${stats.errors}")
        }
    }
}

@Composable
private fun FaceDetectionCard(
    faceDetectionEnabled: Boolean,
    faceResult: FaceProcessor.FaceResult,
    hasCameraPermission: Boolean,
    faceProcessor: FaceProcessor,
    onToggle: () -> Unit,
    onManageFaces: () -> Unit
) {
    var showPreview by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Face, contentDescription = null)
                Text(
                    "Face Detection (Edge)",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = faceDetectionEnabled,
                    onCheckedChange = { onToggle() }
                )
            }

            AnimatedVisibility(visible = faceDetectionEnabled && hasCameraPermission) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Camera runs in BG; preview is behind an expand toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(onClick = { showPreview = !showPreview }) {
                            Icon(
                                if (showPreview) Icons.Default.ExpandLess
                                else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (showPreview) "Hide Camera" else "Show Camera")
                        }
                    }

                    // Collapsible camera preview
                    AnimatedVisibility(visible = showPreview) {
                        CameraFacePreview(
                            faceProcessor = faceProcessor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    // The CameraX binding lives here regardless of preview visibility
                    // so face detection keeps running in the background
                    if (!showPreview) {
                        // Invisible CameraX binding — analysis runs, no preview rendered
                        CameraFacePreview(
                            faceProcessor = faceProcessor,
                            modifier = Modifier
                                .size(1.dp)
                                .clip(RoundedCornerShape(0.dp))
                        )
                    }

                    // Face results — only visible when camera preview is expanded
                    AnimatedVisibility(visible = showPreview) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${faceResult.peopleCount}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text("People", style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "%.0f%%".format(faceResult.knownFaceConfidence * 100),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text("Known", style = MaterialTheme.typography.bodySmall)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "%.0f%%".format(faceResult.unknownFaceConfidence * 100),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF9800)
                                    )
                                    Text("Unknown", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            if (faceResult.bestMatchName != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "✅ Recognised: ${faceResult.bestMatchName}",
                                        modifier = Modifier.padding(12.dp),
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Manage known faces button
            OutlinedButton(
                onClick = onManageFaces,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.People, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Manage Known Faces")
            }
        }
    }
}

/**
 * Map V/A/D values to emotional state labels, matching the server's PromptMode.
 */
private fun mapVadToEmotion(valence: Float, arousal: Float, dominance: Float): String {
    return when {
        valence > 0.6f && arousal > 0.6f && dominance > 0.5f -> "😆 Excited"
        valence > 0.55f && arousal in 0.3f..0.65f -> "😊 Happy"
        valence < 0.35f && arousal > 0.55f && dominance > 0.5f -> "😠 Angry"
        valence < 0.35f && arousal > 0.5f && dominance < 0.4f -> "😨 Fear"
        valence < 0.4f && arousal < 0.35f -> "😢 Sad"
        arousal < 0.3f && dominance < 0.4f -> "😴 Tired"
        valence in 0.4f..0.6f && arousal in 0.35f..0.55f -> "🤔 Curious"
        else -> "😐 Neutral"
    }
}
