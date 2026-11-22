package com.example.data.repository

import com.example.data.model.BatchEnrollment
import com.example.data.model.BatchMember
import com.example.data.model.DeckAccent
import com.example.data.model.DeckProgress
import com.example.data.model.Enrollment
import com.example.data.model.Flashcard
import com.example.data.model.LiveItemType
import com.example.data.model.LiveSession
import com.example.data.model.QuizQuestion
import com.example.data.model.StudyBatch
import com.example.data.model.StudyDeck
import com.example.data.model.StudyLog
import com.example.data.model.StudyMode
import com.example.data.model.StudyVideo
import com.example.data.model.StudyWallet
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WalletTransaction
import com.example.data.model.WalletTxType
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

const val GUEST_USER_ID = "guest_local"

private data class GuestSessionData(
    val profile: UserProfile? = null,
    val decks: Map<String, StudyDeck> = emptyMap(),
    val flashcards: Map<String, List<Flashcard>> = emptyMap(),
    val questions: Map<String, List<QuizQuestion>> = emptyMap(),
    val videos: Map<String, List<StudyVideo>> = emptyMap(),
    val progress: Map<String, DeckProgress> = emptyMap(),
    val enrollments: Map<String, Enrollment> = emptyMap(),
    val studyLogs: List<StudyLog> = emptyList(),
    val liveSessions: Map<String, LiveSession> = emptyMap(),
    val wallet: StudyWallet? = null,
    val transactions: List<WalletTransaction> = emptyList(),
    val batches: Map<String, StudyBatch> = emptyMap(),
    val batchEnrollments: Map<String, BatchEnrollment> = emptyMap(),
    val batchMembers: Map<String, List<BatchMember>> = emptyMap(),
)

object GuestStudyStore {
    private val state = MutableStateFlow(GuestSessionData())

    private fun nextId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    private fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun guestName(): String =
        state.value.profile?.displayName?.takeIf { it.isNotBlank() } ?: "Guest Scholar"

    // --- STREAMS ---

    fun observeUserProfile(): Flow<UserProfile?> =
        state.map { it.profile }

    fun observeTeacherDecks(): Flow<List<StudyDeck>> =
        state.map { s ->
            s.decks.values.sortedByDescending { it.updatedAt ?: it.createdAt }
        }

    fun observePublishedDecks(): Flow<List<StudyDeck>> =
        state.map { s ->
            s.decks.values.filter { it.published }.sortedByDescending { it.updatedAt ?: it.createdAt }
        }

    fun observeEnrollments(): Flow<List<Enrollment>> =
        state.map { s ->
            s.enrollments.values.sortedByDescending { it.updatedAt ?: it.createdAt }
        }

    fun observeStudyLogs(): Flow<List<StudyLog>> =
        state.map { s ->
            s.studyLogs.sortedByDescending { it.createdAt }
        }

    fun observeDeck(deckId: String): Flow<StudyDeck?> =
        state.map { it.decks[deckId] }

    fun observeFlashcards(deckId: String): Flow<List<Flashcard>> =
        state.map { s ->
            s.flashcards[deckId].orEmpty()
                .sortedWith(compareBy<Flashcard> { it.orderIndex }.thenBy { it.createdAt })
        }

    fun observeQuestions(deckId: String): Flow<List<QuizQuestion>> =
        state.map { s ->
            s.questions[deckId].orEmpty()
                .sortedWith(compareBy<QuizQuestion> { it.orderIndex }.thenBy { it.createdAt })
        }

    fun observeVideos(deckId: String): Flow<List<StudyVideo>> =
        state.map { s ->
            s.videos[deckId].orEmpty().sortedByDescending { it.createdAt }
        }

    fun observeStudentDeckProgress(deckId: String): Flow<DeckProgress?> =
        state.map { it.progress[deckId] }

    fun observeClassProgress(deckId: String): Flow<List<DeckProgress>> =
        state.map { listOfNotNull(it.progress[deckId]) }

    fun observeLiveSession(deckId: String): Flow<LiveSession?> =
        state.map { it.liveSessions[deckId] }

    fun observeWallet(): Flow<StudyWallet?> =
        state.map { it.wallet }

    fun observeTransactions(): Flow<List<WalletTransaction>> =
        state.map { s -> s.transactions.sortedByDescending { it.createdAt } }

    fun observeTeacherBatches(): Flow<List<StudyBatch>> =
        state.map { s ->
            s.batches.values.sortedByDescending { it.updatedAt ?: it.createdAt }
        }

