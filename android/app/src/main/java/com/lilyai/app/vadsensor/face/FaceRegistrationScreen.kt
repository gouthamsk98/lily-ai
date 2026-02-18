package com.lilyai.app.vadsensor.face

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRegistrationScreen(
    onBack: () -> Unit,
    viewModel: FaceRegistrationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val faceResult by viewModel.faceResult.collectAsStateWithLifecycle()
    val regState by viewModel.regState.collectAsStateWithLifecycle()
    val knownFaces by viewModel.knownFaces.collectAsStateWithLifecycle()

    // ── Camera permission ────────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Name dialog state ────────────────────────────────────────────
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingFeatures by remember { mutableStateOf<FloatArray?>(null) }

    // Watch for NamingFace state → open dialog
    LaunchedEffect(regState) {
        if (regState is FaceRegistrationViewModel.RegState.NamingFace) {
            pendingFeatures = (regState as FaceRegistrationViewModel.RegState.NamingFace).features
            showNameDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Face Registration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Camera preview ───────────────────────────────────────
            if (hasCameraPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box {
                        CameraFacePreview(
                            faceProcessor = viewModel.faceProcessor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )

                        // Face count badge
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = if (faceResult.peopleCount > 0)
                                Color(0xFF4CAF50).copy(alpha = 0.9f)
                            else Color(0xFF9E9E9E).copy(alpha = 0.9f)
                        ) {
                            Text(
                                "👤 ${faceResult.peopleCount}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Match info
                        if (faceResult.bestMatchName != null) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF2196F3).copy(alpha = 0.9f)
                            ) {
                                Text(
                                    "✅ ${faceResult.bestMatchName}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("Camera permission required", fontWeight = FontWeight.Medium)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            // ── Registration controls ────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status text
                    val statusText = when (val s = regState) {
                        is FaceRegistrationViewModel.RegState.Idle -> {
                            if (faceResult.peopleCount == 1) "✅ Face detected — ready to capture"
                            else if (faceResult.peopleCount == 0) "📷 Position your face in the camera"
                            else "⚠️ Only 1 face should be visible (found ${faceResult.peopleCount})"
                        }
                        is FaceRegistrationViewModel.RegState.Capturing ->
                            "📸 Capturing... ${s.captured}/${s.total}"
                        is FaceRegistrationViewModel.RegState.NamingFace ->
                            "✏️ Enter a name for this face"
                        is FaceRegistrationViewModel.RegState.Saved ->
                            "✅ Face saved successfully!"
                        is FaceRegistrationViewModel.RegState.Error ->
                            "❌ ${s.message}"
                    }
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)

                    // Capture progress
                    AnimatedVisibility(visible = regState is FaceRegistrationViewModel.RegState.Capturing) {
                        val capturing = regState as? FaceRegistrationViewModel.RegState.Capturing
                        if (capturing != null) {
                            LinearProgressIndicator(
                                progress = { capturing.captured.toFloat() / capturing.total },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startCapture() },
                            enabled = hasCameraPermission &&
                                    regState is FaceRegistrationViewModel.RegState.Idle &&
                                    faceResult.peopleCount == 1,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Register Face")
                        }

                        if (regState is FaceRegistrationViewModel.RegState.Capturing) {
                            OutlinedButton(onClick = { viewModel.cancelRegistration() }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            // ── Known faces list ─────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.People, contentDescription = null)
                        Text(
                            "Known Faces (${knownFaces.size})",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (knownFaces.isEmpty()) {
                        Text(
                            "No faces registered yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Use a fixed-height Column instead of LazyColumn inside scrollable
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            knownFaces.forEach { face ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            face.name,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "${face.features.size} features • ${formatAge(face.capturedAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteFace(face.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                if (face != knownFaces.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Name dialog ──────────────────────────────────────────────────
    if (showNameDialog && pendingFeatures != null) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showNameDialog = false
                viewModel.cancelRegistration()
            },
            icon = { Icon(Icons.Default.Face, contentDescription = null) },
            title = { Text("Name this face") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveFace(nameInput, pendingFeatures!!)
                        showNameDialog = false
                        pendingFeatures = null
                    },
                    enabled = nameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNameDialog = false
                    pendingFeatures = null
                    viewModel.cancelRegistration()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatAge(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    val mins = diff / 60_000
    val hours = mins / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        mins > 0 -> "${mins}m ago"
        else -> "just now"
    }
}
