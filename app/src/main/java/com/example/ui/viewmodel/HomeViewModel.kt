package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.unwrapFirestoreThrowable
import com.example.data.media.StudyVideoStorage
import com.example.data.model.BatchEnrollment
import com.example.data.model.DeckAccent
import com.example.data.model.Enrollment
import com.example.data.model.StudyBatch
import com.example.data.model.StudyDeck
import com.example.data.model.StudyLog
import com.example.data.model.StudyWallet
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.WalletTransaction
import com.example.data.repository.StudyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: StudyRepository,
    private val currentUserId: String,
    private val initialDisplayName: String?,
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val retryTrigger = MutableStateFlow(0)

    private val _isSavingFlashcard = MutableStateFlow(false)
    val isSavingFlashcard: StateFlow<Boolean> = _isSavingFlashcard.asStateFlow()

    private val _uploadVideoProgress = MutableStateFlow<Float?>(null)
    val uploadVideoProgress: StateFlow<Float?> = _uploadVideoProgress.asStateFlow()

    private val _isSavingBatch = MutableStateFlow(false)
    val isSavingBatch: StateFlow<Boolean> = _isSavingBatch.asStateFlow()

    private val _isProcessingPayment = MutableStateFlow(false)
    val isProcessingPayment: StateFlow<Boolean> = _isProcessingPayment.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, initialDisplayName)
        }
    }

    fun retryAll() {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, initialDisplayName)
        }
        retryTrigger.value += 1
    }

    private fun <T> Flow<T>.toUiStateFlow(errorLabel: String): StateFlow<UiState<T>> =
        retryTrigger.flatMapLatest {
            this
                .map<T, UiState<T>> { UiState.Success(it) }
                .onStart { emit(UiState.Loading) }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    Log.w(TAG, errorLabel, root)
                    emit(UiState.Error(root.message ?: errorLabel))
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val userProfile: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiStateFlow("Failed to load profile")

    val teacherDecks: StateFlow<UiState<List<StudyDeck>>> =
        repository.observeTeacherDecks(currentUserId)
            .toUiStateFlow("Failed to load teacher decks")

    val publishedDecks: StateFlow<UiState<List<StudyDeck>>> =
        repository.observePublishedDecks()
            .toUiStateFlow("Failed to load study catalog")

    val enrollments: StateFlow<UiState<List<Enrollment>>> =
        repository.observeEnrollments(currentUserId)
            .toUiStateFlow("Failed to load enrolled decks")

    val studyLogs: StateFlow<UiState<List<StudyLog>>> =
        repository.observeStudyLogs(currentUserId)
            .toUiStateFlow("Failed to load study logs")

    val wallet: StateFlow<UiState<StudyWallet?>> =
        repository.observeWallet(currentUserId)
            .toUiStateFlow("Failed to load wallet")

    val transactions: StateFlow<UiState<List<WalletTransaction>>> =
        repository.observeTransactions(currentUserId)
            .toUiStateFlow("Failed to load transactions")

    val teacherBatches: StateFlow<UiState<List<StudyBatch>>> =
        repository.observeTeacherBatches(currentUserId)
            .toUiStateFlow("Failed to load your batches")

    val publishedBatches: StateFlow<UiState<List<StudyBatch>>> =
        repository.observePublishedBatches()
            .toUiStateFlow("Failed to load batches")

    val batchEnrollments: StateFlow<UiState<List<BatchEnrollment>>> =
        repository.observeBatchEnrollments(currentUserId)
            .toUiStateFlow("Failed to load batch enrollments")

    fun createDeck(
        title: String,
        subject: String,
        description: String,
        accent: DeckAccent,
        published: Boolean,
        onCreated: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.createDeck(title, subject, description, accent, published)
                .onSuccess { deckId ->
                    _events.emit("Deck created! Add flashcards and quiz questions.")
                    onCreated(deckId)
                }
                .onFailure { e ->
                    _events.emit(e.message ?: "Could not create deck.")
                }
        }
    }

    fun saveFlashcard(
        selectedDeck: StudyDeck?,
        newDeckTitle: String,
        newDeckSubject: String,
        front: String,
        back: String,
        hint: String,
        onSaved: (deckId: String) -> Unit = {},
    ) {
        if (_isSavingFlashcard.value) return
        viewModelScope.launch {
            _isSavingFlashcard.value = true
            try {
                val targetDeckId: String
                val targetPublished: Boolean
                val targetOrder: Int

                if (selectedDeck != null) {
                    targetDeckId = selectedDeck.id
                    targetPublished = selectedDeck.published
                    targetOrder = selectedDeck.flashcardCount.toInt().coerceAtLeast(0)
                } else {
                    val cleanTitle = newDeckTitle.trim().ifEmpty { "My Flashcards" }
                    val cleanSubject = newDeckSubject.trim().ifEmpty { "General" }
                    val createdDeckResult = repository.createDeck(
                        title = cleanTitle,
                        subject = cleanSubject,
                        description = "Flashcard deck created with Add Flashcard",
                        accent = DeckAccent.INDIGO,
                        published = true,
                    )
                    targetDeckId = createdDeckResult.getOrElse { error ->
                        _events.emit(error.message ?: "Could not create flashcard deck.")
                        return@launch
                    }
                    targetPublished = true
                    targetOrder = 0
                }

                repository.addFlashcard(
                    deckId = targetDeckId,
                    front = front,
                    back = back,
                    hint = hint,
                    published = targetPublished,
                    orderIndex = targetOrder,
                ).onSuccess {
                    _events.emit("Flashcard saved!")
                    onSaved(targetDeckId)
                }.onFailure { error ->
                    _events.emit(error.message ?: "Failed to save flashcard.")
                }
            } finally {
                _isSavingFlashcard.value = false
            }
        }
    }

    fun uploadStudyVideo(
        context: Context,
        videoUri: Uri,
        selectedDeck: StudyDeck?,
        newDeckTitle: String,
        newDeckSubject: String,
        title: String,
        notes: String,
        durationMs: Long,
        fileSizeHintBytes: Long,
        onUploaded: (deckId: String) -> Unit = {},
    ) {
        if (_uploadVideoProgress.value != null) return
        viewModelScope.launch {
            _uploadVideoProgress.value = 0.05f
            val videoId = "vid_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val fileName = "${videoId}.mp4"
            try {
                val destFile = StudyVideoStorage.importVideoFast(
                    context = context,
                    uri = videoUri,
                    targetFileName = fileName,
                    totalBytesHint = fileSizeHintBytes,
                    onProgress = { progress -> _uploadVideoProgress.value = progress },
                )

                val targetDeckId: String
                val targetPublished: Boolean
                if (selectedDeck != null) {
                    targetDeckId = selectedDeck.id
                    targetPublished = selectedDeck.published
                } else {
                    val cleanDeckTitle = newDeckTitle.trim().ifEmpty { "Study Videos" }
                    val cleanSubject = newDeckSubject.trim().ifEmpty { "General" }
                    targetDeckId = repository.createDeck(
                        title = cleanDeckTitle,
                        subject = cleanSubject,
                        description = "Recorded video lessons",
                        accent = DeckAccent.INDIGO,
                        published = true,
                    ).getOrElse { error ->
                        StudyVideoStorage.deleteVideoFile(context, fileName)
                        _events.emit(error.message ?: "Could not create deck for video.")
                        return@launch
                    }
                    targetPublished = true
                }

                val actualBytes = destFile.length().coerceAtLeast(fileSizeHintBytes)
                repository.addVideo(
                    deckId = targetDeckId,
                    videoId = videoId,
                    title = title,
                    notes = notes,
                    fileName = fileName,
                    durationMs = durationMs,
                    fileSizeBytes = actualBytes,
                    published = targetPublished,
                ).onSuccess {
                    _uploadVideoProgress.value = 1f
                    _events.emit("Study video uploaded!")
                    onUploaded(targetDeckId)
                }.onFailure { error ->
                    StudyVideoStorage.deleteVideoFile(context, fileName)
                    _events.emit(error.message ?: "Failed to save video details.")
                }
            } catch (e: Exception) {
                StudyVideoStorage.deleteVideoFile(context, fileName)
                _events.emit(e.message ?: "Video upload failed.")
            } finally {
                _uploadVideoProgress.value = null
            }
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
                .onSuccess { _events.emit("Deck deleted.") }
                .onFailure { e -> _events.emit(e.message ?: "Failed to delete deck.") }
        }
    }

    fun joinDeckByCode(code: String, onJoined: (StudyDeck) -> Unit = {}) {
        viewModelScope.launch {
            repository.joinDeckByCode(code)
                .onSuccess { deck ->
                    _events.emit("Joined \"${deck.title}\"!")
                    onJoined(deck)
                }
                .onFailure { e ->
                    _events.emit(e.message ?: "Could not join deck.")
                }
        }
    }

    fun enrollInDeck(deck: StudyDeck, onEnrolled: () -> Unit = {}) {
        viewModelScope.launch {
            repository.enrollInDeck(deck)
                .onSuccess { onEnrolled() }
                .onFailure { e -> _events.emit(e.message ?: "Could not open deck.") }
        }
    }

    fun topUpWallet(
        amountInr: Long,
        paymentMethod: String,
        onSuccess: () -> Unit = {},
    ) {
        if (_isProcessingPayment.value) return
        viewModelScope.launch {
            _isProcessingPayment.value = true
            try {
                repository.topUpWallet(amountInr, paymentMethod)
                    .onSuccess {
                        _events.emit("₹$amountInr added to your Study Wallet!")
                        onSuccess()
                    }
                    .onFailure { e ->
                        _events.emit(e.message ?: "Could not add money to wallet.")
                    }
            } finally {
                _isProcessingPayment.value = false
            }
        }
    }

    fun createBatch(
        title: String,
        subject: String,
        description: String,
        scheduleText: String,
        priceInr: Long,
        upiId: String,
        coverImageData: String,
        accent: DeckAccent,
        published: Boolean,
        onCreated: (String) -> Unit = {},
    ) {
        if (_isSavingBatch.value) return
        viewModelScope.launch {
            _isSavingBatch.value = true
            try {
                repository.createBatch(
                    title = title,
                    subject = subject,
                    description = description,
                    scheduleText = scheduleText,
                    priceInr = priceInr,
                    upiId = upiId,
                    coverImageData = coverImageData,
                    accent = accent,
                    published = published,
                ).onSuccess { batchId ->
                    _events.emit("Batch created!")
                    onCreated(batchId)
                }.onFailure { e ->
                    _events.emit(e.message ?: "Could not create batch.")
                }
            } finally {
                _isSavingBatch.value = false
            }
        }
    }

    fun updateBatch(
        batchId: String,
        title: String,
        subject: String,
        description: String,
        scheduleText: String,
        priceInr: Long,
        upiId: String,
        coverImageData: String,
        accent: DeckAccent,
        published: Boolean,
        onUpdated: () -> Unit = {},
    ) {
        if (_isSavingBatch.value) return
        viewModelScope.launch {
            _isSavingBatch.value = true
            try {
                repository.updateBatch(
                    batchId = batchId,
                    title = title,
                    subject = subject,
                    description = description,
                    scheduleText = scheduleText,
                    priceInr = priceInr,
                    upiId = upiId,
                    coverImageData = coverImageData,
                    accent = accent,
                    published = published,
                ).onSuccess {
                    _events.emit("Batch updated!")
                    onUpdated()
                }.onFailure { e ->
                    _events.emit(e.message ?: "Could not update batch.")
                }
            } finally {
                _isSavingBatch.value = false
            }
        }
    }

    fun deleteBatch(batchId: String) {
        viewModelScope.launch {
            repository.deleteBatch(batchId)
                .onSuccess { _events.emit("Batch deleted.") }
                .onFailure { e -> _events.emit(e.message ?: "Failed to delete batch.") }
        }
    }

    fun payAndEnrollBatch(
        batch: StudyBatch,
        paymentMethod: String = "WALLET",
        onEnrolled: () -> Unit = {},
    ) {
        if (_isProcessingPayment.value) return
        viewModelScope.launch {
            _isProcessingPayment.value = true
            try {
                repository.payAndEnrollBatch(batch, paymentMethod)
                    .onSuccess {
                        val msg = if (batch.priceInr > 0L) {
                            "Payment of ₹${batch.priceInr} successful! Enrolled in \"${batch.title}\"."
                        } else {
                            "Joined \"${batch.title}\"!"
                        }
                        _events.emit(msg)
                        onEnrolled()
                    }
                    .onFailure { e ->
                        _events.emit(e.message ?: "Could not complete batch enrollment.")
                    }
            } finally {
                _isProcessingPayment.value = false
            }
        }
    }

    fun findBatchByCode(code: String, onFound: (StudyBatch) -> Unit) {
        viewModelScope.launch {
            repository.findBatchByCode(code)
                .onSuccess { batch -> onFound(batch) }
                .onFailure { e -> _events.emit(e.message ?: "No batch found for code.") }
        }
    }

    private companion object {
        const val TAG = "HomeVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
