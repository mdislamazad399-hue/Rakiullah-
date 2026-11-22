package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.unwrapFirestoreThrowable
import com.example.data.media.StudyVideoStorage
import com.example.data.model.DeckAccent
import com.example.data.model.DeckProgress
import com.example.data.model.Flashcard
import com.example.data.model.LiveItemType
import com.example.data.model.LiveSession
import com.example.data.model.QuizQuestion
import com.example.data.model.StudyDeck
import com.example.data.model.StudyVideo
import com.example.data.model.UiState
import com.example.data.repository.StudyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DeckDetailViewModel(
    private val repository: StudyRepository,
    private val currentUserId: String,
    private val deckId: String,
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val deck: StateFlow<UiState<StudyDeck?>> =
        repository.observeDeck(deckId)
            .map<StudyDeck?, UiState<StudyDeck?>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreThrowable(error)
                Log.w(TAG, "Error observing deck", root)
                emit(UiState.Error(root.message ?: "Failed to load deck"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val flashcards: StateFlow<UiState<List<Flashcard>>> =
        deck.flatMapLatest { state ->
            when (state) {
                is UiState.Success -> {
                    val d = state.data
                    if (d == null) flowOf(UiState.Success(emptyList()))
                    else repository.observeFlashcards(deckId, d.teacherId, currentUserId)
                        .map<List<Flashcard>, UiState<List<Flashcard>>> { UiState.Success(it) }
                        .catch { error ->
                            val root = unwrapFirestoreThrowable(error)
                            Log.w(TAG, "Error observing flashcards", root)
                            emit(UiState.Error(root.message ?: "Failed to load flashcards"))
                        }
                }
                is UiState.Error -> flowOf(UiState.Error(state.message))
                UiState.Loading -> flowOf(UiState.Loading)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val questions: StateFlow<UiState<List<QuizQuestion>>> =
        deck.flatMapLatest { state ->
            when (state) {
                is UiState.Success -> {
                    val d = state.data
                    if (d == null) flowOf(UiState.Success(emptyList()))
                    else repository.observeQuestions(deckId, d.teacherId, currentUserId)
                        .map<List<QuizQuestion>, UiState<List<QuizQuestion>>> { UiState.Success(it) }
                        .catch { error ->
                            val root = unwrapFirestoreThrowable(error)
                            Log.w(TAG, "Error observing questions", root)
                            emit(UiState.Error(root.message ?: "Failed to load quiz questions"))
                        }
                }
                is UiState.Error -> flowOf(UiState.Error(state.message))
                UiState.Loading -> flowOf(UiState.Loading)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val videos: StateFlow<UiState<List<StudyVideo>>> =
        deck.flatMapLatest { state ->
            when (state) {
                is UiState.Success -> {
                    val d = state.data
                    if (d == null) flowOf(UiState.Success(emptyList()))
                    else repository.observeVideos(deckId, d.teacherId, currentUserId)
                        .map<List<StudyVideo>, UiState<List<StudyVideo>>> { UiState.Success(it) }
                        .catch { error ->
                            val root = unwrapFirestoreThrowable(error)
                            Log.w(TAG, "Error observing study videos", root)
                            emit(UiState.Error(root.message ?: "Failed to load study videos"))
                        }
                }
                is UiState.Error -> flowOf(UiState.Error(state.message))
                UiState.Loading -> flowOf(UiState.Loading)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val myProgress: StateFlow<UiState<DeckProgress?>> =
        repository.observeStudentDeckProgress(deckId, currentUserId)
            .map<DeckProgress?, UiState<DeckProgress?>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreThrowable(error)
                Log.w(TAG, "Error observing student progress", root)
                emit(UiState.Error(root.message ?: "Failed to load progress"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val classProgress: StateFlow<UiState<List<DeckProgress>>> =
        deck.flatMapLatest { state ->
            when (state) {
                is UiState.Success -> {
                    val d = state.data
                    if (d == null || d.teacherId != currentUserId) {
                        flowOf(UiState.Success(emptyList()))
                    } else {
                        repository.observeClassProgress(deckId, currentUserId)
                            .map<List<DeckProgress>, UiState<List<DeckProgress>>> { UiState.Success(it) }
                            .catch { error ->
                                val root = unwrapFirestoreThrowable(error)
                                Log.w(TAG, "Error observing class progress", root)
                                emit(UiState.Error(root.message ?: "Failed to load class progress"))
                            }
                    }
                }
                is UiState.Error -> flowOf(UiState.Error(state.message))
                UiState.Loading -> flowOf(UiState.Loading)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val liveSession: StateFlow<UiState<LiveSession?>> =
        repository.observeLiveSession(deckId)
            .map<LiveSession?, UiState<LiveSession?>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreThrowable(error)
                Log.w(TAG, "Error observing live session", root)
                emit(UiState.Error(root.message ?: "Failed to load live class status"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    fun updateDeck(
        title: String,
        subject: String,
        description: String,
        accent: DeckAccent,
        published: Boolean,
        previousPublished: Boolean,
    ) {
        viewModelScope.launch {
            repository.updateDeck(deckId, title, subject, description, accent, published, previousPublished)
                .onSuccess { _events.emit("Deck updated.") }
                .onFailure { e -> _events.emit(e.message ?: "Failed to update deck.") }
        }
    }

    fun addFlashcard(front: String, back: String, hint: String, published: Boolean, nextOrder: Int) {
        viewModelScope.launch {
            repository.addFlashcard(deckId, front, back, hint, published, nextOrder)
                .onSuccess { _events.emit("Flashcard added.") }
                .onFailure { e -> _events.emit(e.message ?: "Failed to add flashcard.") }
        }
    }

    fun updateFlashcard(cardId: String, front: String, back: String, hint: String, orderIndex: Int) {
        viewModelScope.launch {
            repository.updateFlashcard(deckId, cardId, front, back, hint, orderIndex)
                .onSuccess { _events.emit("Flashcard updated.") }
                .onFailure { e -> _events.emit(e.message ?: "Failed to update flashcard.") }
        }
    }

    fun deleteFlashcard(cardId: String, currentCount: Long) {
        viewModelScope.launch {
            repository.deleteFlashcard(deckId, cardId, currentCount)
                .onSuccess { _events.emit("Flashcard removed.") }
                .onFailure { e -> _events.emit(e.message ?: "Failed to remove flashcard.") }
        }
    }

    fun addQuestion(
        prompt: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
        published: Boolean,
        nextOrder: Int,
    ) {
        viewModelScope.launch {
            repository.addQuestion(deckId, prompt, options, correctIndex, explanation, published, nextOrder)
                .onSuccess { _events.emit("Quiz question added.") }
                .onFailure { e -> _events.emit(e.message ?: "Failed to add question.") }
        }
    }

    fun updateQuestion(
        questionId: String,
        prompt: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
        orderIndex: Int,
    ) {
        viewModelScope.launch {
            repository.updateQuestion(deckId, questionId, prompt, options, correctIndex, explanation, orderIndex)
                .onSuccess { _events.emit("Question updated.") }
                .onFailure { e -> _events.emit(e.message ?: "Failed to update question.") }
        }
    }

    fun deleteQuestion(questionId: String, currentCount: Long) {
        viewModelScope.launch {
            repository.deleteQuestion(deckId, questionId, currentCount)
                .onSuccess { _events.emit("Question removed.") }
                .onFailure { e -> _events.emit(e.message ?: "Failed to remove question.") }
        }
    }

    fun recordFlashcardSession(
        deck: StudyDeck,
        masteredCardIds: List<String>,
        reviewedCount: Int,
        knownCount: Int,
        totalCardsInDeck: Int,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.recordFlashcardSession(deck, masteredCardIds, reviewedCount, knownCount, totalCardsInDeck)
                .onSuccess { onSaved() }
                .onFailure { e -> _events.emit(e.message ?: "Failed to save study session.") }
        }
    }

    fun recordQuizResult(
        deck: StudyDeck,
        correctCount: Int,
        totalCount: Int,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.recordQuizResult(deck, correctCount, totalCount)
                .onSuccess { onSaved() }
                .onFailure { e -> _events.emit(e.message ?: "Failed to save quiz score.") }
        }
    }

    fun updateLiveSession(
        deck: StudyDeck,
        active: Boolean,
        itemType: LiveItemType,
        itemIndex: Int,
        activePrompt: String,
    ) {
        viewModelScope.launch {
            repository.updateLiveSession(deck, active, itemType, itemIndex, activePrompt)
                .onFailure { e -> _events.emit(e.message ?: "Could not update live class status.") }
        }
    }

    fun deleteVideo(context: Context, video: StudyVideo) {
        viewModelScope.launch {
            repository.deleteVideo(deckId, video.id)
                .onSuccess {
                    StudyVideoStorage.deleteVideoFile(context, video.fileName)
                    _events.emit("Study video removed.")
                }
                .onFailure { e -> _events.emit(e.message ?: "Failed to remove study video.") }
        }
    }

    private companion object {
        const val TAG = "DeckDetailVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
