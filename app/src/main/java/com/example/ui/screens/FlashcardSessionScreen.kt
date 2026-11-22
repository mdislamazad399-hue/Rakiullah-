package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Flashcard
import com.example.data.model.UiState
import com.example.ui.components.MasteryRingChart
import com.example.ui.viewmodel.DeckDetailViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardSessionScreen(
    viewModel: DeckDetailViewModel,
    onBack: () -> Unit,
) {
    val deckState by viewModel.deck.collectAsStateWithLifecycle()
    val flashcardsState by viewModel.flashcards.collectAsStateWithLifecycle()

    val deck = (deckState as? UiState.Success)?.data
    val cards = (flashcardsState as? UiState.Success)?.data.orEmpty()

    val queue = remember { mutableStateListOf<Flashcard>() }
    val masteredIds = remember { mutableStateListOf<String>() }
    var initialized by remember { mutableStateOf(false) }
    var isFlipped by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var reviewedCount by remember { mutableIntStateOf(0) }
    var knownCount by remember { mutableIntStateOf(0) }
    var sessionComplete by remember { mutableStateOf(false) }
    var savedToCloud by remember { mutableStateOf(false) }

    LaunchedEffect(cards) {
        if (!initialized && cards.isNotEmpty()) {
            queue.clear()
            queue.addAll(cards)
            initialized = true
        }
    }

    LaunchedEffect(sessionComplete) {
        if (sessionComplete && !savedToCloud && deck != null && cards.isNotEmpty()) {
            savedToCloud = true
            viewModel.recordFlashcardSession(
                deck = deck,
                masteredCardIds = masteredIds.toList(),
                reviewedCount = reviewedCount,
                knownCount = knownCount,
                totalCardsInDeck = cards.size,
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(deck?.title ?: "Flashcards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Exit flashcards")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (flashcardsState) {
                UiState.Loading -> CircularProgressIndicator()
                is UiState.Error -> Text(
                    text = (flashcardsState as UiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                )
                is UiState.Success -> {
                    if (cards.isEmpty()) {
                        Text("No flashcards in this deck yet.")
                    } else if (sessionComplete || queue.isEmpty()) {
                        val masteryPct = ((masteredIds.size.toFloat() / cards.size.toFloat()) * 100f).roundToInt()
                        val xpEarned = knownCount * 10 + reviewedCount * 3
                        Column(
                            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            MasteryRingChart(
                                progress = masteredIds.size.toFloat() / cards.size.toFloat(),
                                centerLabel = "$masteryPct%",
                                subtitle = "Mastered",
                                accentColor = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = "Flashcard Session Complete!",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "You mastered ${masteredIds.size} of ${cards.size} cards across $reviewedCount reviews and earned +$xpEarned XP.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        queue.clear()
                                        queue.addAll(cards)
                                        masteredIds.clear()
                                        reviewedCount = 0
                                        knownCount = 0
                                        isFlipped = false
                                        showHint = false
                                        sessionComplete = false
                                        savedToCloud = false
                                    },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                ) {
                                    Text("Study Again")
                                }
                                Button(
                                    onClick = onBack,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    } else {
                        val currentCard = queue.first()
                        val rotation by animateFloatAsState(
                            targetValue = if (isFlipped) 180f else 0f,
                            animationSpec = tween(durationMillis = 420),
                            label = "cardFlip",
                        )

                        Column(
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${masteredIds.size} of ${cards.size} mastered",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                    Text(
                                        text = "${queue.size} remaining",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { masteredIds.size.toFloat() / cards.size.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(vertical = 20.dp)
                                    .graphicsLayer {
                                        rotationY = rotation
                                        cameraDistance = 12f * density
                                    }
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .clickable { isFlipped = !isFlipped }
                                    .testTag("flashcard_flip_surface"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (rotation <= 90f) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                shape = MaterialTheme.shapes.extraLarge,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(28.dp)
                                        .graphicsLayer {
                                            if (rotation > 90f) rotationY = 180f
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (rotation <= 90f) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(999.dp),
                                            ) {
                                                Text(
                                                    text = "FRONT • TAP TO FLIP",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                                )
                                            }
                                            Text(
                                                text = currentCard.front,
                                                style = MaterialTheme.typography.headlineMedium,
                                                textAlign = TextAlign.Center,
                                            )
                                            if (currentCard.hint.isNotBlank()) {
                                                if (showHint) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = MaterialTheme.shapes.medium,
                                                    ) {
                                                        Text(
                                                            text = "Hint: ${currentCard.hint}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.padding(12.dp),
                                                        )
                                                    }
                                                } else {
                                                    TextButton(onClick = { showHint = true }) {
                                                        Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Show Hint")
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(999.dp),
                                            ) {
                                                Text(
                                                    text = "ANSWER",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                                )
                                            }
                                            Text(
                                                text = currentCard.back,
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }

                            if (!isFlipped) {
                                Button(
                                    onClick = { isFlipped = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("reveal_answer_button"),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Icon(Icons.Rounded.Flip, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reveal Answer")
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            reviewedCount++
                                            val popped = queue.removeAt(0)
                                            queue.add(popped)
                                            isFlipped = false
                                            showHint = false
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .testTag("review_again_button"),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                    ) {
                                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Review Again")
                                    }
                                    Button(
                                        onClick = {
                                            reviewedCount++
                                            knownCount++
                                            val popped = queue.removeAt(0)
                                            if (popped.id !in masteredIds) {
                                                masteredIds.add(popped.id)
                                            }
                                            isFlipped = false
                                            showHint = false
                                            if (queue.isEmpty()) {
                                                sessionComplete = true
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .testTag("got_it_button"),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary,
                                        ),
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Got It!")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}
