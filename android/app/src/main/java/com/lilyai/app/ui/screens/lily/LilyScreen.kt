package com.lilyai.app.ui.screens.lily

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.cos
import kotlin.math.sin

private val Accent = Color(0xFF7C4DFF)
private val AccentGlow = Color(0xFFB388FF)
private val TextDim = Color(0xFF6B7280)
private val TextBright = Color(0xFF1F2937)
private val BubbleBg = Color(0xFFF3EEFF)
private val LilyBubbleBg = Color(0xFFFFFFFF)

@Composable
fun LilyScreen(viewModel: LilyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.connect()
    }

    // Auto-connect when screen opens
    LaunchedEffect(Unit) {
        val hasMic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.connect()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disconnect() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF5FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status bar
            val statusText = when {
                state.error != null -> state.error ?: ""
                !state.isConnected -> "Connecting..."
                state.isSpeaking -> "Speaking"
                state.isListening -> "Listening"
                else -> "Ready"
            }
            val statusColor = when {
                state.error != null -> Color(0xFFEF4444)
                state.isListening -> Color(0xFF10B981)
                state.isSpeaking -> AccentGlow
                state.isConnected -> Color(0xFF10B981)
                else -> TextDim
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(8.dp)) {
                    drawCircle(statusColor)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    statusText,
                    fontSize = 13.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Animated orb
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedOrb(
                    isActive = state.isListening || state.isSpeaking,
                    isSpeaking = state.isSpeaking,
                )

                // Mic toggle overlay
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!state.isConnected) return@clickable
                            if (state.isListening) viewModel.stopListening()
                            else viewModel.startListening()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (state.isListening) "Tap to stop" else "Tap orb to speak",
                fontSize = 12.sp,
                color = TextDim,
            )

            Spacer(Modifier.height(24.dp))

            // Conversation area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state.transcript.isNotEmpty()) {
                    ConversationBubble(
                        label = "You",
                        text = state.transcript,
                        isUser = true,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (state.responseText.isNotEmpty()) {
                    ConversationBubble(
                        label = "Lily",
                        text = state.responseText,
                        isUser = false,
                    )
                }

                if (state.isSpeaking && state.responseText.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    WaveformDots()
                }
            }
        }
    }
}

@Composable
private fun AnimatedOrb(isActive: Boolean, isSpeaking: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (isActive) 800 else 3000, easing = EaseInOutCubic),
            RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (isActive) 3000 else 8000, easing = LinearEasing),
        ),
        label = "rotation",
    )

    val glowAlpha = if (isActive) 0.6f else 0.2f

    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        // Outer glow
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .scale(breathe + 0.15f)
                .blur(40.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isSpeaking) AccentGlow.copy(alpha = glowAlpha)
                        else Accent.copy(alpha = glowAlpha),
                        Color.Transparent,
                    )
                )
            )
        }

        // Inner orb with gradient
        Canvas(
            modifier = Modifier
                .size(140.dp)
                .scale(breathe)
        ) {
            val rad = Math.toRadians(rotation.toDouble())
            val cx = center.x + (20f * cos(rad)).toFloat()
            val cy = center.y + (20f * sin(rad)).toFloat()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentGlow.copy(alpha = 0.8f),
                        Accent,
                        Color(0xFF3D1F99),
                    ),
                    center = Offset(cx, cy),
                    radius = size.minDimension / 1.5f,
                )
            )
        }

        // Core bright spot
        Canvas(
            modifier = Modifier
                .size(50.dp)
                .scale(breathe)
                .blur(12.dp)
        ) {
            drawCircle(Color.White.copy(alpha = if (isActive) 0.4f else 0.15f))
        }
    }
}

@Composable
private fun ConversationBubble(label: String, text: String, isUser: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isUser) Accent else AccentGlow,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            letterSpacing = 0.5.sp,
        )
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (isUser) BubbleBg else LilyBubbleBg,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text,
                fontSize = 14.sp,
                color = TextBright,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun WaveformDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (0..4).forEach { i ->
            val height by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = 20f,
                animationSpec = infiniteRepeatable(
                    tween(400, delayMillis = i * 80, easing = EaseInOutCubic),
                    RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(3.dp)
                    .height(height.dp)
                    .background(AccentGlow.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
            )
        }
    }
}
