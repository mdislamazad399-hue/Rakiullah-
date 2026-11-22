package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LiveItemType
import com.example.data.model.UiState
import com.example.ui.components.CameraPreview
import com.example.ui.viewmodel.DeckDetailViewModel

@Composable
fun LiveClassScreen(
    viewModel: DeckDetailViewModel,
    onBack: () -> Unit,
) {
    val deckState by viewModel.deck.collectAsStateWithLifecycle()
    val flashcardsState by viewModel.flashcards.collectAsStateWithLifecycle()
    val questionsState by viewModel.questions.collectAsStateWithLifecycle()
    val liveSessionState by viewModel.liveSession.collectAsStateWithLifecycle()

    val deck = (deckState as? UiState.Success)?.data
    val flashcards = (flashcardsState as? UiState.Success)?.data.orEmpty()
    val questions = (questionsState as? UiState.Success)?.data.orEmpty()
    val liveSession = (liveSessionState as? UiState.Success)?.data

    var hasCameraPermission by remember { mutableStateOf(false) }
    var cameraOn by remember { mutableStateOf(true) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var isLiveBroadcast by remember { mutableStateOf(liveSession?.active ?: false) }

    var presenterType by remember {
        mutableStateOf(
            when {
                flashcards.isNotEmpty() -> LiveItemType.FLASHCARD
                questions.isNotEmpty() -> LiveItemType.QUIZ
                else -> LiveItemType.NONE
            }
        )
    }
    var itemIndex by remember { mutableIntStateOf(0) }
    var showCardAnswer by remember { mutableStateOf(false) }

    LaunchedEffect(flashcards.size, questions.size) {
        if (presenterType == LiveItemType.NONE) {
            presenterType = when {
                flashcards.isNotEmpty() -> LiveItemType.FLASHCARD
                questions.isNotEmpty() -> LiveItemType.QUIZ
                else -> LiveItemType.NONE
            }
        }
    }

    LaunchedEffect(liveSession?.active) {
        if (liveSession != null) {
            isLiveBroadcast = liveSession.active
        }
    }

    // Sync live prompt when live broadcast is active and item changes
    LaunchedEffect(isLiveBroadcast, presenterType, itemIndex, flashcards.size, questions.size, deck?.id) {
        val currentDeck = deck ?: return@LaunchedEffect
        if (isLiveBroadcast) {
            val prompt = when (presenterType) {
                LiveItemType.FLASHCARD -> flashcards.getOrNull(itemIndex)?.front.orEmpty()
                LiveItemType.QUIZ -> questions.getOrNull(itemIndex)?.prompt.orEmpty()
                LiveItemType.NONE -> ""
            }
            viewModel.updateLiveSession(
                deck = currentDeck,
                active = true,
                itemType = presenterType,
                itemIndex = itemIndex,
                activePrompt = prompt,
            )
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 1. Live CameraX Feed via reusable CameraPreview composable
            CameraPreview(
                cameraEnabled = cameraOn,
                useFrontCamera = useFrontCamera,
                onPermissionStateChanged = { granted -> hasCameraPermission = granted },
                onLensFallback = { activeFront -> useFrontCamera = activeFront },
                modifier = Modifier.fillMaxSize(),
            )

            // 2. Top Live Studio Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        onClick = {
                            val currentDeck = deck
                            if (isLiveBroadcast && currentDeck != null) {
                                viewModel.updateLiveSession(
                                    deck = currentDeck,
                                    active = false,
                                    itemType = LiveItemType.NONE,
                                    itemIndex = 0,
                                    activePrompt = "",
                                )
                            }
                            onBack()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.45f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.testTag("live_class_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Leave live class")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deck?.title ?: "Live Video Class",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (deck != null) {
                            Text(
                                text = "Join Code: ${deck.joinCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFE9C7),
                            )
                        }
                    }
                }

                val pillColor by animateColorAsState(
                    targetValue = if (isLiveBroadcast) Color(0xFFD93829) else Color.White.copy(alpha = 0.2f),
                    label = "livePillColor",
                )
                Surface(
                    color = pillColor,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("live_status_badge"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLiveBroadcast) Color.White else Color.LightGray)
                        )
                        Text(
                            text = if (isLiveBroadcast) "LIVE" else "STANDBY",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // 3. Bottom Presenter Card + Camera Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (flashcards.isNotEmpty() || questions.isNotEmpty()) {
                    LivePresenterCard(
                        presenterType = presenterType,
                        hasFlashcards = flashcards.isNotEmpty(),
                        hasQuestions = questions.isNotEmpty(),
                        itemIndex = itemIndex,
                        flashcardsCount = flashcards.size,
                        questionsCount = questions.size,
                        currentCardFront = flashcards.getOrNull(itemIndex)?.front,
                        currentCardBack = flashcards.getOrNull(itemIndex)?.back,
                        currentQuestionPrompt = questions.getOrNull(itemIndex)?.prompt,
                        currentQuestionAnswer = questions.getOrNull(itemIndex)?.let { q ->
                            q.options.getOrNull(q.correctIndex.toInt())
                        },
                        showAnswer = showCardAnswer,
                        onToggleType = { newType ->
                            presenterType = newType
                            itemIndex = 0
                            showCardAnswer = false
                        },
                        onPrev = {
                            val size = if (presenterType == LiveItemType.FLASHCARD) flashcards.size else questions.size
                            if (size > 0) {
                                itemIndex = (itemIndex - 1 + size) % size
                                showCardAnswer = false
                            }
                        },
                        onNext = {
                            val size = if (presenterType == LiveItemType.FLASHCARD) flashcards.size else questions.size
                            if (size > 0) {
                                itemIndex = (itemIndex + 1) % size
                                showCardAnswer = false
                            }
                        },
                        onToggleAnswer = { showCardAnswer = !showCardAnswer },
                    )
                }

                // Camera & Live Broadcast Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconButton(
                            onClick = { cameraOn = !cameraOn },
                            enabled = hasCameraPermission,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (cameraOn && hasCameraPermission) {
                                    Color.White.copy(alpha = 0.18f)
                                } else {
                                    Color(0xFFD93829)
                                },
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("toggle_camera_button"),
                        ) {
                            Icon(
                                imageVector = if (cameraOn && hasCameraPermission) {
                                    Icons.Rounded.Videocam
                                } else {
                                    Icons.Rounded.VideocamOff
                                },
                                contentDescription = if (cameraOn) "Turn camera off" else "Turn camera on",
                            )
                        }

                        IconButton(
                            onClick = { useFrontCamera = !useFrontCamera },
                            enabled = hasCameraPermission && cameraOn,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("switch_camera_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cameraswitch,
                                contentDescription = "Switch front or rear camera",
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val currentDeck = deck ?: return@Button
                            val nextLive = !isLiveBroadcast
                            isLiveBroadcast = nextLive
                            val prompt = when {
                                !nextLive -> ""
                                presenterType == LiveItemType.FLASHCARD -> flashcards.getOrNull(itemIndex)?.front.orEmpty()
                                presenterType == LiveItemType.QUIZ -> questions.getOrNull(itemIndex)?.prompt.orEmpty()
                                else -> ""
                            }
                            viewModel.updateLiveSession(
                                deck = currentDeck,
                                active = nextLive,
                                itemType = if (nextLive) presenterType else LiveItemType.NONE,
                                itemIndex = if (nextLive) itemIndex else 0,
                                activePrompt = prompt,
                            )
                        },
                        enabled = deck != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLiveBroadcast) Color(0xFFD93829) else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .semantics(mergeDescendants = true) {}
                            .testTag("go_live_button"),
                    ) {
                        Icon(
                            imageVector = if (isLiveBroadcast) Icons.Rounded.StopCircle else Icons.Rounded.Cast,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLiveBroadcast) "End Live Class" else "Go Live",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePresenterCard(
    presenterType: LiveItemType,
    hasFlashcards: Boolean,
    hasQuestions: Boolean,
    itemIndex: Int,
    flashcardsCount: Int,
    questionsCount: Int,
    currentCardFront: String?,
    currentCardBack: String?,
    currentQuestionPrompt: String?,
    currentQuestionAnswer: String?,
    showAnswer: Boolean,
    onToggleType: (LiveItemType) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleAnswer: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasFlashcards) {
                        FilterChip(
                            selected = presenterType == LiveItemType.FLASHCARD,
                            onClick = { onToggleType(LiveItemType.FLASHCARD) },
                            label = { Text("Cards ($flashcardsCount)") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        )
                    }
                    if (hasQuestions) {
                        FilterChip(
                            selected = presenterType == LiveItemType.QUIZ,
                            onClick = { onToggleType(LiveItemType.QUIZ) },
                            label = { Text("Quiz ($questionsCount)") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = "Previous item")
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = "Next item")
                    }
                }
            }

            val promptText = if (presenterType == LiveItemType.FLASHCARD) {
                currentCardFront.orEmpty()
            } else {
                currentQuestionPrompt.orEmpty()
            }
            val answerText = if (presenterType == LiveItemType.FLASHCARD) {
                currentCardBack.orEmpty()
            } else {
                currentQuestionAnswer.orEmpty()
            }
            val total = if (presenterType == LiveItemType.FLASHCARD) flashcardsCount else questionsCount

            Text(
                text = "PRESENTING ${itemIndex + 1} OF $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = promptText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAnswer && answerText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Answer: $answerText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            if (answerText.isNotBlank()) {
                TextButton(
                    onClick = onToggleAnswer,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (showAnswer) "Hide Answer" else "Reveal Answer")
                }
            }
        }
    }
}
