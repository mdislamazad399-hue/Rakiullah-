package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.roundToInt

class StudyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val isGuest: Boolean = false,
) {
    constructor(context: Context, isGuest: Boolean = false) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        isGuest = isGuest,
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    private fun currentDisplayName(): String {
        val name = auth.currentUser?.displayName?.trim()
        if (!name.isNullOrBlank()) return name.take(80)
        val phone = auth.currentUser?.phoneNumber?.trim()
        if (!phone.isNullOrBlank()) return phone.take(80)
        return "Scholar"
    }

    private fun <T> Flow<T>.logAndRethrow(operation: OperationType, path: String, expectedUid: String? = null): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val stillValidUser = expectedUid == null || auth.currentUser?.uid == expectedUid
            if (stillValidUser) {
                handleFirestoreError(firestoreError ?: error, operation, path)
            }
            throw error
        }

    private fun <T> Flow<T>.resilientFirestoreStream(
        operation: OperationType,
        path: String,
        expectedUid: String? = null,
    ): Flow<T> = retryWhen { cause, attempt ->
        val firestoreError = generateSequence(cause) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && cause is CancellationException) return@retryWhen false
        val stillValidUser = expectedUid == null || auth.currentUser?.uid == expectedUid
        if (!stillValidUser) return@retryWhen false
        val isRetryable = firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            firestoreError?.code == FirebaseFirestoreException.Code.ABORTED ||
            firestoreError?.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
        if (isRetryable && attempt < 3L) {
            delay((attempt + 1L) * 1500L)
            true
        } else {
            false
        }
    }.logAndRethrow(operation, path, expectedUid)

    // --- STREAMS ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (isGuest) return GuestStudyStore.observeUserProfile()
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .resilientFirestoreStream(OperationType.GET, "users/$userId", userId)
    }

    fun observeTeacherDecks(teacherId: String): Flow<List<StudyDeck>> {
        if (isGuest) return GuestStudyStore.observeTeacherDecks()
        return db.collection("decks")
            .whereEqualTo("teacherId", teacherId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(StudyDeck::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "decks", teacherId)
    }

    fun observePublishedDecks(): Flow<List<StudyDeck>> {
        if (isGuest) return GuestStudyStore.observePublishedDecks()
        return db.collection("decks")
            .whereEqualTo("published", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(StudyDeck::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "decks")
    }

    fun observeEnrollments(userId: String): Flow<List<Enrollment>> {
        if (isGuest) return GuestStudyStore.observeEnrollments()
        return db.collection("users").document(userId).collection("enrollments")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Enrollment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "users/$userId/enrollments", userId)
    }

    fun observeStudyLogs(userId: String): Flow<List<StudyLog>> {
        if (isGuest) return GuestStudyStore.observeStudyLogs()
        return db.collection("users").document(userId).collection("studyLogs")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(StudyLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "users/$userId/studyLogs", userId)
    }

    fun observeDeck(deckId: String): Flow<StudyDeck?> {
        if (isGuest) return GuestStudyStore.observeDeck(deckId)
        return db.collection("decks").document(deckId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(StudyDeck::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .resilientFirestoreStream(OperationType.GET, "decks/$deckId")
    }

    fun observeFlashcards(deckId: String, teacherId: String, currentUserId: String): Flow<List<Flashcard>> {
        if (isGuest) return GuestStudyStore.observeFlashcards(deckId)
        val base = db.collection("decks").document(deckId).collection("flashcards")
        val query = if (teacherId == currentUserId) {
            base.whereEqualTo("teacherId", currentUserId)
        } else {
            base.whereEqualTo("published", true)
        }
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(Flashcard::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<Flashcard> { it.orderIndex }.thenBy { it.createdAt })
            }
            .resilientFirestoreStream(OperationType.LIST, "decks/$deckId/flashcards", currentUserId)
    }

    fun observeQuestions(deckId: String, teacherId: String, currentUserId: String): Flow<List<QuizQuestion>> {
        if (isGuest) return GuestStudyStore.observeQuestions(deckId)
        val base = db.collection("decks").document(deckId).collection("questions")
        val query = if (teacherId == currentUserId) {
            base.whereEqualTo("teacherId", currentUserId)
        } else {
            base.whereEqualTo("published", true)
        }
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(QuizQuestion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<QuizQuestion> { it.orderIndex }.thenBy { it.createdAt })
            }
            .resilientFirestoreStream(OperationType.LIST, "decks/$deckId/questions", currentUserId)
    }

    fun observeVideos(deckId: String, teacherId: String, currentUserId: String): Flow<List<StudyVideo>> {
        if (isGuest) return GuestStudyStore.observeVideos(deckId)
        val base = db.collection("decks").document(deckId).collection("videos")
        val query = if (teacherId == currentUserId) {
            base.whereEqualTo("teacherId", currentUserId)
        } else {
            base.whereEqualTo("published", true)
        }
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(StudyVideo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "decks/$deckId/videos", currentUserId)
    }

    fun observeStudentDeckProgress(deckId: String, studentId: String): Flow<DeckProgress?> {
        if (isGuest) return GuestStudyStore.observeStudentDeckProgress(deckId)
        return db.collection("decks").document(deckId).collection("progress").document(studentId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(DeckProgress::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .resilientFirestoreStream(OperationType.GET, "decks/$deckId/progress/$studentId", studentId)
    }

    fun observeClassProgress(deckId: String, teacherId: String): Flow<List<DeckProgress>> {
        if (isGuest) return GuestStudyStore.observeClassProgress(deckId)
        return db.collection("decks").document(deckId).collection("progress")
            .whereEqualTo("teacherId", teacherId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(DeckProgress::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.bestQuizPercent }
            }
            .resilientFirestoreStream(OperationType.LIST, "decks/$deckId/progress", teacherId)
    }

    // --- USER PROFILE MUTATIONS ---

    suspend fun ensureUserProfile(userId: String, displayName: String?): Result<Unit> {
        if (isGuest) return GuestStudyStore.ensureUserProfile(displayName)
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Session user mismatch." }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = displayName?.trim()?.take(80)?.ifBlank { null } ?: currentDisplayName()
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "role" to UserRole.TEACHER.name,
                        "totalXp" to 0L,
                        "quizzesCompleted" to 0L,
                        "cardsReviewed" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserRole(userId: String, role: UserRole): Result<Unit> {
        if (isGuest) return GuestStudyStore.updateUserRole(role)
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Session user mismatch." }
            db.collection("users").document(uid).update(
                mapOf(
                    "role" to role.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- TEACHER DECK & CONTENT MUTATIONS ---

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    suspend fun createDeck(
        title: String,
        subject: String,
        description: String,
        accent: DeckAccent,
        published: Boolean,
    ): Result<String> {
        if (isGuest) return GuestStudyStore.createDeck(title, subject, description, accent, published)
        var docPath = "decks"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim()
            val cleanSubject = subject.trim()
            val cleanDesc = description.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) { "Title must be 1–100 characters." }
            require(cleanSubject.isNotEmpty() && cleanSubject.length <= 50) { "Subject must be 1–50 characters." }
            require(cleanDesc.length <= 300) { "Description must be 300 characters or fewer." }

            val docRef = db.collection("decks").document()
            docPath = docRef.path
            val joinCode = generateJoinCode()

            docRef.set(
                mapOf(
                    "teacherId" to uid,
                    "teacherName" to currentDisplayName(),
                    "title" to cleanTitle,
                    "subject" to cleanSubject,
                    "description" to cleanDesc,
                    "joinCode" to joinCode,
                    "accentColorKey" to accent.name,
                    "published" to published,
                    "flashcardCount" to 0L,
                    "quizCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDeck(
        deckId: String,
        title: String,
        subject: String,
        description: String,
        accent: DeckAccent,
        published: Boolean,
        previousPublished: Boolean,
    ): Result<Unit> {
        if (isGuest) return GuestStudyStore.updateDeck(deckId, title, subject, description, accent, published)
        val docPath = "decks/$deckId"
        return try {
            val uid = requireUserId()
            require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
            val cleanTitle = title.trim()
            val cleanSubject = subject.trim()
            val cleanDesc = description.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) { "Title must be 1–100 characters." }
            require(cleanSubject.isNotEmpty() && cleanSubject.length <= 50) { "Subject must be 1–50 characters." }
            require(cleanDesc.length <= 300) { "Description must be 300 characters or fewer." }

            val deckRef = db.collection("decks").document(deckId)
            val batch = db.batch()
            batch.update(
                deckRef,
                mapOf(
                    "title" to cleanTitle,
                    "subject" to cleanSubject,
                    "description" to cleanDesc,
                    "accentColorKey" to accent.name,
                    "published" to published,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            if (published != previousPublished) {
                val cards = deckRef.collection("flashcards").whereEqualTo("teacherId", uid).get().await()
                for (doc in cards.documents) {
                    batch.update(doc.reference, mapOf("published" to published, "updatedAt" to FieldValue.serverTimestamp()))
                }
                val questions = deckRef.collection("questions").whereEqualTo("teacherId", uid).get().await()
                for (doc in questions.documents) {
                    batch.update(doc.reference, mapOf("published" to published, "updatedAt" to FieldValue.serverTimestamp()))
                }
                val videos = deckRef.collection("videos").whereEqualTo("teacherId", uid).get().await()
                for (doc in videos.documents) {
                    batch.update(doc.reference, mapOf("published" to published, "updatedAt" to FieldValue.serverTimestamp()))
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDeck(deckId: String): Result<Unit> {
        if (isGuest) return GuestStudyStore.deleteDeck(deckId)
        val docPath = "decks/$deckId"
        return try {
            val uid = requireUserId()
            val deckRef = db.collection("decks").document(deckId)
            val cards = deckRef.collection("flashcards").whereEqualTo("teacherId", uid).get().await()
            val questions = deckRef.collection("questions").whereEqualTo("teacherId", uid).get().await()
            val videos = deckRef.collection("videos").whereEqualTo("teacherId", uid).get().await()
            val progressDocs = deckRef.collection("progress").whereEqualTo("teacherId", uid).get().await()

            val allRefs = cards.documents.map { it.reference } +
                questions.documents.map { it.reference } +
                videos.documents.map { it.reference } +
                progressDocs.documents.map { it.reference } +
                deckRef

            allRefs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addFlashcard(
        deckId: String,
        front: String,
        back: String,
        hint: String,
        published: Boolean,
        orderIndex: Int,
    ): Result<Unit> {
        if (isGuest) return GuestStudyStore.addFlashcard(deckId, front, back, hint, published, orderIndex)
        var docPath = "decks/$deckId/flashcards"
        return try {
            val uid = requireUserId()
            val cleanFront = front.trim()
            val cleanBack = back.trim()
            val cleanHint = hint.trim()
            require(cleanFront.isNotEmpty() && cleanFront.length <= 300) { "Front text must be 1–300 characters." }
            require(cleanBack.isNotEmpty() && cleanBack.length <= 500) { "Back text must be 1–500 characters." }
            require(cleanHint.length <= 150) { "Hint must be 150 characters or fewer." }

            val deckRef = db.collection("decks").document(deckId)
            val cardRef = deckRef.collection("flashcards").document()
            docPath = cardRef.path

            val batch = db.batch()
            batch.set(
                cardRef,
                mapOf(
                    "deckId" to deckId,
                    "teacherId" to uid,
                    "front" to cleanFront,
                    "back" to cleanBack,
                    "hint" to cleanHint,
                    "orderIndex" to orderIndex.toLong(),
                    "published" to published,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.update(
                deckRef,
                mapOf(
                    "flashcardCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateFlashcard(
        deckId: String,
        cardId: String,
        front: String,
        back: String,
        hint: String,
        orderIndex: Int,
    ): Result<Unit> {
        if (isGuest) return GuestStudyStore.updateFlashcard(deckId, cardId, front, back, hint, orderIndex)
        val docPath = "decks/$deckId/flashcards/$cardId"
        return try {
            requireUserId()
            val cleanFront = front.trim()
            val cleanBack = back.trim()
            val cleanHint = hint.trim()
            require(cleanFront.isNotEmpty() && cleanFront.length <= 300) { "Front text must be 1–300 characters." }
            require(cleanBack.isNotEmpty() && cleanBack.length <= 500) { "Back text must be 1–500 characters." }
            require(cleanHint.length <= 150) { "Hint must be 150 characters or fewer." }

            db.collection("decks").document(deckId).collection("flashcards").document(cardId)
                .update(
                    mapOf(
                        "front" to cleanFront,
                        "back" to cleanBack,
                        "hint" to cleanHint,
                        "orderIndex" to orderIndex.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFlashcard(deckId: String, cardId: String, currentCount: Long): Result<Unit> {
        if (isGuest) return GuestStudyStore.deleteFlashcard(deckId, cardId)
        val docPath = "decks/$deckId/flashcards/$cardId"
        return try {
            requireUserId()
            val deckRef = db.collection("decks").document(deckId)
            val cardRef = deckRef.collection("flashcards").document(cardId)
            val batch = db.batch()
            batch.delete(cardRef)
            batch.update(
                deckRef,
                mapOf(
                    "flashcardCount" to max(0L, currentCount - 1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addQuestion(
        deckId: String,
        prompt: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
        published: Boolean,
        orderIndex: Int,
    ): Result<Unit> {
        if (isGuest) return GuestStudyStore.addQuestion(deckId, prompt, options, correctIndex, explanation, published, orderIndex)
        var docPath = "decks/$deckId/questions"
        return try {
            val uid = requireUserId()
            val cleanPrompt = prompt.trim()
            val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
            val cleanExplanation = explanation.trim()
            require(cleanPrompt.isNotEmpty() && cleanPrompt.length <= 300) { "Question prompt must be 1–300 characters." }
            require(cleanOptions.size in 2..4) { "Provide 2 to 4 answer options." }
            require(cleanOptions.all { it.length <= 150 }) { "Each option must be 150 characters or fewer." }
            require(correctIndex in cleanOptions.indices) { "Correct answer index out of range." }
            require(cleanExplanation.length <= 400) { "Explanation must be 400 characters or fewer." }

            val deckRef = db.collection("decks").document(deckId)
            val questionRef = deckRef.collection("questions").document()
            docPath = questionRef.path

            val batch = db.batch()
            batch.set(
                questionRef,
                mapOf(
                    "deckId" to deckId,
                    "teacherId" to uid,
                    "prompt" to cleanPrompt,
                    "options" to cleanOptions,
                    "correctIndex" to correctIndex.toLong(),
                    "explanation" to cleanExplanation,
                    "orderIndex" to orderIndex.toLong(),
                    "published" to published,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.update(
                deckRef,
                mapOf(
                    "quizCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateQuestion(
        deckId: String,
        questionId: String,
        prompt: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
        orderIndex: Int,
    ): Result<Unit> {
        if (isGuest) return GuestStudyStore.updateQuestion(deckId, questionId, prompt, options, correctIndex, explanation, orderIndex)
        val docPath = "decks/$deckId/questions/$questionId"
        return try {
            requireUserId()
            val cleanPrompt = prompt.trim()
            val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
            val cleanExplanation = explanation.trim()
            require(cleanPrompt.isNotEmpty() && cleanPrompt.length <= 300) { "Question prompt must be 1–300 characters." }
            require(cleanOptions.size in 2..4) { "Provide 2 to 4 answer options." }
            require(cleanOptions.all { it.length <= 150 }) { "Each option must be 150 characters or fewer." }
            require(correctIndex in cleanOptions.indices) { "Correct answer index out of range." }
            require(cleanExplanation.length <= 400) { "Explanation must be 400 characters or fewer." }

            db.collection("decks").document(deckId).collection("questions").document(questionId)
                .update(
                    mapOf(
                        "prompt" to cleanPrompt,
                        "options" to cleanOptions,
                        "correctIndex" to correctIndex.toLong(),
                        "explanation" to cleanExplanation,
                        "orderIndex" to orderIndex.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteQuestion(deckId: String, questionId: String, currentCount: Long): Result<Unit> {
        if (isGuest) return GuestStudyStore.deleteQuestion(deckId, questionId)
        val docPath = "decks/$deckId/questions/$questionId"
        return try {
            requireUserId()
            val deckRef = db.collection("decks").document(deckId)
            val questionRef = deckRef.collection("questions").document(questionId)
            val batch = db.batch()
            batch.delete(questionRef)
            batch.update(
                deckRef,
                mapOf(
                    "quizCount" to max(0L, currentCount - 1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- STUDENT ENROLLMENT & PROGRESS TRACKING ---

    suspend fun joinDeckByCode(rawCode: String): Result<StudyDeck> {
        if (isGuest) return GuestStudyStore.joinDeckByCode(rawCode)
        val path = "decks"
        return try {
            requireUserId()
            val code = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
            require(code.length in 4..12) { "Enter a valid 4–12 character join code." }

            val querySnapshot = db.collection("decks")
                .whereEqualTo("published", true)
                .whereEqualTo("joinCode", code)
                .limit(1)
                .get()
                .await()

            val deck = querySnapshot.documents.firstOrNull()
                ?.toObject(StudyDeck::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw IllegalArgumentException("No published deck found for code $code.")

            enrollInDeck(deck).getOrThrow()
            Result.success(deck)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun enrollInDeck(deck: StudyDeck): Result<Unit> {
        if (isGuest) return GuestStudyStore.enrollInDeck(deck)
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/enrollments/${deck.id}"
        return try {
            val verifiedUid = requireUserId()
            require(deck.id.isNotBlank()) { "Invalid deck." }
            val enrollRef = db.collection("users").document(verifiedUid)
                .collection("enrollments").document(deck.id)
            val snap = enrollRef.get().await()
            if (!snap.exists()) {
                enrollRef.set(
                    mapOf(
                        "deckId" to deck.id,
                        "deckTitle" to deck.title.take(100).ifBlank { "Untitled Deck" },
                        "subject" to deck.subject.take(50).ifBlank { "General" },
                        "teacherName" to deck.teacherName.take(80).ifBlank { "Instructor" },
                        "accentColorKey" to DeckAccent.fromRaw(deck.accentColorKey).name,
                        "masteredCount" to 0L,
                        "totalCards" to deck.flashcardCount.coerceIn(0L, 1000L),
                        "bestQuizPercent" to 0L,
                        "quizzesTaken" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordFlashcardSession(
        deck: StudyDeck,
        masteredCardIds: List<String>,
        reviewedCount: Int,
        knownCount: Int,
        totalCardsInDeck: Int,
    ): Result<Unit> {
        if (isGuest) {
            return GuestStudyStore.recordFlashcardSession(
                deck = deck,
                masteredCardIds = masteredCardIds,
                reviewedCount = reviewedCount,
                knownCount = knownCount,
                totalCardsInDeck = totalCardsInDeck,
            )
        }
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val progressPath = "decks/${deck.id}/progress/$uid"
        return try {
            val verifiedUid = requireUserId()
            ensureUserProfile(verifiedUid, auth.currentUser?.displayName).getOrThrow()

            val cleanMastered = masteredCardIds.distinct().take(200)
            val safeTotal = max(totalCardsInDeck, cleanMastered.size).coerceIn(1, 1000)
            val sessionTotal = max(reviewedCount, 1).coerceIn(1, 1000)
            val sessionKnown = knownCount.coerceIn(0, sessionTotal)
            val sessionPercent = ((sessionKnown.toFloat() / sessionTotal.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            val xpEarned = (sessionKnown * 10 + reviewedCount * 3).coerceIn(5, 10000).toLong()

            val progressRef = db.collection("decks").document(deck.id)
                .collection("progress").document(verifiedUid)
            val existingProgress = progressRef.get().await()

            val enrollRef = db.collection("users").document(verifiedUid)
                .collection("enrollments").document(deck.id)
            val existingEnroll = enrollRef.get().await()

            val logRef = db.collection("users").document(verifiedUid)
                .collection("studyLogs").document()
            val userRef = db.collection("users").document(verifiedUid)

            val batch = db.batch()
            if (!existingProgress.exists()) {
                batch.set(
                    progressRef,
                    mapOf(
                        "deckId" to deck.id,
                        "teacherId" to deck.teacherId,
                        "studentId" to verifiedUid,
                        "studentName" to currentDisplayName(),
                        "masteredCardIds" to cleanMastered,
                        "reviewedCardsCount" to reviewedCount.coerceAtLeast(0).toLong(),
                        "totalCardsCount" to safeTotal.toLong(),
                        "quizzesTaken" to 0L,
                        "bestQuizPercent" to 0L,
                        "lastQuizPercent" to 0L,
                        "lastQuizCorrect" to 0L,
                        "lastQuizTotal" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                val prevMastered = (existingProgress.get("masteredCardIds") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?: emptyList()
                val mergedMastered = (prevMastered + cleanMastered).distinct().take(200)
                val prevReviewed = existingProgress.getLong("reviewedCardsCount") ?: 0L
                batch.update(
                    progressRef,
                    mapOf(
                        "studentName" to currentDisplayName(),
                        "masteredCardIds" to mergedMastered,
                        "reviewedCardsCount" to prevReviewed + reviewedCount.coerceAtLeast(0).toLong(),
                        "totalCardsCount" to max(safeTotal, mergedMastered.size).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            if (!existingEnroll.exists()) {
                batch.set(
                    enrollRef,
                    mapOf(
                        "deckId" to deck.id,
                        "deckTitle" to deck.title.take(100),
                        "subject" to deck.subject.take(50),
                        "teacherName" to deck.teacherName.take(80),
                        "accentColorKey" to DeckAccent.fromRaw(deck.accentColorKey).name,
                        "masteredCount" to cleanMastered.size.toLong(),
                        "totalCards" to safeTotal.toLong(),
                        "bestQuizPercent" to 0L,
                        "quizzesTaken" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                val prevBestMastered = (existingEnroll.getLong("masteredCount") ?: 0L).toInt()
                val updatedMastered = max(prevBestMastered, cleanMastered.size).coerceAtMost(safeTotal)
                batch.update(
                    enrollRef,
                    mapOf(
                        "deckTitle" to deck.title.take(100),
                        "subject" to deck.subject.take(50),
                        "teacherName" to deck.teacherName.take(80),
                        "accentColorKey" to DeckAccent.fromRaw(deck.accentColorKey).name,
                        "masteredCount" to updatedMastered.toLong(),
                        "totalCards" to safeTotal.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            batch.set(
                logRef,
                mapOf(
                    "deckId" to deck.id,
                    "deckTitle" to deck.title.take(100),
                    "mode" to StudyMode.FLASHCARDS.name,
                    "scorePercent" to sessionPercent.toLong(),
                    "correctCount" to sessionKnown.toLong(),
                    "totalCount" to sessionTotal.toLong(),
                    "xpEarned" to xpEarned,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )

            batch.update(
                userRef,
                mapOf(
                    "totalXp" to FieldValue.increment(xpEarned),
                    "cardsReviewed" to FieldValue.increment(reviewedCount.coerceAtLeast(0).toLong()),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, progressPath)
            Result.failure(e)
        }
    }

    suspend fun recordQuizResult(
        deck: StudyDeck,
        correctCount: Int,
        totalCount: Int,
    ): Result<Unit> {
        if (isGuest) return GuestStudyStore.recordQuizResult(deck, correctCount, totalCount)
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val progressPath = "decks/${deck.id}/progress/$uid"
        return try {
            val verifiedUid = requireUserId()
            ensureUserProfile(verifiedUid, auth.currentUser?.displayName).getOrThrow()

            val safeTotal = totalCount.coerceIn(1, 1000)
            val safeCorrect = correctCount.coerceIn(0, safeTotal)
            val percent = ((safeCorrect.toFloat() / safeTotal.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            val xpEarned = (safeCorrect * 20 + 15).coerceIn(15, 10000).toLong()

            val progressRef = db.collection("decks").document(deck.id)
                .collection("progress").document(verifiedUid)
            val existingProgress = progressRef.get().await()

            val enrollRef = db.collection("users").document(verifiedUid)
                .collection("enrollments").document(deck.id)
            val existingEnroll = enrollRef.get().await()

            val logRef = db.collection("users").document(verifiedUid)
                .collection("studyLogs").document()
            val userRef = db.collection("users").document(verifiedUid)

            val batch = db.batch()
            if (!existingProgress.exists()) {
                batch.set(
                    progressRef,
                    mapOf(
                        "deckId" to deck.id,
                        "teacherId" to deck.teacherId,
                        "studentId" to verifiedUid,
                        "studentName" to currentDisplayName(),
                        "masteredCardIds" to emptyList<String>(),
                        "reviewedCardsCount" to 0L,
                        "totalCardsCount" to deck.flashcardCount.coerceIn(0L, 1000L),
                        "quizzesTaken" to 1L,
                        "bestQuizPercent" to percent.toLong(),
                        "lastQuizPercent" to percent.toLong(),
                        "lastQuizCorrect" to safeCorrect.toLong(),
                        "lastQuizTotal" to safeTotal.toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                val prevBest = existingProgress.getLong("bestQuizPercent") ?: 0L
                val prevTaken = existingProgress.getLong("quizzesTaken") ?: 0L
                batch.update(
                    progressRef,
                    mapOf(
                        "studentName" to currentDisplayName(),
                        "quizzesTaken" to prevTaken + 1L,
                        "bestQuizPercent" to max(prevBest, percent.toLong()),
                        "lastQuizPercent" to percent.toLong(),
                        "lastQuizCorrect" to safeCorrect.toLong(),
                        "lastQuizTotal" to safeTotal.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            if (!existingEnroll.exists()) {
                batch.set(
                    enrollRef,
                    mapOf(
                        "deckId" to deck.id,
                        "deckTitle" to deck.title.take(100),
                        "subject" to deck.subject.take(50),
                        "teacherName" to deck.teacherName.take(80),
                        "accentColorKey" to DeckAccent.fromRaw(deck.accentColorKey).name,
                        "masteredCount" to 0L,
                        "totalCards" to deck.flashcardCount.coerceIn(0L, 1000L),
                        "bestQuizPercent" to percent.toLong(),
                        "quizzesTaken" to 1L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                val prevBest = existingEnroll.getLong("bestQuizPercent") ?: 0L
                val prevTaken = existingEnroll.getLong("quizzesTaken") ?: 0L
                batch.update(
                    enrollRef,
                    mapOf(
                        "deckTitle" to deck.title.take(100),
                        "subject" to deck.subject.take(50),
                        "teacherName" to deck.teacherName.take(80),
                        "accentColorKey" to DeckAccent.fromRaw(deck.accentColorKey).name,
                        "bestQuizPercent" to max(prevBest, percent.toLong()),
                        "quizzesTaken" to prevTaken + 1L,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            batch.set(
                logRef,
                mapOf(
                    "deckId" to deck.id,
                    "deckTitle" to deck.title.take(100),
                    "mode" to StudyMode.QUIZ.name,
                    "scorePercent" to percent.toLong(),
                    "correctCount" to safeCorrect.toLong(),
                    "totalCount" to safeTotal.toLong(),
                    "xpEarned" to xpEarned,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )

            batch.update(
                userRef,
                mapOf(
                    "totalXp" to FieldValue.increment(xpEarned),
                    "quizzesCompleted" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, progressPath)
            Result.failure(e)
        }
    }

    // --- LIVE VIDEO CLASS SESSION ---

    fun observeLiveSession(deckId: String): Flow<LiveSession?> {
        if (isGuest) return GuestStudyStore.observeLiveSession(deckId)
        return db.collection("decks").document(deckId)
            .collection("liveSessions").document("active")
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(LiveSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .resilientFirestoreStream(OperationType.GET, "decks/$deckId/liveSessions/active")
    }

    suspend fun updateLiveSession(
        deck: StudyDeck,
        active: Boolean,
        itemType: LiveItemType,
        itemIndex: Int,
        activePrompt: String,
    ): Result<Unit> {
        if (isGuest) return GuestStudyStore.updateLiveSession(deck, active, itemType, itemIndex, activePrompt)
        val docPath = "decks/${deck.id}/liveSessions/active"
        return try {
            val uid = requireUserId()
            check(deck.teacherId == uid) { "Only the deck instructor can control live video class." }
            val sessionRef = db.collection("decks").document(deck.id)
                .collection("liveSessions").document("active")
            val existing = sessionRef.get().await()
            val cleanPrompt = activePrompt.trim().take(300)
            val safeIndex = itemIndex.coerceIn(0, 10000).toLong()

            if (!existing.exists()) {
                sessionRef.set(
                    mapOf(
                        "deckId" to deck.id,
                        "teacherId" to uid,
                        "teacherName" to currentDisplayName(),
                        "active" to active,
                        "published" to deck.published,
                        "activeItemType" to itemType.name,
                        "activeItemIndex" to safeIndex,
                        "activePrompt" to cleanPrompt,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                sessionRef.update(
                    mapOf(
                        "teacherName" to currentDisplayName(),
                        "active" to active,
                        "published" to deck.published,
                        "activeItemType" to itemType.name,
                        "activeItemIndex" to safeIndex,
                        "activePrompt" to cleanPrompt,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    // --- STUDY VIDEOS ---

    suspend fun addVideo(
        deckId: String,
        videoId: String,
        title: String,
        notes: String,
        fileName: String,
        durationMs: Long,
        fileSizeBytes: Long,
        published: Boolean,
    ): Result<Unit> {
        if (isGuest) {
            return GuestStudyStore.addVideo(
                deckId = deckId,
                videoId = videoId,
                title = title,
                notes = notes,
                fileName = fileName,
                durationMs = durationMs,
                fileSizeBytes = fileSizeBytes,
                published = published,
            )
        }
        val docPath = "decks/$deckId/videos/$videoId"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim()
            val cleanNotes = notes.trim()
            require(videoId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) { "Invalid video ID." }
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) { "Video title must be 1–100 characters." }
            require(cleanNotes.length <= 300) { "Notes must be 300 characters or fewer." }
            require(fileName.matches(Regex("^[a-zA-Z0-9_.\\-]{1,128}$"))) { "Invalid video file name." }

            db.collection("decks").document(deckId)
                .collection("videos").document(videoId)
                .set(
                    mapOf(
                        "deckId" to deckId,
                        "teacherId" to uid,
                        "teacherName" to currentDisplayName(),
                        "title" to cleanTitle,
                        "notes" to cleanNotes,
                        "fileName" to fileName,
                        "durationMs" to durationMs.coerceIn(0L, 14_400_000L),
                        "fileSizeBytes" to fileSizeBytes.coerceIn(0L, 2_000_000_000L),
                        "published" to published,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteVideo(deckId: String, videoId: String): Result<Unit> {
        if (isGuest) return GuestStudyStore.deleteVideo(deckId, videoId)
        val docPath = "decks/$deckId/videos/$videoId"
        return try {
            requireUserId()
            db.collection("decks").document(deckId)
                .collection("videos").document(videoId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- STUDY WALLET & TRANSACTIONS ---

    fun observeWallet(userId: String): Flow<StudyWallet?> {
        if (isGuest) return GuestStudyStore.observeWallet()
        return db.collection("users").document(userId)
            .collection("wallet").document("summary")
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(StudyWallet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .resilientFirestoreStream(OperationType.GET, "users/$userId/wallet/summary", userId)
    }

    fun observeTransactions(userId: String): Flow<List<WalletTransaction>> {
        if (isGuest) return GuestStudyStore.observeTransactions()
        return db.collection("users").document(userId)
            .collection("transactions")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(WalletTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "users/$userId/transactions", userId)
    }

    suspend fun topUpWallet(amountInr: Long, paymentMethod: String): Result<Unit> {
        if (isGuest) return GuestStudyStore.topUpWallet(amountInr, paymentMethod)
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val walletPath = "users/$uid/wallet/summary"
        return try {
            val verifiedUid = requireUserId()
            require(amountInr in 1L..1_000_000L) { "Enter an amount between ₹1 and ₹10,00,000." }
            val safeMethod = when (paymentMethod) {
                "UPI", "CARD_TOPUP", "NETBANKING_TOPUP" -> paymentMethod
                else -> "UPI"
            }

            val walletRef = db.collection("users").document(verifiedUid)
                .collection("wallet").document("summary")
            val existingWallet = walletRef.get().await()
            val txRef = db.collection("users").document(verifiedUid)
                .collection("transactions").document()

            val batch = db.batch()
            if (!existingWallet.exists()) {
                batch.set(
                    walletRef,
                    mapOf(
                        "userId" to verifiedUid,
                        "balanceInr" to amountInr,
                        "totalAddedInr" to amountInr,
                        "totalSpentInr" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                val prevBalance = existingWallet.getLong("balanceInr") ?: 0L
                require(prevBalance + amountInr <= 10_000_000L) { "Maximum wallet balance limit reached." }
                batch.update(
                    walletRef,
                    mapOf(
                        "balanceInr" to FieldValue.increment(amountInr),
                        "totalAddedInr" to FieldValue.increment(amountInr),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            batch.set(
                txRef,
                mapOf(
                    "userId" to verifiedUid,
                    "type" to WalletTxType.TOP_UP.name,
                    "amountInr" to amountInr,
                    "title" to "Added money to Study Wallet",
                    "batchId" to "",
                    "paymentMethod" to safeMethod,
                    "status" to "COMPLETED",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, walletPath)
            Result.failure(e)
        }
    }

    // --- STUDY BATCHES & ENROLLMENT ---

    fun observeTeacherBatches(teacherId: String): Flow<List<StudyBatch>> {
        if (isGuest) return GuestStudyStore.observeTeacherBatches()
        return db.collection("batches")
            .whereEqualTo("teacherId", teacherId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(StudyBatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "batches", teacherId)
    }

    fun observePublishedBatches(): Flow<List<StudyBatch>> {
        if (isGuest) return GuestStudyStore.observePublishedBatches()
        return db.collection("batches")
            .whereEqualTo("published", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(StudyBatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "batches")
    }

    fun observeBatch(batchId: String): Flow<StudyBatch?> {
        if (isGuest) return GuestStudyStore.observeBatch(batchId)
        return db.collection("batches").document(batchId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(StudyBatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .resilientFirestoreStream(OperationType.GET, "batches/$batchId")
    }

    fun observeBatchEnrollments(userId: String): Flow<List<BatchEnrollment>> {
        if (isGuest) return GuestStudyStore.observeBatchEnrollments()
        return db.collection("users").document(userId)
            .collection("batchEnrollments")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(BatchEnrollment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "users/$userId/batchEnrollments", userId)
    }

    fun observeBatchMembers(batchId: String, teacherId: String): Flow<List<BatchMember>> {
        if (isGuest) return GuestStudyStore.observeBatchMembers(batchId)
        return db.collection("batches").document(batchId)
            .collection("members")
            .whereEqualTo("teacherId", teacherId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(BatchMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt }
            }
            .resilientFirestoreStream(OperationType.LIST, "batches/$batchId/members", teacherId)
    }

    suspend fun createBatch(
        title: String,
        subject: String,
        description: String,
        scheduleText: String,
        priceInr: Long,
        upiId: String,
        coverImageData: String,
        accent: DeckAccent,
        published: Boolean,
    ): Result<String> {
        if (isGuest) {
            return GuestStudyStore.createBatch(
                title = title,
                subject = subject,
                description = description,
                scheduleText = scheduleText,
                priceInr = priceInr,
                upiId = upiId,
                coverImageData = coverImageData,
                accent = accent,
                published = published,
            )
        }
        var docPath = "batches"
        return try {
            val uid = requireUserId()
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

            val docRef = db.collection("batches").document()
            docPath = docRef.path
            val batchCode = generateJoinCode()

            docRef.set(
                mapOf(
                    "teacherId" to uid,
                    "teacherName" to currentDisplayName(),
                    "title" to cleanTitle,
                    "subject" to cleanSubject,
                    "description" to cleanDesc,
                    "scheduleText" to cleanSchedule,
                    "batchCode" to batchCode,
                    "priceInr" to priceInr,
                    "upiId" to cleanUpi,
                    "coverImageData" to coverImageData,
                    "accentColorKey" to accent.name,
                    "published" to published,
                    "enrolledCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateBatch(
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
    ): Result<Unit> {
        if (isGuest) {
            return GuestStudyStore.updateBatch(
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
            )
        }
        val docPath = "batches/$batchId"
        return try {
            requireUserId()
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

            db.collection("batches").document(batchId).update(
                mapOf(
                    "title" to cleanTitle,
                    "subject" to cleanSubject,
                    "description" to cleanDesc,
                    "scheduleText" to cleanSchedule,
                    "priceInr" to priceInr,
                    "upiId" to cleanUpi,
                    "coverImageData" to coverImageData,
                    "accentColorKey" to accent.name,
                    "published" to published,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBatch(batchId: String): Result<Unit> {
        if (isGuest) return GuestStudyStore.deleteBatch(batchId)
        val docPath = "batches/$batchId"
        return try {
            val uid = requireUserId()
            val batchRef = db.collection("batches").document(batchId)
            val members = batchRef.collection("members").whereEqualTo("teacherId", uid).get().await()
            val allRefs = members.documents.map { it.reference } + batchRef
            allRefs.chunked(400).forEach { chunk ->
                val writeBatch = db.batch()
                chunk.forEach { writeBatch.delete(it) }
                writeBatch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun payAndEnrollBatch(
        studyBatch: StudyBatch,
        preferredMethod: String = "WALLET",
    ): Result<Unit> {
        if (isGuest) return GuestStudyStore.payAndEnrollBatch(studyBatch, preferredMethod)
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/batchEnrollments/${studyBatch.id}"
        return try {
            val verifiedUid = requireUserId()
            require(studyBatch.id.isNotBlank()) { "Invalid batch." }

            val enrollRef = db.collection("users").document(verifiedUid)
                .collection("batchEnrollments").document(studyBatch.id)
            if (enrollRef.get().await().exists()) {
                return Result.success(Unit)
            }

            val fee = studyBatch.priceInr.coerceIn(0L, 100_000L)
            val actualMethod = when {
                fee == 0L -> "FREE"
                preferredMethod == "UPI" -> "UPI"
                else -> "WALLET"
            }

            val walletRef = db.collection("users").document(verifiedUid)
                .collection("wallet").document("summary")
            if (actualMethod == "WALLET") {
                val walletSnap = walletRef.get().await()
                val currentBalance = walletSnap.getLong("balanceInr") ?: 0L
                if (!walletSnap.exists() || currentBalance < fee) {
                    throw IllegalStateException(
                        "Insufficient Study Wallet balance (₹$currentBalance). Add ₹${fee - currentBalance} to join."
                    )
                }
            }

            val writeBatch = db.batch()
            var txId = ""
            if (fee > 0L) {
                val txRef = db.collection("users").document(verifiedUid)
                    .collection("transactions").document()
                txId = txRef.id

                if (actualMethod == "WALLET") {
                    writeBatch.update(
                        walletRef,
                        mapOf(
                            "balanceInr" to FieldValue.increment(-fee),
                            "totalSpentInr" to FieldValue.increment(fee),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }

                writeBatch.set(
                    txRef,
                    mapOf(
                        "userId" to verifiedUid,
                        "type" to WalletTxType.BATCH_PAYMENT.name,
                        "amountInr" to fee,
                        "title" to "Joined ${studyBatch.title}".take(120),
                        "batchId" to studyBatch.id,
                        "paymentMethod" to actualMethod,
                        "status" to "COMPLETED",
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            writeBatch.set(
                enrollRef,
                mapOf(
                    "batchId" to studyBatch.id,
                    "batchTitle" to studyBatch.title.take(100).ifBlank { "Study Batch" },
                    "subject" to studyBatch.subject.take(50).ifBlank { "General" },
                    "teacherId" to studyBatch.teacherId,
                    "teacherName" to studyBatch.teacherName.take(80).ifBlank { "Instructor" },
                    "scheduleText" to studyBatch.scheduleText.take(100),
                    "accentColorKey" to DeckAccent.fromRaw(studyBatch.accentColorKey).name,
                    "coverImageData" to studyBatch.coverImageData.take(150_000),
                    "amountPaidInr" to fee,
                    "paymentMethod" to actualMethod,
                    "transactionId" to txId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )

            val memberRef = db.collection("batches").document(studyBatch.id)
                .collection("members").document(verifiedUid)
            if (!memberRef.get().await().exists()) {
                writeBatch.set(
                    memberRef,
                    mapOf(
                        "batchId" to studyBatch.id,
                        "teacherId" to studyBatch.teacherId,
                        "studentId" to verifiedUid,
                        "studentName" to currentDisplayName(),
                        "amountPaidInr" to fee,
                        "paymentMethod" to actualMethod,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                writeBatch.update(
                    db.collection("batches").document(studyBatch.id),
                    mapOf(
                        "enrolledCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            writeBatch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun findBatchByCode(rawCode: String): Result<StudyBatch> {
        if (isGuest) return GuestStudyStore.findBatchByCode(rawCode)
        val path = "batches"
        return try {
            requireUserId()
            val code = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
            require(code.length in 4..12) { "Enter a valid 4–12 character batch code." }

            val querySnapshot = db.collection("batches")
                .whereEqualTo("published", true)
                .whereEqualTo("batchCode", code)
                .limit(1)
                .get()
                .await()

            val batch = querySnapshot.documents.firstOrNull()
                ?.toObject(StudyBatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw IllegalArgumentException("No published batch found for code $code.")

            Result.success(batch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun fetchFlashcardsForDeck(deckId: String, isOwner: Boolean): Result<List<Flashcard>> {
        if (isGuest) return GuestStudyStore.fetchFlashcardsForDeck(deckId)
        val path = "decks/$deckId/flashcards"
        return try {
            val uid = requireUserId()
            require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
            val base = db.collection("decks").document(deckId).collection("flashcards")
            val query = if (isOwner) {
                base.whereEqualTo("teacherId", uid)
            } else {
                base.whereEqualTo("published", true)
            }
            val snap = query.get().await()
            val cards = snap.toObjects(Flashcard::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedWith(compareBy<Flashcard> { it.orderIndex }.thenBy { it.createdAt })
            Result.success(cards)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }
}