    fun observePublishedBatches(): Flow<List<StudyBatch>> =
        state.map { s ->
            s.batches.values.filter { it.published }.sortedByDescending { it.updatedAt ?: it.createdAt }
        }

    fun observeBatch(batchId: String): Flow<StudyBatch?> =
        state.map { it.batches[batchId] }

    fun observeBatchEnrollments(): Flow<List<BatchEnrollment>> =
        state.map { s ->
            s.batchEnrollments.values.sortedByDescending { it.createdAt }
        }

    fun observeBatchMembers(batchId: String): Flow<List<BatchMember>> =
        state.map { s ->
            s.batchMembers[batchId].orEmpty().sortedByDescending { it.createdAt }
        }

    // --- MUTATIONS ---

    fun ensureUserProfile(displayName: String?): Result<Unit> {
        val now = Timestamp.now()
        state.update { s ->
            if (s.profile != null) s
            else {
                val safeName = displayName?.trim()?.take(80)?.ifBlank { null } ?: "Guest Scholar"
                s.copy(
                    profile = UserProfile(
                        id = GUEST_USER_ID,
                        userId = GUEST_USER_ID,
                        displayName = safeName,
                        role = UserRole.TEACHER.name,
                        totalXp = 0L,
                        quizzesCompleted = 0L,
                        cardsReviewed = 0L,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
        return Result.success(Unit)
    }

    fun updateUserRole(role: UserRole): Result<Unit> {
        ensureUserProfile(null)
        val now = Timestamp.now()
        state.update { s ->
            s.copy(profile = s.profile?.copy(role = role.name, updatedAt = now))
        }
        return Result.success(Unit)
    }

    fun createDeck(
        title: String,
        subject: String,
        description: String,
        accent: DeckAccent,
        published: Boolean,
    ): Result<String> = runCatching {
        val cleanTitle = title.trim()
        val cleanSubject = subject.trim()
        val cleanDesc = description.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) { "Title must be 1–100 characters." }
        require(cleanSubject.isNotEmpty() && cleanSubject.length <= 50) { "Subject must be 1–50 characters." }
        require(cleanDesc.length <= 300) { "Description must be 300 characters or fewer." }

        val deckId = nextId("deck")
        val now = Timestamp.now()
        val deck = StudyDeck(
            id = deckId,
            teacherId = GUEST_USER_ID,
            teacherName = guestName(),
            title = cleanTitle,
            subject = cleanSubject,
            description = cleanDesc,
            joinCode = generateCode(),
            accentColorKey = accent.name,
            published = published,
            flashcardCount = 0L,
            quizCount = 0L,
            createdAt = now,
            updatedAt = now,
        )
        state.update { s -> s.copy(decks = s.decks + (deckId to deck)) }
        deckId
    }

    fun updateDeck(
        deckId: String,
        title: String,
        subject: String,
        description: String,
        accent: DeckAccent,
        published: Boolean,
    ): Result<Unit> = runCatching {
        require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
        val cleanTitle = title.trim()
        val cleanSubject = subject.trim()
        val cleanDesc = description.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) { "Title must be 1–100 characters." }
        require(cleanSubject.isNotEmpty() && cleanSubject.length <= 50) { "Subject must be 1–50 characters." }
        require(cleanDesc.length <= 300) { "Description must be 300 characters or fewer." }

        val now = Timestamp.now()
        state.update { s ->
            val existing = s.decks[deckId] ?: return@update s
            val updatedDeck = existing.copy(
                title = cleanTitle,
                subject = cleanSubject,
                description = cleanDesc,
                accentColorKey = accent.name,
                published = published,
                updatedAt = now,
            )
            val updatedCards = s.flashcards[deckId].orEmpty().map { it.copy(published = published, updatedAt = now) }
            val updatedQuestions = s.questions[deckId].orEmpty().map { it.copy(published = published, updatedAt = now) }
            val updatedVideos = s.videos[deckId].orEmpty().map { it.copy(published = published, updatedAt = now) }
            s.copy(
                decks = s.decks + (deckId to updatedDeck),
                flashcards = s.flashcards + (deckId to updatedCards),
                questions = s.questions + (deckId to updatedQuestions),
                videos = s.videos + (deckId to updatedVideos),
            )
        }
    }

    fun deleteDeck(deckId: String): Result<Unit> {
        state.update { s ->
            s.copy(
                decks = s.decks - deckId,
                flashcards = s.flashcards - deckId,
                questions = s.questions - deckId,
                videos = s.videos - deckId,
                progress = s.progress - deckId,
                liveSessions = s.liveSessions - deckId,
            )
        }
        return Result.success(Unit)
    }

    fun addFlashcard(
        deckId: String,
        front: String,
        back: String,
        hint: String,
        published: Boolean,
        orderIndex: Int,
    ): Result<Unit> = runCatching {
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        val cleanHint = hint.trim()
        require(cleanFront.isNotEmpty() && cleanFront.length <= 300) { "Front text must be 1–300 characters." }
        require(cleanBack.isNotEmpty() && cleanBack.length <= 500) { "Back text must be 1–500 characters." }
        require(cleanHint.length <= 150) { "Hint must be 150 characters or fewer." }

        val now = Timestamp.now()
        val card = Flashcard(
            id = nextId("card"),
            deckId = deckId,
            teacherId = GUEST_USER_ID,
            front = cleanFront,
            back = cleanBack,
            hint = cleanHint,
            orderIndex = orderIndex.toLong(),
            published = published,
            createdAt = now,
            updatedAt = now,
        )
        state.update { s ->
            val updatedList = s.flashcards[deckId].orEmpty() + card
            val deck = s.decks[deckId]?.let {
                it.copy(flashcardCount = updatedList.size.toLong(), updatedAt = now)
            }
            s.copy(
                flashcards = s.flashcards + (deckId to updatedList),
                decks = if (deck != null) s.decks + (deckId to deck) else s.decks,
            )
        }
    }

    fun updateFlashcard(
        deckId: String,
        cardId: String,
        front: String,
        back: String,
        hint: String,
        orderIndex: Int,
    ): Result<Unit> = runCatching {
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        val cleanHint = hint.trim()
        require(cleanFront.isNotEmpty() && cleanFront.length <= 300) { "Front text must be 1–300 characters." }
        require(cleanBack.isNotEmpty() && cleanBack.length <= 500) { "Back text must be 1–500 characters." }
        require(cleanHint.length <= 150) { "Hint must be 150 characters or fewer." }

        val now = Timestamp.now()
        state.update { s ->
            val updatedList = s.flashcards[deckId].orEmpty().map { card ->
                if (card.id == cardId) {
                    card.copy(
                        front = cleanFront,
                        back = cleanBack,
                        hint = cleanHint,
                        orderIndex = orderIndex.toLong(),
                        updatedAt = now,
                    )
                } else card
            }
            s.copy(flashcards = s.flashcards + (deckId to updatedList))
        }
    }

    fun deleteFlashcard(deckId: String, cardId: String): Result<Unit> {
        val now = Timestamp.now()
        state.update { s ->
            val updatedList = s.flashcards[deckId].orEmpty().filterNot { it.id == cardId }
            val deck = s.decks[deckId]?.let {
                it.copy(flashcardCount = updatedList.size.toLong(), updatedAt = now)
            }
            s.copy(
                flashcards = s.flashcards + (deckId to updatedList),
                decks = if (deck != null) s.decks + (deckId to deck) else s.decks,
            )
        }
        return Result.success(Unit)
    }

    fun addQuestion(
        deckId: String,
        prompt: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
        published: Boolean,
        orderIndex: Int,
    ): Result<Unit> = runCatching {
        val cleanPrompt = prompt.trim()
        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
        val cleanExplanation = explanation.trim()
        require(cleanPrompt.isNotEmpty() && cleanPrompt.length <= 300) { "Question prompt must be 1–300 characters." }
        require(cleanOptions.size in 2..4) { "Provide 2 to 4 answer options." }
        require(cleanOptions.all { it.length <= 150 }) { "Each option must be 150 characters or fewer." }
        require(correctIndex in cleanOptions.indices) { "Correct answer index out of range." }
        require(cleanExplanation.length <= 400) { "Explanation must be 400 characters or fewer." }

        val now = Timestamp.now()
        val q = QuizQuestion(
            id = nextId("q"),
            deckId = deckId,
            teacherId = GUEST_USER_ID,
            prompt = cleanPrompt,
            options = cleanOptions,
            correctIndex = correctIndex.toLong(),
            explanation = cleanExplanation,
            orderIndex = orderIndex.toLong(),
            published = published,
            createdAt = now,
            updatedAt = now,
        )
        state.update { s ->
            val updatedList = s.questions[deckId].orEmpty() + q
            val deck = s.decks[deckId]?.let {
                it.copy(quizCount = updatedList.size.toLong(), updatedAt = now)
            }
            s.copy(
                questions = s.questions + (deckId to updatedList),
                decks = if (deck != null) s.decks + (deckId to deck) else s.decks,
            )
        }
    }

    fun updateQuestion(
        deckId: String,
        questionId: String,
        prompt: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
        orderIndex: Int,
    ): Result<Unit> = runCatching {
        val cleanPrompt = prompt.trim()
        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
        val cleanExplanation = explanation.trim()
        require(cleanPrompt.isNotEmpty() && cleanPrompt.length <= 300) { "Question prompt must be 1–300 characters." }
        require(cleanOptions.size in 2..4) { "Provide 2 to 4 answer options." }
        require(cleanOptions.all { it.length <= 150 }) { "Each option must be 150 characters or fewer." }
        require(correctIndex in cleanOptions.indices) { "Correct answer index out of range." }
        require(cleanExplanation.length <= 400) { "Explanation must be 400 characters or fewer." }

        val now = Timestamp.now()
        state.update { s ->
            val updatedList = s.questions[deckId].orEmpty().map { q ->
                if (q.id == questionId) {
                    q.copy(
                        prompt = cleanPrompt,
                        options = cleanOptions,
                        correctIndex = correctIndex.toLong(),
                        explanation = cleanExplanation,
                        orderIndex = orderIndex.toLong(),
                        updatedAt = now,
                    )
                } else q
            }
            s.copy(questions = s.questions + (deckId to updatedList))
        }
    }

    fun deleteQuestion(deckId: String, questionId: String): Result<Unit> {
        val now = Timestamp.now()
        state.update { s ->
            val updatedList = s.questions[deckId].orEmpty().filterNot { it.id == questionId }
            val deck = s.decks[deckId]?.let {
                it.copy(quizCount = updatedList.size.toLong(), updatedAt = now)
            }
            s.copy(
                questions = s.questions + (deckId to updatedList),
                decks = if (deck != null) s.decks + (deckId to deck) else s.decks,
            )
        }
        return Result.success(Unit)
    }

    fun joinDeckByCode(rawCode: String): Result<StudyDeck> = runCatching {
        val code = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        require(code.length in 4..12) { "Enter a valid 4–12 character join code." }
        val deck = state.value.decks.values.firstOrNull { it.published && it.joinCode == code }
            ?: throw IllegalArgumentException("No published deck found for code $code.")
        enrollInDeck(deck).getOrThrow()
        deck
    }

    fun enrollInDeck(deck: StudyDeck): Result<Unit> = runCatching {
        require(deck.id.isNotBlank()) { "Invalid deck." }
        val now = Timestamp.now()
        state.update { s ->
            if (s.enrollments.containsKey(deck.id)) s
            else {
                val enroll = Enrollment(
                    id = deck.id,
                    deckId = deck.id,
                    deckTitle = deck.title.take(100).ifBlank { "Untitled Deck" },
                    subject = deck.subject.take(50).ifBlank { "General" },
                    teacherName = deck.teacherName.take(80).ifBlank { "Instructor" },
                    accentColorKey = DeckAccent.fromRaw(deck.accentColorKey).name,
                    masteredCount = 0L,
                    totalCards = deck.flashcardCount.coerceIn(0L, 1000L),
                    bestQuizPercent = 0L,
                    quizzesTaken = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
                s.copy(enrollments = s.enrollments + (deck.id to enroll))
            }
        }
    }

    fun recordFlashcardSession(
        deck: StudyDeck,
        masteredCardIds: List<String>,
        reviewedCount: Int,
        knownCount: Int,
        totalCardsInDeck: Int,
    ): Result<Unit> {
        ensureUserProfile(null)
        val now = Timestamp.now()
        val cleanMastered = masteredCardIds.distinct().take(200)
        val safeTotal = max(totalCardsInDeck, cleanMastered.size).coerceIn(1, 1000)
        val sessionTotal = max(reviewedCount, 1).coerceIn(1, 1000)
        val sessionKnown = knownCount.coerceIn(0, sessionTotal)
        val sessionPercent = ((sessionKnown.toFloat() / sessionTotal.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        val xpEarned = (sessionKnown * 10 + reviewedCount * 3).coerceIn(5, 10000).toLong()

        state.update { s ->
            val existingProgress = s.progress[deck.id]
            val updatedProgress = if (existingProgress == null) {
                DeckProgress(
                    id = GUEST_USER_ID,
                    deckId = deck.id,
                    teacherId = deck.teacherId,
                    studentId = GUEST_USER_ID,
                    studentName = guestName(),
                    masteredCardIds = cleanMastered,
                    reviewedCardsCount = reviewedCount.coerceAtLeast(0).toLong(),
                    totalCardsCount = safeTotal.toLong(),
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                val merged = (existingProgress.masteredCardIds + cleanMastered).distinct().take(200)
                existingProgress.copy(
                    masteredCardIds = merged,
                    reviewedCardsCount = existingProgress.reviewedCardsCount + reviewedCount.coerceAtLeast(0).toLong(),
                    totalCardsCount = max(safeTotal, merged.size).toLong(),
                    updatedAt = now,
                )
            }

            val existingEnroll = s.enrollments[deck.id]
            val updatedEnroll = if (existingEnroll == null) {
                Enrollment(
                    id = deck.id,
                    deckId = deck.id,
                    deckTitle = deck.title.take(100),
                    subject = deck.subject.take(50),
                    teacherName = deck.teacherName.take(80),
                    accentColorKey = DeckAccent.fromRaw(deck.accentColorKey).name,
                    masteredCount = cleanMastered.size.toLong(),
                    totalCards = safeTotal.toLong(),
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                val bestMastered = max(existingEnroll.masteredCount.toInt(), cleanMastered.size).coerceAtMost(safeTotal)
                existingEnroll.copy(
                    deckTitle = deck.title.take(100),
                    subject = deck.subject.take(50),
                    teacherName = deck.teacherName.take(80),
                    accentColorKey = DeckAccent.fromRaw(deck.accentColorKey).name,
                    masteredCount = bestMastered.toLong(),
                    totalCards = safeTotal.toLong(),
                    updatedAt = now,
                )
            }

            val log = StudyLog(
                id = nextId("log"),
                deckId = deck.id,
                deckTitle = deck.title.take(100),
                mode = StudyMode.FLASHCARDS.name,
                scorePercent = sessionPercent.toLong(),
                correctCount = sessionKnown.toLong(),
                totalCount = sessionTotal.toLong(),
                xpEarned = xpEarned,
                createdAt = now,
            )

            val updatedProfile = s.profile?.let { p ->
                p.copy(
                    totalXp = p.totalXp + xpEarned,
                    cardsReviewed = p.cardsReviewed + reviewedCount.coerceAtLeast(0).toLong(),
                    updatedAt = now,
                )
            }

            s.copy(
                progress = s.progress + (deck.id to updatedProgress),
                enrollments = s.enrollments + (deck.id to updatedEnroll),
                studyLogs = s.studyLogs + log,
                profile = updatedProfile,
            )
        }
        return Result.success(Unit)
    }

    fun recordQuizResult(
        deck: StudyDeck,
        correctCount: Int,
        totalCount: Int,
    ): Result<Unit> {
        ensureUserProfile(null)
        val now = Timestamp.now()
        val safeTotal = totalCount.coerceIn(1, 1000)
        val safeCorrect = correctCount.coerceIn(0, safeTotal)
        val percent = ((safeCorrect.toFloat() / safeTotal.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        val xpEarned = (safeCorrect * 20 + 15).coerceIn(15, 10000).toLong()

        state.update { s ->
            val existingProgress = s.progress[deck.id]
            val updatedProgress = if (existingProgress == null) {
                DeckProgress(
                    id = GUEST_USER_ID,
                    deckId = deck.id,
                    teacherId = deck.teacherId,
                    studentId = GUEST_USER_ID,
                    studentName = guestName(),
                    totalCardsCount = deck.flashcardCount.coerceIn(0L, 1000L),
                    quizzesTaken = 1L,
                    bestQuizPercent = percent.toLong(),
                    lastQuizPercent = percent.toLong(),
                    lastQuizCorrect = safeCorrect.toLong(),
                    lastQuizTotal = safeTotal.toLong(),
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                existingProgress.copy(
                    quizzesTaken = existingProgress.quizzesTaken + 1L,
                    bestQuizPercent = max(existingProgress.bestQuizPercent, percent.toLong()),
                    lastQuizPercent = percent.toLong(),
                    lastQuizCorrect = safeCorrect.toLong(),
                    lastQuizTotal = safeTotal.toLong(),
                    updatedAt = now,
                )
            }

            val existingEnroll = s.enrollments[deck.id]
            val updatedEnroll = if (existingEnroll == null) {
                Enrollment(
                    id = deck.id,
                    deckId = deck.id,
                    deckTitle = deck.title.take(100),
                    subject = deck.subject.take(50),
                    teacherName = deck.teacherName.take(80),
                    accentColorKey = DeckAccent.fromRaw(deck.accentColorKey).name,
                    totalCards = deck.flashcardCount.coerceIn(0L, 1000L),
                    bestQuizPercent = percent.toLong(),
                    quizzesTaken = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                existingEnroll.copy(
                    deckTitle = deck.title.take(100),
                    subject = deck.subject.take(50),
                    teacherName = deck.teacherName.take(80),
                    accentColorKey = DeckAccent.fromRaw(deck.accentColorKey).name,
                    bestQuizPercent = max(existingEnroll.bestQuizPercent, percent.toLong()),
                    quizzesTaken = existingEnroll.quizzesTaken + 1L,
                    updatedAt = now,
                )
            }

            val log = StudyLog(
                id = nextId("log"),
                deckId = deck.id,
                deckTitle = deck.title.take(100),
                mode = StudyMode.QUIZ.name,
                scorePercent = percent.toLong(),
                correctCount = safeCorrect.toLong(),
                totalCount = safeTotal.toLong(),
                xpEarned = xpEarned,
                createdAt = now,
            )

            val updatedProfile = s.profile?.let { p ->
                p.copy(
                    totalXp = p.totalXp + xpEarned,
                    quizzesCompleted = p.quizzesCompleted + 1L,
                    updatedAt = now,
                )
            }

            s.copy(
                progress = s.progress + (deck.id to updatedProgress),
                enrollments = s.enrollments + (deck.id to updatedEnroll),
                studyLogs = s.studyLogs + log,
                profile = updatedProfile,
            )
        }
        return Result.success(Unit)
    }

    fun updateLiveSession(
        deck: StudyDeck,
        active: Boolean,
        itemType: LiveItemType,
        itemIndex: Int,
        activePrompt: String,
    ): Result<Unit> {
        val now = Timestamp.now()
        val cleanPrompt = activePrompt.trim().take(300)
        val safeIndex = itemIndex.coerceIn(0, 10000).toLong()
        state.update { s ->
            val existing = s.liveSessions[deck.id]
            val session = LiveSession(
                id = "active",
                deckId = deck.id,
                teacherId = GUEST_USER_ID,
                teacherName = guestName(),
                active = active,
                published = deck.published,
                activeItemType = itemType.name,
                activeItemIndex = safeIndex,
                activePrompt = cleanPrompt,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            s.copy(liveSessions = s.liveSessions + (deck.id to session))
        }
        return Result.success(Unit)
    }

    fun addVideo(
        deckId: String,
        videoId: String,
        title: String,
        notes: String,
        fileName: String,
        durationMs: Long,
        fileSizeBytes: Long,
        published: Boolean,
    ): Result<Unit> = runCatching {
        val cleanTitle = title.trim()
        val cleanNotes = notes.trim()
        require(videoId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) { "Invalid video ID." }
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) { "Video title must be 1–100 characters." }
        require(cleanNotes.length <= 300) { "Notes must be 300 characters or fewer." }
        require(fileName.matches(Regex("^[a-zA-Z0-9_.\\-]{1,128}$"))) { "Invalid video file name." }

        val now = Timestamp.now()
        val video = StudyVideo(
            id = videoId,
            deckId = deckId,
            teacherId = GUEST_USER_ID,
            teacherName = guestName(),
            title = cleanTitle,
            notes = cleanNotes,
            fileName = fileName,
            durationMs = durationMs.coerceIn(0L, 14_400_000L),
            fileSizeBytes = fileSizeBytes.coerceIn(0L, 2_000_000_000L),
            published = published,
            createdAt = now,
            updatedAt = now,
        )
        state.update { s ->
            val updated = s.videos[deckId].orEmpty() + video
            s.copy(videos = s.videos + (deckId to updated))
        }
    }

    fun deleteVideo(deckId: String, videoId: String): Result<Unit> {
        state.update { s ->
            val updated = s.videos[deckId].orEmpty().filterNot { it.id == videoId }
            s.copy(videos = s.videos + (deckId to updated))
        }
        return Result.success(Unit)
    }

    fun topUpWallet(amountInr: Long, paymentMethod: String): Result<Unit> = runCatching {
        require(amountInr in 1L..1_000_000L) { "Enter an amount between ₹1 and ₹10,00,000." }
        val safeMethod = when (paymentMethod) {
            "UPI", "CARD_TOPUP", "NETBANKING_TOPUP" -> paymentMethod
            else -> "UPI"
        }
        val now = Timestamp.now()
        state.update { s ->
            val existing = s.wallet
            val newBalance = (existing?.balanceInr ?: 0L) + amountInr
            require(newBalance <= 10_000_000L) { "Maximum wallet balance limit reached." }
            val updatedWallet = StudyWallet(
                id = "summary",
                userId = GUEST_USER_ID,
                balanceInr = newBalance,
                totalAddedInr = (existing?.totalAddedInr ?: 0L) + amountInr,
                totalSpentInr = existing?.totalSpentInr ?: 0L,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            val tx = WalletTransaction(
                id = nextId("tx"),
                userId = GUEST_USER_ID,
                type = WalletTxType.TOP_UP.name,
                amountInr = amountInr,
                title = "Added money to Study Wallet",
                batchId = "",
                paymentMethod = safeMethod,
                status = "COMPLETED",
                createdAt = now,
            )
            s.copy(
                wallet = updatedWallet,
                transactions = s.transactions + tx,
            )
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
    ): Result<String> = runCatching {
        val cleanTitle = title.trim()
        val cleanSubject = subject.trim()
        val cleanDesc = description.trim()
        val cleanSchedule = scheduleText.trim()
        val cleanUpi = upiId.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) { "Batch name must be 1–100 characters." }
        require(cleanSubject.isNotEmpty() && cleanSubject.length <= 50) { "Subject must be 1–50 characters." }
        require(cleanDesc.length <= 400) { "Description must be 400 characters or fewer." }
        require(cleanSchedule.length <= 100) { "Schedule must be 100 characters or fewer." }
        require(priceInr in 0L..100_000L) { "Batch fee must be between ₹0 and ₹1,00,000." }
        require(cleanUpi.length <= 100) { "UPI ID must be 100 characters or fewer." }
        require(coverImageData.length <= 150_000) { "Cover image is too large." }

        val batchId = nextId("batch")
        val now = Timestamp.now()
        val batch = StudyBatch(
            id = batchId,
            teacherId = GUEST_USER_ID,
            teacherName = guestName(),
            title = cleanTitle,
            subject = cleanSubject,
            description = cleanDesc,
            scheduleText = cleanSchedule,
            batchCode = generateCode(),
            priceInr = priceInr,
            upiId = cleanUpi,
            coverImageData = coverImageData,
            accentColorKey = accent.name,
            published = published,
            enrolledCount = 0L,
            createdAt = now,
            updatedAt = now,
        )
        state.update { s -> s.copy(batches = s.batches + (batchId to batch)) }
        batchId
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
    ): Result<Unit> = runCatching {
        require(batchId.isNotBlank()) { "Batch ID cannot be blank." }
        val cleanTitle = title.trim()
        val cleanSubject = subject.trim()
        val cleanDesc = description.trim()
        val cleanSchedule = scheduleText.trim()
        val cleanUpi = upiId.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) { "Batch name must be 1–100 characters." }
        require(cleanSubject.isNotEmpty() && cleanSubject.length <= 50) { "Subject must be 1–50 characters." }
        require(cleanDesc.length <= 400) { "Description must be 400 characters or fewer." }
        require(cleanSchedule.length <= 100) { "Schedule must be 100 characters or fewer." }
        require(priceInr in 0L..100_000L) { "Batch fee must be between ₹0 and ₹1,00,000." }
        require(cleanUpi.length <= 100) { "UPI ID must be 100 characters or fewer." }
        require(coverImageData.length <= 150_000) { "Cover image is too large." }

        val now = Timestamp.now()
        state.update { s ->
            val existing = s.batches[batchId] ?: return@update s
            val updated = existing.copy(
                title = cleanTitle,
                subject = cleanSubject,
                description = cleanDesc,
                scheduleText = cleanSchedule,
                priceInr = priceInr,
                upiId = cleanUpi,
                coverImageData = coverImageData,
                accentColorKey = accent.name,
                published = published,
                updatedAt = now,
            )
            s.copy(batches = s.batches + (batchId to updated))
        }
    }

    fun deleteBatch(batchId: String): Result<Unit> {
        state.update { s ->
            s.copy(
                batches = s.batches - batchId,
                batchMembers = s.batchMembers - batchId,
            )
        }
        return Result.success(Unit)
    }

    fun payAndEnrollBatch(
        studyBatch: StudyBatch,
        preferredMethod: String = "WALLET",
    ): Result<Unit> = runCatching {
        require(studyBatch.id.isNotBlank()) { "Invalid batch." }
        val current = state.value
        if (current.batchEnrollments.containsKey(studyBatch.id)) return@runCatching

        val fee = studyBatch.priceInr.coerceIn(0L, 100_000L)
        val actualMethod = when {
            fee == 0L -> "FREE"
            preferredMethod == "UPI" -> "UPI"
            else -> "WALLET"
        }

        if (actualMethod == "WALLET") {
            val balance = current.wallet?.balanceInr ?: 0L
            if (current.wallet == null || balance < fee) {
                throw IllegalStateException(
                    "Insufficient Study Wallet balance (₹$balance). Add ₹${fee - balance} to join."
                )
            }
        }

        val now = Timestamp.now()
        var txId = ""
        state.update { s ->
            var nextWallet = s.wallet
            var nextTxs = s.transactions
            if (fee > 0L) {
                txId = nextId("tx")
                if (actualMethod == "WALLET" && nextWallet != null) {
                    nextWallet = nextWallet.copy(
                        balanceInr = (nextWallet.balanceInr - fee).coerceAtLeast(0L),
                        totalSpentInr = nextWallet.totalSpentInr + fee,
                        updatedAt = now,
                    )
                }
                nextTxs = nextTxs + WalletTransaction(
                    id = txId,
                    userId = GUEST_USER_ID,
                    type = WalletTxType.BATCH_PAYMENT.name,
                    amountInr = fee,
                    title = "Joined ${studyBatch.title}".take(120),
                    batchId = studyBatch.id,
                    paymentMethod = actualMethod,
                    status = "COMPLETED",
                    createdAt = now,
                )
            }

            val enroll = BatchEnrollment(
                id = studyBatch.id,
                batchId = studyBatch.id,
                batchTitle = studyBatch.title.take(100).ifBlank { "Study Batch" },
                subject = studyBatch.subject.take(50).ifBlank { "General" },
                teacherId = studyBatch.teacherId,
                teacherName = studyBatch.teacherName.take(80).ifBlank { "Instructor" },
                scheduleText = studyBatch.scheduleText.take(100),
                accentColorKey = DeckAccent.fromRaw(studyBatch.accentColorKey).name,
                coverImageData = studyBatch.coverImageData.take(150_000),
                amountPaidInr = fee,
                paymentMethod = actualMethod,
                transactionId = txId,
                createdAt = now,
                updatedAt = now,
            )

            val existingMembers = s.batchMembers[studyBatch.id].orEmpty()
            val alreadyMember = existingMembers.any { it.studentId == GUEST_USER_ID }
            val nextMembers = if (alreadyMember) existingMembers else {
                existingMembers + BatchMember(
                    id = GUEST_USER_ID,
                    batchId = studyBatch.id,
                    teacherId = studyBatch.teacherId,
                    studentId = GUEST_USER_ID,
                    studentName = guestName(),
                    amountPaidInr = fee,
                    paymentMethod = actualMethod,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            val nextBatches = if (!alreadyMember && s.batches.containsKey(studyBatch.id)) {
                val b = s.batches.getValue(studyBatch.id)
                s.batches + (studyBatch.id to b.copy(enrolledCount = b.enrolledCount + 1L, updatedAt = now))
            } else s.batches

            s.copy(
                wallet = nextWallet,
                transactions = nextTxs,
                batchEnrollments = s.batchEnrollments + (studyBatch.id to enroll),
                batchMembers = s.batchMembers + (studyBatch.id to nextMembers),
                batches = nextBatches,
            )
        }
    }

    fun findBatchByCode(rawCode: String): Result<StudyBatch> = runCatching {
        val code = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        require(code.length in 4..12) { "Enter a valid 4–12 character batch code." }
        state.value.batches.values.firstOrNull { it.published && it.batchCode == code }
            ?: throw IllegalArgumentException("No published batch found for code $code.")
    }

    fun fetchFlashcardsForDeck(deckId: String): Result<List<Flashcard>> = runCatching {
        require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
        state.value.flashcards[deckId].orEmpty()
            .sortedWith(compareBy<Flashcard> { it.orderIndex }.thenBy { it.createdAt })
    }
}
