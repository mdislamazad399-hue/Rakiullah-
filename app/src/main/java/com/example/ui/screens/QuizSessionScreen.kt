package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UiState
import com.example.ui.components.MasteryRingChart
import com.example.ui.viewmodel.DeckDetailViewModel
import kotlin.math.roundToInt

private data class AnswerRecord(
    val prompt: String,
    val chosen: String,
    val correct: String,
    val isRight: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSessionScreen(
    viewModel: DeckDetailViewModel,
    onBack: () -> Unit,
) {
    val deckState by viewModel.deck.collectAsStateWithLifecycle()
    val questionsState by viewModel.questions.collectAsStateWithLifecycle()

    val deck = (deckState as? UiState.Success)?.data
    val questions = (questionsState as? UiState.Success)?.data.orEmpty()

    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var quizFinished by remember { mutableStateOf(false) }
    var savedToCloud by remember { mutableStateOf(false) }
    val records = remember { mutableStateListOf<AnswerRecord>() }

    LaunchedEffect(quizFinished) {
        if (quizFinished && !savedToCloud && deck != null && questions.isNotEmpty()) {
            savedToCloud = true
            viewModel.recordQuizResult(
                deck = deck,
                correctCount = correctCount,
                totalCount = questions.size,
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(deck?.title ?: "Interactive Quiz") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Exit quiz")
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
            contentAlignment = Alignment.TopCenter,
        ) {
            when (questionsState) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Text(
                    text = (questionsState as UiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                )
                is UiState.Success -> {
                    if (questions.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No quiz questions in this deck yet.")
                        }
                    } else if (quizFinished) {
                        val scorePct = ((correctCount.toFloat() / questions.size.toFloat()) * 100f).roundToInt()
                        val xpEarned = correctCount * 20 + 15
                        Column(
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MasteryRingChart(
                                progress = correctCount.toFloat() / questions.size.toFloat(),
                                centerLabel = "$scorePct%",
                                subtitle = "$correctCount/${questions.size}",
                                accentColor = if (scorePct >= 70) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                            )
                            Text(
                                text = when {
                                    scorePct >= 90 -> "Outstanding Mastery!"
                                    scorePct >= 70 -> "Great Progress!"
                                    else -> "Keep Practicing!"
                                },
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "You answered $correctCount out of ${questions.size} correctly and earned +$xpEarned XP.",
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
                                        currentIndex = 0
                                        selectedOption = null
                                        correctCount = 0
                                        streak = 0
                                        quizFinished = false
                                        savedToCloud = false
                                        records.clear()
                                    },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                ) {
                                    Text("Retake Quiz")
                                }
                                Button(
                                    onClick = onBack,
                                    modifier = Modifier.weight(1f).height(50.dp),
                                ) {
                                    Text("Done")
                                }
                            }

                            Text(
                                text = "Question Review",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            records.forEach { rec ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (rec.isRight) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                                contentDescription = null,
                                                tint = if (rec.isRight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = rec.prompt,
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                        }
                                        if (!rec.isRight) {
                                            Text(
                                                text = "Your answer: ${rec.chosen}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                        Text(
                                            text = "Correct answer: ${rec.correct}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    } else {
                        val question = questions[currentIndex.coerceIn(0, questions.lastIndex)]
                        val correctIdx = question.correctIndex.toInt().coerceIn(0, question.options.lastIndex.coerceAtLeast(0))
                        val hasAnswered = selectedOption != null

                        Column(
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Question ${currentIndex + 1} of ${questions.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (streak > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(999.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.LocalFireDepartment,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = "$streak streak",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                }
                            }

                            LinearProgressIndicator(
                                progress = { (currentIndex + 1).toFloat() / questions.size.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text = question.prompt,
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                question.options.forEachIndexed { idx, optionText ->
                                    val isChosen = selectedOption == idx
                                    val isCorrectOption = idx == correctIdx

                                    val containerColor = when {
                                        !hasAnswered -> MaterialTheme.colorScheme.surface
                                        isCorrectOption -> MaterialTheme.colorScheme.tertiaryContainer
                                        isChosen -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    val borderColor = when {
                                        !hasAnswered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        isCorrectOption -> MaterialTheme.colorScheme.tertiary
                                        isChosen -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    }
                                    val letter = ('A' + idx).toString()

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable(enabled = !hasAnswered) {
                                                selectedOption = idx
                                                val right = idx == correctIdx
                                                if (right) {
                                                    correctCount++
                                                    streak++
                                                } else {
                                                    streak = 0
                                                }
                                                records.add(
                                                    AnswerRecord(
                                                        prompt = question.prompt,
                                                        chosen = optionText,
                                                        correct = question.options.getOrElse(correctIdx) { "" },
                                                        isRight = right,
                                                    )
                                                )
                                            }
                                            .testTag("quiz_option_$idx"),
                                        color = containerColor,
                                        border = BorderStroke(1.5.dp, borderColor),
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = letter,
                                                        style = MaterialTheme.typography.labelLarge,
                                                    )
                                                }
                                            }
                                            Text(
                                                text = optionText,
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (hasAnswered && isCorrectOption) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                    contentDescription = "Correct",
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                )
                                            } else if (hasAnswered && isChosen) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Cancel,
                                                    contentDescription = "Incorrect",
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = hasAnswered && question.explanation.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "Instructor Explanation",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Text(
                                            text = question.explanation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }

                            if (hasAnswered) {
                                Button(
                                    onClick = {
                                        if (currentIndex + 1 < questions.size) {
                                            currentIndex++
                                            selectedOption = null
                                        } else {
                                            quizFinished = true
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("quiz_next_button"),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text(if (currentIndex + 1 < questions.size) "Next Question" else "See Results")
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}
