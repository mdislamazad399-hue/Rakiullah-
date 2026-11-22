package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.example.data.model.DeckAccent
import com.example.data.model.Flashcard
import com.example.data.model.QuizQuestion
import com.example.data.model.StudyDeck
import com.example.ui.theme.deckAccentPalette

@Composable
fun CreateOrEditDeckDialog(
    initialDeck: StudyDeck? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, subject: String, description: String, accent: DeckAccent, published: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(initialDeck?.title ?: "") }
    var subject by remember { mutableStateOf(initialDeck?.subject ?: "") }
    var description by remember { mutableStateOf(initialDeck?.description ?: "") }
    var accent by remember { mutableStateOf(DeckAccent.fromRaw(initialDeck?.accentColorKey)) }
    var published by remember { mutableStateOf(initialDeck?.published ?: true) }
    val isDark = isSystemInDarkTheme()

    val isValid = title.trim().isNotEmpty() && subject.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialDeck == null) "Create Study Deck" else "Edit Study Deck",
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("Deck Title") },
                    placeholder = { Text("e.g., Cellular Respiration") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("deck_title_input"),
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { if (it.length <= 50) subject = it },
                    label = { Text("Subject") },
                    placeholder = { Text("e.g., Biology, Calculus, History") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("deck_subject_input"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 300) description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("What will students master?") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("deck_desc_input"),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Deck Color Theme",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeckAccent.entries.forEach { option ->
                            val palette = deckAccentPalette(option.name, isDark)
                            val selected = option == accent
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                                    .clickable { accent = option },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
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
                            text = "Publish to Students",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Students can join via code and study",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = published,
                        onCheckedChange = { published = it },
                        modifier = Modifier.testTag("deck_publish_switch"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, subject, description, accent, published) },
                enabled = isValid,
                modifier = Modifier.testTag("save_deck_button"),
            ) {
                Text(if (initialDeck == null) "Create Deck" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun FlashcardEditorDialog(
    initialCard: Flashcard? = null,
    onDismiss: () -> Unit,
    onSave: (front: String, back: String, hint: String) -> Unit,
) {
    var front by remember { mutableStateOf(initialCard?.front ?: "") }
    var back by remember { mutableStateOf(initialCard?.back ?: "") }
    var hint by remember { mutableStateOf(initialCard?.hint ?: "") }

    val isValid = front.trim().isNotEmpty() && back.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCard == null) "Add Flashcard" else "Edit Flashcard",
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = front,
                    onValueChange = { if (it.length <= 300) front = it },
                    label = { Text("Front (Term or Question)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("flashcard_front_input"),
                )
                OutlinedTextField(
                    value = back,
                    onValueChange = { if (it.length <= 500) back = it },
                    label = { Text("Back (Definition or Answer)") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("flashcard_back_input"),
                )
                OutlinedTextField(
                    value = hint,
                    onValueChange = { if (it.length <= 150) hint = it },
                    label = { Text("Study Hint (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("flashcard_hint_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(front, back, hint) },
                enabled = isValid,
                modifier = Modifier.testTag("save_flashcard_button"),
            ) {
                Text(if (initialCard == null) "Add Card" else "Update Card")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun QuestionEditorDialog(
    initialQuestion: QuizQuestion? = null,
    onDismiss: () -> Unit,
    onSave: (prompt: String, options: List<String>, correctIndex: Int, explanation: String) -> Unit,
) {
    var prompt by remember { mutableStateOf(initialQuestion?.prompt ?: "") }
    var opt0 by remember { mutableStateOf(initialQuestion?.options?.getOrNull(0) ?: "") }
    var opt1 by remember { mutableStateOf(initialQuestion?.options?.getOrNull(1) ?: "") }
    var opt2 by remember { mutableStateOf(initialQuestion?.options?.getOrNull(2) ?: "") }
    var opt3 by remember { mutableStateOf(initialQuestion?.options?.getOrNull(3) ?: "") }
    var correctIndex by remember {
        mutableIntStateOf((initialQuestion?.correctIndex ?: 0L).toInt().coerceIn(0, 3))
    }
    var explanation by remember { mutableStateOf(initialQuestion?.explanation ?: "") }

    val rawOptions = listOf(opt0, opt1, opt2, opt3)
    val nonEmptyOptions = rawOptions.mapIndexedNotNull { idx, text ->
        if (text.trim().isNotEmpty()) idx to text.trim() else null
    }
    val mappedCorrectIdx = nonEmptyOptions.indexOfFirst { it.first == correctIndex }
    val isValid = prompt.trim().isNotEmpty() && nonEmptyOptions.size >= 2 && mappedCorrectIdx >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialQuestion == null) "Add Quiz Question" else "Edit Quiz Question",
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { if (it.length <= 300) prompt = it },
                    label = { Text("Question Prompt") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("question_prompt_input"),
                )
                Text(
                    text = "Answer Options (select the correct one)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                val labels = listOf("Option A (required)", "Option B (required)", "Option C (optional)", "Option D (optional)")
                val setters = listOf<(String) -> Unit>(
                    { if (it.length <= 150) opt0 = it },
                    { if (it.length <= 150) opt1 = it },
                    { if (it.length <= 150) opt2 = it },
                    { if (it.length <= 150) opt3 = it },
                )
                rawOptions.forEachIndexed { idx, value ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = correctIndex == idx,
                            onClick = { correctIndex = idx },
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = setters[idx],
                            label = { Text(labels[idx]) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("question_option_${idx}_input"),
                        )
                    }
                }
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { if (it.length <= 400) explanation = it },
                    label = { Text("Teacher Explanation (shown after answer)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("question_explanation_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalOptions = nonEmptyOptions.map { it.second }
                    onSave(prompt, finalOptions, mappedCorrectIdx, explanation)
                },
                enabled = isValid,
                modifier = Modifier.testTag("save_question_button"),
            ) {
                Text(if (initialQuestion == null) "Add Question" else "Update Question")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun JoinDeckDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val cleanCode = code.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Join Class Deck",
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the 6-character code shared by your instructor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 12) code = it.uppercase() },
                    label = { Text("Join Code") },
                    placeholder = { Text("e.g., BIO4K9") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("join_dialog_input"),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Spacer(modifier = Modifier.width(0.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(cleanCode) },
                enabled = cleanCode.length in 4..12,
                modifier = Modifier.testTag("join_dialog_confirm"),
            ) {
                Text("Join Deck")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
