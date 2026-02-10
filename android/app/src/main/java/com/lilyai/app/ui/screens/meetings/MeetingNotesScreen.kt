package com.lilyai.app.ui.screens.meetings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lilyai.app.domain.model.MeetingNote
import com.lilyai.app.domain.model.MeetingPhoto
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingNotesScreen(
    viewModel: MeetingNotesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadNotes()
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Show detail bottom sheet if a note is selected
    state.selectedNote?.let { note ->
        MeetingDetailSheet(
            note = note,
            isUploadingPhoto = state.isUploadingPhoto,
            onDismiss = { viewModel.clearSelectedNote() },
            onDelete = { viewModel.deleteMeetingNote(note.id) },
            onRefresh = { viewModel.refreshTranscription(note.id) },
            onAddPhoto = { file -> viewModel.uploadPhoto(note.id, file) },
            onDeletePhoto = { photoId -> viewModel.deletePhoto(note.id, photoId) },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Meeting Notes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (state.isRecording) {
            RecordingCard(
                durationSecs = state.recordingDurationSecs,
                isPaused = state.isPaused,
                meetingTitle = state.meetingTitle,
                onTitleChange = { viewModel.updateTitle(it) },
                onPause = { viewModel.pauseRecording() },
                onResume = { viewModel.resumeRecording() },
                onStop = { viewModel.stopRecording() },
            )
        } else {
            Button(
                onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.startRecording()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Meeting Recording")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp).align(Alignment.CenterHorizontally))
        } else if (state.notes.isEmpty()) {
            Text(
                "No meeting recordings yet.\nTap the button above to start recording.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.notes, key = { it.id }) { note ->
                    MeetingNoteCard(
                        note = note,
                        onClick = { viewModel.selectNote(note) },
                    )
                }
            }
        }

        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecordingCard(
    durationSecs: Int,
    isPaused: Boolean,
    meetingTitle: String,
    onTitleChange: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isPaused) "PAUSED" else "RECORDING",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDuration(durationSecs),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = meetingTitle,
                onValueChange = onTitleChange,
                label = { Text("Meeting Title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isPaused) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Resume")
                    }
                } else {
                    OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pause")
                    }
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun MeetingNoteCard(note: MeetingNote, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(note.meetingTitle, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(formatDuration(note.durationSecs))
                        append(" • ")
                        append(note.createdAt.take(10))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (note.transcriptText != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        note.transcriptText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            TranscriptionBadge(note.transcriptionStatus)
        }
    }
}

@Composable
private fun TranscriptionBadge(status: String) {
    val (text, color) = when (status) {
        "completed" -> "✓ Transcribed" to MaterialTheme.colorScheme.primary
        "transcribing" -> "⏳ Processing" to MaterialTheme.colorScheme.tertiary
        "failed" -> "✗ Failed" to MaterialTheme.colorScheme.error
        else -> "○ Pending" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetingDetailSheet(
    note: MeetingNote,
    isUploadingPhoto: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onAddPhoto: (File) -> Unit,
    onDeletePhoto: (String) -> Unit,
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPhotoDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    // Camera capture (must be declared before permission launcher that references it)
    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri.value?.let { uri ->
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                if (tempFile.exists() && tempFile.length() > 0) {
                    onAddPhoto(tempFile)
                }
            }
        }
    }

    // Camera permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCameraFile != null) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", pendingCameraFile!!
            )
            cameraUri.value = uri
            cameraLauncher.launch(uri)
        }
    }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val tempFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
            if (tempFile.exists() && tempFile.length() > 0) {
                onAddPhoto(tempFile)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Meeting?") },
            text = { Text("This will permanently delete the recording, transcript, and photos.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    showPhotoDeleteConfirm?.let { photoId ->
        AlertDialog(
            onDismissRequest = { showPhotoDeleteConfirm = null },
            title = { Text("Delete Photo?") },
            confirmButton = {
                TextButton(onClick = { showPhotoDeleteConfirm = null; onDeletePhoto(photoId) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoDeleteConfirm = null }) { Text("Cancel") }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.meetingTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                "${formatDuration(note.durationSecs)} • ${note.createdAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            // Transcript section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transcript", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (note.transcriptionStatus == "transcribing") {
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
            }

            Spacer(Modifier.height(8.dp))

            when (note.transcriptionStatus) {
                "completed" -> {
                    Text(
                        note.transcriptText ?: "No transcript available",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                "transcribing" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Transcription in progress...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "failed" -> {
                    Text("Transcription failed. The audio may be too short or unclear.",
                        color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    Text("Waiting for audio upload...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Photos section
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Photos", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (isUploadingPhoto) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick photo")
                }
                IconButton(onClick = {
                    val photoFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                    pendingCameraFile = photoFile
                    val hasCamPerm = ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCamPerm) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", photoFile
                        )
                        cameraUri.value = uri
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Take photo")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (note.photos.isEmpty()) {
                Text(
                    "No photos attached. Tap the icons above to add.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(note.photos, key = { it.id }) { photo ->
                        PhotoThumbnail(
                            photo = photo,
                            onDelete = { showPhotoDeleteConfirm = photo.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(photo: MeetingPhoto, onDelete: () -> Unit) {
    Box(modifier = Modifier.size(120.dp)) {
        AsyncImage(
            model = photo.photoUrl,
            contentDescription = photo.caption,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun formatDuration(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
