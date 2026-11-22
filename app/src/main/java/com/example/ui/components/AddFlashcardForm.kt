package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.StudyDeck
import com.example.ui.theme.deckAccentPalette

/**
 * Interactive "Add Flashcard" form with deck selection (or quick new-deck creation),
 * Front & Back card inputs, optional hint, live character counts, and Firestore persistence actions.
 */
@Composable
fun AddFlashcardForm(
    decks: List<StudyDeck>,
    initialDeckId: String? = null,
    isSaving: Boolean = false,
    onSaveFlashcard: (
        selectedDeck: StudyDeck?,
        newDeckTitle: String,
        newDeckSubject: String,
        front: String,
        back: String,
        hint: String,
        addAnother: Boolean,
        onClearInputs: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDeckId by rememberSaveable(initialDeckId) {
        mutableStateOf(initialDeckId)
    }
    var creatingNewDeck by rememberSaveable { mutableStateOf(false) }
    var newDeckTitle by rememberSaveable { mutableStateOf("") }
    var newDeckSubject by rememberSaveable { mutableStateOf("") }

    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var hint by rememberSaveable { mutableStateOf("") }

    // Automatically select the preselected deck or first available deck when decks load
    LaunchedEffect(decks, initialDeckId) {
        if (!creatingNewDeck) {
            val resolved = when {
                initialDeckId != null && decks.any { it.id == initialDeckId } -> initialDeckId
                selectedDeckId != null && decks.any { it.id == selectedDeckId } -> selectedDeckId
                decks.isNotEmpty() -> decks.first().id
                else -> null
            }
            selectedDeckId = resolved
            if (decks.isEmpty()) {
                creatingNewDeck = true
            }
        }
    }

    val selectedDeck = remember(decks, selectedDeckId, creatingNewDeck) {
        if (creatingNewDeck) null else decks.firstOrNull { it.id == selectedDeckId }
    }

    val isDeckValid = if (selectedDeck != null) {
        true
    } else {
        newDeckTitle.trim().isNotEmpty()
    }
    val isCardValid = front.trim().isNotEmpty() && back.trim().isNotEmpty()
    val canSubmit = isDeckValid && isCardValid && !isSaving

    val isDark = isSystemInDarkTheme()
    val activeAccent = deckAccentPalette(selectedDeck?.accentColorKey ?: "INDIGO", isDark)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Deck Collection Picker Card
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
                        tint = activeAccent.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Destination Firestore Deck",
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
                                        text = "${deck.title} (${deck.flashcardCount})",
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
                                modifier = Modifier.testTag("select_deck_chip_${deck.id}"),
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
                            modifier = Modifier.testTag("select_new_deck_chip"),
                        )
                    }
                }

                if (creatingNewDeck || decks.isEmpty()) {
                    Text(
                        text = if (decks.isEmpty()) {
                            "Create your first deck below to save flashcards to your cloud account:"
                        } else {
                            "Create a new deck in your account:"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newDeckTitle,
                        onValueChange = { if (it.length <= 100) newDeckTitle = it },
                        label = { Text("New Deck Title") },
                        placeholder = { Text("e.g., Biology 101") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_deck_title_input"),
                    )
                    OutlinedTextField(
                        value = newDeckSubject,
                        onValueChange = { if (it.length <= 50) newDeckSubject = it },
                        label = { Text("Subject (optional)") },
                        placeholder = { Text("e.g., Science") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_deck_subject_input"),
                    )
                }
            }
        }

        // Front Side Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, activeAccent.accent.copy(alpha = 0.5f)),
            shape = MaterialTheme.shapes.large,
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
                    Surface(
                        color = activeAccent.container,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = "FRONT SIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = activeAccent.onContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        text = "${front.length}/300",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = front,
                    onValueChange = { if (it.length <= 300) front = it },
                    label = { Text(stringResource(R.string.add_flashcard_front_label)) },
                    placeholder = { Text(stringResource(R.string.add_flashcard_front_placeholder)) },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_flashcard_front_input"),
                )
            }
        }

        // Back Side Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
            shape = MaterialTheme.shapes.large,
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
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = "BACK SIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Text(
                        text = "${back.length}/500",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = back,
                    onValueChange = { if (it.length <= 500) back = it },
                    label = { Text(stringResource(R.string.add_flashcard_back_label)) },
                    placeholder = { Text(stringResource(R.string.add_flashcard_back_placeholder)) },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_flashcard_back_input"),
                )

                OutlinedTextField(
                    value = hint,
                    onValueChange = { if (it.length <= 150) hint = it },
                    label = { Text(stringResource(R.string.add_flashcard_hint_label)) },
                    placeholder = { Text(stringResource(R.string.add_flashcard_hint_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_flashcard_hint_input"),
                )
            }
        }

        // Action Buttons
        val clearInputs = {
            front = ""
            back = ""
            hint = ""
            if (creatingNewDeck) {
                creatingNewDeck = false
                newDeckTitle = ""
                newDeckSubject = ""
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    onSaveFlashcard(
                        selectedDeck,
                        newDeckTitle,
                        newDeckSubject,
                        front,
                        back,
                        hint,
                        false,
                        clearInputs,
                    )
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_flashcard_submit_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_flashcard_save))
                }
            }

            OutlinedButton(
                onClick = {
                    onSaveFlashcard(
                        selectedDeck,
                        newDeckTitle,
                        newDeckSubject,
                        front,
                        back,
                        hint,
                        true,
                        clearInputs,
                    )
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_and_add_another_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_flashcard_save_another))
            }
        }
    }
}
