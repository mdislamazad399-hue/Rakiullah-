package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.media.BatchImageStorage
import com.example.data.model.DeckAccent
import com.example.data.model.StudyBatch
import com.example.data.model.UiState
import com.example.ui.theme.deckAccentPalette
import com.example.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBatchScreen(
    homeViewModel: HomeViewModel,
    editingBatchId: String? = null,
    onBack: () -> Unit,
    onBatchSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isSaving by homeViewModel.isSavingBatch.collectAsStateWithLifecycle()
    val teacherBatchesState by homeViewModel.teacherBatches.collectAsStateWithLifecycle()

    val existingBatch: StudyBatch? = remember(editingBatchId, teacherBatchesState) {
        if (editingBatchId.isNullOrBlank()) null
        else (teacherBatchesState as? UiState.Success)?.data?.firstOrNull { it.id == editingBatchId }
    }

    var initializedForId by remember { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var scheduleText by rememberSaveable { mutableStateOf("") }
    var isFree by rememberSaveable { mutableStateOf(false) }
    var priceText by rememberSaveable { mutableStateOf("499") }
    var upiId by rememberSaveable { mutableStateOf("") }
    var coverImageData by rememberSaveable { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }
    var accent by remember { mutableStateOf(DeckAccent.INDIGO) }
    var published by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(existingBatch) {
        if (existingBatch != null && initializedForId != existingBatch.id) {
            initializedForId = existingBatch.id
            title = existingBatch.title
            subject = existingBatch.subject
            description = existingBatch.description
            scheduleText = existingBatch.scheduleText
            isFree = existingBatch.priceInr == 0L
            priceText = if (existingBatch.priceInr > 0L) existingBatch.priceInr.toString() else "0"
            upiId = existingBatch.upiId
            coverImageData = existingBatch.coverImageData
            previewBitmap = BatchImageStorage.decodeToImageBitmap(existingBatch.coverImageData)
            accent = DeckAccent.fromRaw(existingBatch.accentColorKey)
            published = existingBatch.published
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    isProcessingImage = true
                    try {
                        val picked = BatchImageStorage.processPickedImage(context, uri)
                        previewBitmap = picked.previewBitmap
                        coverImageData = picked.base64Data
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(e.message ?: "Failed to load batch image.")
                    } finally {
                        isProcessingImage = false
                    }
                }
            }
        },
    )

    val parsedPrice = if (isFree) 0L else (priceText.trim().toLongOrNull() ?: -1L)
    val isValid = title.trim().isNotEmpty() &&
        subject.trim().isNotEmpty() &&
        parsedPrice in 0L..100_000L &&
        !isSaving &&
        !isProcessingImage
    val isDark = isSystemInDarkTheme()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingBatch == null) "Add New Batch" else "Edit Batch",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("add_batch_back_button"),
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
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Batch Cover Image Upload Card (Zero-permission Photo Picker)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Batch Cover Image",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .clickable(enabled = !isProcessingImage) {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isProcessingImage -> CircularProgressIndicator()
                                previewBitmap != null -> {
                                    Image(
                                        bitmap = previewBitmap!!,
                                        contentDescription = "Selected batch cover image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                else -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp),
                                        )
                                        Text(
                                            text = "Tap to upload a batch banner image",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isProcessingImage,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("upload_batch_image_button"),
                            ) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (previewBitmap == null) {
                                        stringResource(R.string.batch_image_upload)
                                    } else {
                                        stringResource(R.string.batch_image_change)
                                    }
                                )
                            }
                            if (previewBitmap != null) {
                                OutlinedButton(
                                    onClick = {
                                        previewBitmap = null
                                        coverImageData = ""
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("remove_batch_image_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.batch_image_remove),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                // Batch Details
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("Batch Name *") },
                    placeholder = { Text("e.g., NEET Physics Crash Batch 2026") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_title_input"),
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { if (it.length <= 50) subject = it },
                    label = { Text("Subject / Exam *") },
                    placeholder = { Text("e.g., Physics, Maths, UPSC, Coding") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_subject_input"),
                )

                OutlinedTextField(
                    value = scheduleText,
                    onValueChange = { if (it.length <= 100) scheduleText = it },
                    label = { Text("Class Timing / Schedule (optional)") },
                    placeholder = { Text("e.g., Mon–Fri • 6:00 PM IST") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_schedule_input"),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 400) description = it },
                    label = { Text("Batch Details & Syllabus (optional)") },
                    placeholder = { Text("What students will learn, mock tests, live sessions...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_description_input"),
                )

                // Batch Fee / Payment Options
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Batch Fee & Payment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            FilterChip(
                                selected = isFree,
                                onClick = { isFree = !isFree },
                                label = { Text("Free Batch") },
                                modifier = Modifier.testTag("batch_free_chip"),
                            )
                        }

                        if (!isFree) {
                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }
                                    if (digits.length <= 6) priceText = digits
                                },
                                label = { Text("Batch Fee (INR)") },
                                leadingIcon = { Icon(Icons.Rounded.CurrencyRupee, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("batch_price_input"),
                            )
                            OutlinedTextField(
                                value = upiId,
                                onValueChange = { if (it.length <= 100) upiId = it },
                                label = { Text("Teacher UPI ID (optional)") },
                                placeholder = { Text("e.g., teacher@okaxis") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("batch_upi_input"),
                            )
                        }
                    }
                }

                // Theme & Publish Toggle
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Accent Theme",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeckAccent.entries.forEach { option ->
                            val palette = deckAccentPalette(option.name, isDark)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                                    .clickable { accent = option },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (option == accent) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "${option.label} selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Publish Batch in Search",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Allow students to find, pay, and join this batch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = published,
                        onCheckedChange = { published = it },
                        modifier = Modifier.testTag("batch_publish_switch"),
                    )
                }

                Button(
                    onClick = {
                        if (existingBatch == null) {
                            homeViewModel.createBatch(
                                title = title,
                                subject = subject,
                                description = description,
                                scheduleText = scheduleText,
                                priceInr = parsedPrice,
                                upiId = if (isFree) "" else upiId,
                                coverImageData = coverImageData,
                                accent = accent,
                                published = published,
                                onCreated = onBatchSaved,
                            )
                        } else {
                            homeViewModel.updateBatch(
                                batchId = existingBatch.id,
                                title = title,
                                subject = subject,
                                description = description,
                                scheduleText = scheduleText,
                                priceInr = parsedPrice,
                                upiId = if (isFree) "" else upiId,
                                coverImageData = coverImageData,
                                accent = accent,
                                published = published,
                                onUpdated = { onBatchSaved(existingBatch.id) },
                            )
                        }
                    },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_batch_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (existingBatch == null) "Save & Publish Batch" else "Save Batch Changes")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
