package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.media.PickedVideoMetadata
import com.example.data.media.StudyVideoStorage
import com.example.data.model.StudyDeck
import com.example.data.model.UiState
import com.example.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadVideoScreen(
    homeViewModel: HomeViewModel,
    initialDeckId: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val teacherDecksState by homeViewModel.teacherDecks.collectAsStateWithLifecycle()
    val uploadProgress by homeViewModel.uploadVideoProgress.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var videoMeta by remember { mutableStateOf<PickedVideoMetadata?>(null) }
    var isInspecting by remember { mutableStateOf(false) }

    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var selectedDeckId by rememberSaveable(initialDeckId) { mutableStateOf(initialDeckId) }
    var creatingNewDeck by rememberSaveable { mutableStateOf(false) }
    var newDeckTitle by rememberSaveable { mutableStateOf("") }
    var newDeckSubject by rememberSaveable { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
            isInspecting = true
            scope.launch {
                val meta = StudyVideoStorage.inspectVideo(context, uri)
                videoMeta = meta
                if (title.isBlank()) {
                    title = meta.displayName
                }
                isInspecting = false
            }
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val decks = (teacherDecksState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(decks, initialDeckId) {
        if (!creatingNewDeck) {
            val resolved = when {
                initialDeckId != null && decks.any { it.id == initialDeckId } -> initialDeckId
                selectedDeckId != null && decks.any { it.id == selectedDeckId } -> selectedDeckId
                decks.isNotEmpty() -> decks.first().id
                else -> null
            }
            selectedDeckId = resolved
            if (decks.isEmpty() && teacherDecksState is UiState.Success) {
                creatingNewDeck = true
            }
        }
    }

    val selectedDeck: StudyDeck? = remember(decks, selectedDeckId, creatingNewDeck) {
        if (creatingNewDeck) null else decks.firstOrNull { it.id == selectedDeckId }
    }
    val isUploading = uploadProgress != null
    val canSubmit = pickedUri != null &&
        title.trim().isNotEmpty() &&
        (selectedDeck != null || newDeckTitle.trim().isNotEmpty()) &&
        !isUploading &&
        !isInspecting

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_video_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isUploading,
                        modifier = Modifier.testTag("upload_video_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.upload_video_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Fast 1-Tap Photo Picker Video Dropzone
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable(enabled = !isUploading) {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                        .testTag("pick_study_video_button"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = MaterialTheme.shapes.large,
                ) {
                    val thumb = videoMeta?.thumbnail
                    if (pickedUri == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.VideoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.upload_video_pick),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Select any MP4 or recorded study clip directly — no extra storage permission needed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb.asImageBitmap(),
                                        contentDescription = "Selected video preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isInspecting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp),
                                            )
                                        }
                                    }
                                }
                                videoMeta?.let { meta ->
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.7f),
                                            shape = MaterialTheme.shapes.small,
                                        ) {
                                            Text(
                                                text = StudyVideoStorage.formatDuration(meta.durationMs),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                        val sizeLabel = StudyVideoStorage.formatFileSize(meta.sizeBytes)
                                        if (sizeLabel.isNotBlank()) {
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.7f),
                                                shape = MaterialTheme.shapes.small,
                                            ) {
                                                Text(
                                                    text = sizeLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Video ready to upload",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                OutlinedButton(
                                    onClick = {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    enabled = !isUploading,
                                    modifier = Modifier.height(48.dp),
                                ) {
                                    Text(stringResource(R.string.upload_video_change))
                                }
                            }
                        }
                    }
                }

                // Destination Deck Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Style,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Attach to Study Deck",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (decks.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                decks.forEach { deck ->
                                    val isSelected = !creatingNewDeck && selectedDeckId == deck.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            creatingNewDeck = false
                                            selectedDeckId = deck.id
                                        },
                                        label = {
                                            Text(
                                                text = deck.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else null,
                                    )
                                }
                                FilterChip(
                                    selected = creatingNewDeck,
                                    onClick = { creatingNewDeck = true },
                                    label = { Text("New Deck") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }

                        if (creatingNewDeck || decks.isEmpty()) {
                            OutlinedTextField(
                                value = newDeckTitle,
                                onValueChange = { if (it.length <= 100) newDeckTitle = it },
                                label = { Text("New Deck Title") },
                                placeholder = { Text("e.g., Physics Lectures") },
                                singleLine = true,
                                enabled = !isUploading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = newDeckSubject,
                                onValueChange = { if (it.length <= 50) newDeckSubject = it },
                                label = { Text("Subject (optional)") },
                                placeholder = { Text("e.g., Physics") },
                                singleLine = true,
                                enabled = !isUploading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // Lesson Metadata Form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { if (it.length <= 100) title = it },
                            label = { Text(stringResource(R.string.upload_video_lesson_title)) },
                            placeholder = { Text(stringResource(R.string.upload_video_lesson_placeholder)) },
                            singleLine = true,
                            enabled = !isUploading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_title_input"),
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { if (it.length <= 300) notes = it },
                            label = { Text(stringResource(R.string.upload_video_notes_label)) },
                            placeholder = { Text(stringResource(R.string.upload_video_notes_placeholder)) },
                            minLines = 2,
                            maxLines = 4,
                            enabled = !isUploading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_notes_input"),
                        )
                    }
                }

                if (uploadProgress != null) {
                    val pct = ((uploadProgress ?: 0f) * 100f).roundToInt().coerceIn(0, 100)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { uploadProgress ?: 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                        )
                        Text(
                            text = "Uploading & saving study video... $pct%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Button(
                    onClick = {
                        val uri = pickedUri ?: return@Button
                        homeViewModel.uploadStudyVideo(
                            context = context,
                            videoUri = uri,
                            selectedDeck = selectedDeck,
                            newDeckTitle = newDeckTitle,
                            newDeckSubject = newDeckSubject,
                            title = title,
                            notes = notes,
                            durationMs = videoMeta?.durationMs ?: 0L,
                            fileSizeHintBytes = videoMeta?.sizeBytes ?: 0L,
                            onUploaded = { onBack() },
                        )
                    },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_upload_video_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uploading...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.upload_video_submit))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
