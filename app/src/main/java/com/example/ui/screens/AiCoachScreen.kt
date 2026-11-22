package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Enrollment
import com.example.data.model.StudyDeck
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.viewmodel.AiCoachUiState
import com.example.ui.viewmodel.AiCoachViewModel
import com.example.ui.viewmodel.CoachDeckOption
import com.example.ui.viewmodel.CoachMessage

@Composable
fun AiCoachScreen(
    coachViewModel: AiCoachViewModel,
    profileState: UiState<UserProfile?>,
    teacherDecksState: UiState<List<StudyDeck>>,
    enrollmentsState: UiState<List<Enrollment>>,
    publishedDecksState: UiState<List<StudyDeck>>,
    currentUserId: String,
    modifier: Modifier = Modifier,
) {
    val messages by coachViewModel.messages.collectAsStateWithLifecycle()
    val uiState by coachViewModel.uiState.collectAsStateWithLifecycle()
    val selectedDeckId by coachViewModel.selectedDeckId.collectAsStateWithLifecycle()
    val loadedFlashcardCount by coachViewModel.loadedFlashcardCount.collectAsStateWithLifecycle()
    val isLoadingFlashcards by coachViewModel.isLoadingFlashcards.collectAsStateWithLifecycle()
    val flashcardContextError by coachViewModel.flashcardContextError.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val profile = (profileState as? UiState.Success)?.data
    val teacherDecks = (teacherDecksState as? UiState.Success)?.data.orEmpty()
    val enrollments = (enrollmentsState as? UiState.Success)?.data.orEmpty()
    val publishedDecks = (publishedDecksState as? UiState.Success)?.data.orEmpty()

    val deckOptions = remember(teacherDecks, enrollments, publishedDecks, currentUserId) {
        val teacherIds = teacherDecks.map { it.id }.toSet()
        val byId = linkedMapOf<String, CoachDeckOption>()
        teacherDecks.forEach { deck ->
            byId[deck.id] = CoachDeckOption(
                deckId = deck.id,
                title = deck.title,
                subject = deck.subject,
                isOwner = true,
            )
        }
        enrollments.forEach { enrollment ->
            if (!byId.containsKey(enrollment.deckId)) {
                byId[enrollment.deckId] = CoachDeckOption(
                    deckId = enrollment.deckId,
                    title = enrollment.deckTitle,
                    subject = enrollment.subject,
                    isOwner = teacherIds.contains(enrollment.deckId),
                )
            }
        }
        publishedDecks.forEach { deck ->
            if (!byId.containsKey(deck.id)) {
                byId[deck.id] = CoachDeckOption(
                    deckId = deck.id,
                    title = deck.title,
                    subject = deck.subject,
                    isOwner = deck.teacherId == currentUserId,
                )
            }
        }
        byId.values.toList()
    }

    val isStreaming = uiState == AiCoachUiState.Streaming

    LaunchedEffect(deckOptions) {
        coachViewModel.syncAvailableDecks(deckOptions)
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val quickPrompts = listOf(
        "Quiz me on my flashcards",
        "Explain the hardest flashcard simply",
        "Give me memory tricks for these cards",
        "Summarize key terms from my deck",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
        ) {
            // Conversation / Welcome Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AiCoachHeaderCard(
                        hasMessages = messages.isNotEmpty(),
                        isStreaming = isStreaming,
                        loadedFlashcardCount = loadedFlashcardCount,
                        isLoadingFlashcards = isLoadingFlashcards,
                        flashcardContextError = flashcardContextError,
                        onRefreshFlashcards = {
                            coachViewModel.refreshFlashcardContext(deckOptions, force = true)
                        },
                        onClearChat = { coachViewModel.clearChat() },
                    )
                }

                if (deckOptions.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Flashcard Context Deck",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = selectedDeckId == null,
                                    onClick = { coachViewModel.selectDeck(null, deckOptions) },
                                    enabled = !isStreaming,
                                    label = { Text("All Flashcards", maxLines = 1) },
                                    modifier = Modifier.testTag("ai_coach_deck_chip_all"),
                                )
                                deckOptions.forEach { deck ->
                                    FilterChip(
                                        selected = selectedDeckId == deck.deckId,
                                        onClick = { coachViewModel.selectDeck(deck.deckId, deckOptions) },
                                        enabled = !isStreaming,
                                        label = {
                                            Text(
                                                text = deck.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        modifier = Modifier.testTag("ai_coach_deck_chip_${deck.deckId}"),
                                    )
                                }
                            }
                        }
                    }
                }

                if (messages.isEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Quick Coaching Prompts",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            quickPrompts.forEachIndexed { idx, prompt ->
                                Surface(
                                    onClick = {
                                        coachViewModel.sendMessage(
                                            rawPrompt = prompt,
                                            profile = profile,
                                            teacherDecks = teacherDecks,
                                            enrollments = enrollments,
                                            deckOptions = deckOptions,
                                        )
                                    },
                                    enabled = !isStreaming,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_coach_quick_prompt_$idx"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = prompt,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        CoachMessageBubble(message = msg)
                    }
                }

                if (uiState is AiCoachUiState.Error) {
                    val errorMsg = (uiState as AiCoachUiState.Error).message
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_coach_error_banner"),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { coachViewModel.dismissError() },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss error",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Suggestion Chips when chatting
            if (messages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickPrompts.forEach { prompt ->
                        AssistChip(
                            onClick = {
                                coachViewModel.sendMessage(
                                    rawPrompt = prompt,
                                    profile = profile,
                                    teacherDecks = teacherDecks,
                                    enrollments = enrollments,
                                    deckOptions = deckOptions,
                                )
                            },
                            enabled = !isStreaming,
                            label = { Text(prompt, maxLines = 1) },
                        )
                    }
                }
            }

            // Message Composer
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= 1000) inputText = it },
                        placeholder = { Text(stringResource(R.string.ai_coach_input_hint)) },
                        maxLines = 4,
                        enabled = !isStreaming,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_coach_input"),
                    )
                    FilledIconButton(
                        onClick = {
                            val toSend = inputText
                            inputText = ""
                            coachViewModel.sendMessage(
                                rawPrompt = toSend,
                                profile = profile,
                                teacherDecks = teacherDecks,
                                enrollments = enrollments,
                                deckOptions = deckOptions,
                            )
                        },
                        enabled = inputText.trim().isNotEmpty() && !isStreaming,
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("ai_coach_send_button"),
                    ) {
                        if (isStreaming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send to AI Coach",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiCoachHeaderCard(
    hasMessages: Boolean,
    isStreaming: Boolean,
    loadedFlashcardCount: Int,
    isLoadingFlashcards: Boolean,
    flashcardContextError: String?,
    onRefreshFlashcards: () -> Unit,
    onClearChat: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        )
                    )
                )
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.ai_coach_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (hasMessages) {
                        TextButton(
                            onClick = onClearChat,
                            enabled = !isStreaming,
                            modifier = Modifier.testTag("ai_coach_clear_button"),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.ai_coach_clear))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.ai_coach_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_coach_grounding_status"),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isLoadingFlashcards) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Style,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        val statusText = when {
                            isLoadingFlashcards -> "Loading flashcards from Firestore..."
                            flashcardContextError != null && loadedFlashcardCount == 0 -> flashcardContextError
                            loadedFlashcardCount > 0 ->
                                "$loadedFlashcardCount flashcard${if (loadedFlashcardCount == 1) "" else "s"} • ${stringResource(R.string.ai_coach_flashcards_grounded)}"
                            else -> "Add or join a deck to ground answers in flashcards"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (flashcardContextError != null && loadedFlashcardCount == 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = onRefreshFlashcards,
                            enabled = !isLoadingFlashcards && !isStreaming,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("ai_coach_refresh_flashcards"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh flashcards",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachMessageBubble(message: CoachMessage) {
    val isUser = message.isFromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "AI Coach",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (message.text.isBlank() && message.isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Thinking...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
