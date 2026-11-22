package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Enrollment
import com.example.data.model.Flashcard
import com.example.data.model.StudyDeck
import com.example.data.model.UserProfile
import com.example.data.repository.StudyRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class CoachMessage(
    val id: String = UUID.randomUUID().toString(),
    val isFromUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false,
)

data class DeckFlashcardContext(
    val deckId: String,
    val title: String,
    val subject: String,
    val cards: List<Flashcard>,
)

data class CoachDeckOption(
    val deckId: String,
    val title: String,
    val subject: String,
    val isOwner: Boolean,
)

sealed interface AiCoachUiState {
    data object Idle : AiCoachUiState
    data object Streaming : AiCoachUiState
    data class Error(val message: String) : AiCoachUiState
}

class AiCoachViewModel(
    private val repository: StudyRepository,
    private val currentUserId: String,
) : ViewModel() {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are StudyDeck AI Coach, an encouraging, crystal-clear personal tutor for students. " +
                        "You answer student questions based directly on their actual flashcard content (Front, Back, and Hint) provided in the study context. " +
                        "When explaining an answer or concept, reference the specific flashcard term or deck name when relevant, use simple analogies, " +
                        "memory tricks, and active-recall practice questions. " +
                        "If a student asks something not covered by their loaded flashcards, briefly note what is or isn't in their flashcards and still help them clearly. " +
                        "Always reply in the same language the user writes in (e.g., English, Hindi, or Hinglish). " +
                        "Keep responses focused, structured with short bullets when helpful, and directly actionable."
                )
            },
        )
    }

    private var chatSession: Chat? = null
    private var contextKeyLoaded: String? = null
    private var loadedDeckContexts: List<DeckFlashcardContext> = emptyList()

    private val _messages = MutableStateFlow<List<CoachMessage>>(emptyList())
    val messages: StateFlow<List<CoachMessage>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<AiCoachUiState>(AiCoachUiState.Idle)
    val uiState: StateFlow<AiCoachUiState> = _uiState.asStateFlow()

    private val _selectedDeckId = MutableStateFlow<String?>(null)
    val selectedDeckId: StateFlow<String?> = _selectedDeckId.asStateFlow()

    private val _loadedFlashcardCount = MutableStateFlow(0)
    val loadedFlashcardCount: StateFlow<Int> = _loadedFlashcardCount.asStateFlow()

    private val _isLoadingFlashcards = MutableStateFlow(false)
    val isLoadingFlashcards: StateFlow<Boolean> = _isLoadingFlashcards.asStateFlow()

    private val _flashcardContextError = MutableStateFlow<String?>(null)
    val flashcardContextError: StateFlow<String?> = _flashcardContextError.asStateFlow()

    fun selectDeck(
        deckId: String?,
        deckOptions: List<CoachDeckOption>,
    ) {
        if (_selectedDeckId.value == deckId && contextKeyLoaded != null) return
        _selectedDeckId.value = deckId
        chatSession = null
        refreshFlashcardContext(deckOptions, force = true)
    }

    fun syncAvailableDecks(deckOptions: List<CoachDeckOption>) {
        val targetId = _selectedDeckId.value
        val newKey = buildContextKey(targetId, deckOptions)
        if (newKey != contextKeyLoaded && !_isLoadingFlashcards.value && _uiState.value != AiCoachUiState.Streaming) {
            refreshFlashcardContext(deckOptions, force = false)
        }
    }

    fun refreshFlashcardContext(
        deckOptions: List<CoachDeckOption>,
        force: Boolean = true,
    ) {
        val targetId = _selectedDeckId.value
        val key = buildContextKey(targetId, deckOptions)
        if (!force && key == contextKeyLoaded) return

        viewModelScope.launch {
            loadFlashcardsInternal(targetId, deckOptions, key)
        }
    }

    private suspend fun loadFlashcardsInternal(
        targetDeckId: String?,
        deckOptions: List<CoachDeckOption>,
        key: String,
    ) {
        _isLoadingFlashcards.value = true
        _flashcardContextError.value = null
        try {
            val targets = if (targetDeckId != null) {
                deckOptions.filter { it.deckId == targetDeckId }
            } else {
                deckOptions.take(6)
            }

            val contexts = mutableListOf<DeckFlashcardContext>()
            var totalCards = 0
            var firstError: String? = null

            for (option in targets) {
                if (totalCards >= MAX_TOTAL_CARDS) break
                val result = repository.fetchFlashcardsForDeck(option.deckId, option.isOwner)
                result.onSuccess { cards ->
                    val remaining = (MAX_TOTAL_CARDS - totalCards).coerceAtLeast(0)
                    val trimmed = cards.take(remaining)
                    totalCards += trimmed.size
                    contexts.add(
                        DeckFlashcardContext(
                            deckId = option.deckId,
                            title = option.title,
                            subject = option.subject,
                            cards = trimmed,
                        )
                    )
                }.onFailure { err ->
                    if (firstError == null) {
                        firstError = err.message ?: "Failed to load flashcards"
                    }
                }
            }

            loadedDeckContexts = contexts
            _loadedFlashcardCount.value = totalCards
            contextKeyLoaded = key
            if (contexts.isEmpty() && firstError != null) {
                _flashcardContextError.value = firstError
            }
            if (_messages.value.isEmpty()) {
                chatSession = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading flashcard context", e)
            _flashcardContextError.value = e.localizedMessage ?: "Failed to load flashcards"
        } finally {
            _isLoadingFlashcards.value = false
        }
    }

    fun sendMessage(
        rawPrompt: String,
        profile: UserProfile? = null,
        teacherDecks: List<StudyDeck> = emptyList(),
        enrollments: List<Enrollment> = emptyList(),
        deckOptions: List<CoachDeckOption> = emptyList(),
    ) {
        val cleanPrompt = rawPrompt.trim()
        if (cleanPrompt.isEmpty() || _uiState.value == AiCoachUiState.Streaming) return

        val userMsg = CoachMessage(isFromUser = true, text = cleanPrompt)
        val aiPlaceholderId = UUID.randomUUID().toString()
        val aiPlaceholder = CoachMessage(
            id = aiPlaceholderId,
            isFromUser = false,
            text = "",
            isStreaming = true,
        )

        _messages.update { it + userMsg + aiPlaceholder }
        _uiState.value = AiCoachUiState.Streaming

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                val targetId = _selectedDeckId.value
                val expectedKey = buildContextKey(targetId, deckOptions)
                if (contextKeyLoaded != expectedKey && deckOptions.isNotEmpty()) {
                    loadFlashcardsInternal(targetId, deckOptions, expectedKey)
                }

                val session = chatSession ?: run {
                    val contextSummary = buildStudyContext(profile, teacherDecks, enrollments, loadedDeckContexts)
                    val initialHistory = if (contextSummary.isNotBlank()) {
                        listOf(
                            content("user") {
                                text(
                                    "Here is my StudyDeck profile and the exact flashcards I am studying:\n\n$contextSummary\n\n" +
                                        "Use these flashcards as our primary study reference."
                                )
                            },
                            content("model") {
                                text(
                                    "Got it! I've reviewed your StudyDeck flashcards and study progress. " +
                                        "Ask me anything about these cards, request a quick practice quiz, or ask for simpler explanations!"
                                )
                            },
                        )
                    } else {
                        emptyList()
                    }
                    model.startChat(history = initialHistory).also { chatSession = it }
                }

                withTimeout(60_000L) {
                    session.sendMessageStream(cleanPrompt).collect { chunk ->
                        val part = chunk.text.orEmpty()
                        if (part.isNotEmpty()) {
                            accumulated.append(part)
                            val snapshot = accumulated.toString()
                            _messages.update { list ->
                                list.map { msg ->
                                    if (msg.id == aiPlaceholderId) msg.copy(text = snapshot) else msg
                                }
                            }
                        }
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isEmpty()) {
                    removeMessage(aiPlaceholderId)
                    _uiState.value = AiCoachUiState.Error("AI Coach returned an empty response. Please try again.")
                } else {
                    _messages.update { list ->
                        list.map { msg ->
                            if (msg.id == aiPlaceholderId) msg.copy(text = finalReply, isStreaming = false) else msg
                        }
                    }
                    _uiState.value = AiCoachUiState.Idle
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI Coach response timed out", e)
                removeOrFinalize(aiPlaceholderId, accumulated.toString())
                _uiState.value = AiCoachUiState.Error("AI Coach timed out after 60 seconds. Please check your connection and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Coach generation failed", e)
                removeOrFinalize(aiPlaceholderId, accumulated.toString())
                _uiState.value = AiCoachUiState.Error(e.localizedMessage ?: "AI Coach failed to respond.")
            }
        }
    }

    fun clearChat() {
        chatSession = null
        _messages.value = emptyList()
        _uiState.value = AiCoachUiState.Idle
    }

    fun dismissError() {
        if (_uiState.value is AiCoachUiState.Error) {
            _uiState.value = AiCoachUiState.Idle
        }
    }

    private fun removeMessage(id: String) {
        _messages.update { list -> list.filterNot { it.id == id } }
    }

    private fun removeOrFinalize(id: String, partialText: String) {
        if (partialText.isBlank()) {
            removeMessage(id)
        } else {
            _messages.update { list ->
                list.map { msg ->
                    if (msg.id == id) msg.copy(text = partialText.trim(), isStreaming = false) else msg
                }
            }
        }
    }

    private fun buildContextKey(targetDeckId: String?, deckOptions: List<CoachDeckOption>): String {
        val deckSignature = deckOptions.joinToString(",") { "${it.deckId}:${it.isOwner}" }
        return "${currentUserId}|${targetDeckId ?: "ALL"}|$deckSignature"
    }

    private fun buildStudyContext(
        profile: UserProfile?,
        teacherDecks: List<StudyDeck>,
        enrollments: List<Enrollment>,
        deckContexts: List<DeckFlashcardContext>,
    ): String {
        val parts = mutableListOf<String>()
        profile?.let {
            parts.add("Learner: ${it.displayName}, XP=${it.totalXp}, Quizzes=${it.quizzesCompleted}, CardsReviewed=${it.cardsReviewed}")
        }
        if (enrollments.isNotEmpty()) {
            val summary = enrollments.take(6).joinToString("; ") {
                "${it.deckTitle} (${it.subject}, mastered ${it.masteredCount}/${it.totalCards}, best quiz ${it.bestQuizPercent}%)"
            }
            parts.add("Enrolled decks: $summary")
        }
        if (teacherDecks.isNotEmpty()) {
            val summary = teacherDecks.take(6).joinToString("; ") {
                "${it.title} (${it.subject}, ${it.flashcardCount} cards, ${it.quizCount} quiz Qs)"
            }
            parts.add("Created decks: $summary")
        }

        val decksWithCards = deckContexts.filter { it.cards.isNotEmpty() }
        if (decksWithCards.isNotEmpty()) {
            parts.add("--- FLASHCARD CONTENT ---")
            decksWithCards.forEach { deckCtx ->
                parts.add("Deck: \"${deckCtx.title}\" (${deckCtx.subject})")
                deckCtx.cards.forEachIndexed { idx, card ->
                    val hintPart = if (card.hint.isNotBlank()) " | Hint: ${card.hint}" else ""
                    parts.add("  Card ${idx + 1}: Q/Front: ${card.front} | A/Back: ${card.back}$hintPart")
                }
            }
        }
        return parts.joinToString("\n")
    }

    private companion object {
        const val TAG = "AiCoachVM"
        const val MAX_TOTAL_CARDS = 60
    }
}
