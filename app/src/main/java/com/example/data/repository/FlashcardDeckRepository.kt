package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.DeckAccent
import com.example.data.model.FlashcardDeck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FlashcardDeckRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
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
        return if (!name.isNullOrBlank()) name.take(80) else "Scholar"
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    /**
     * Real-time stream of flashcard decks owned by the authenticated user.
     */
    fun observeUserDecks(): Flow<List<FlashcardDeck>> = flow {
        val uid = requireUserId()
        val path = "decks"
        emitAll(
            db.collection(path)
                .whereEqualTo("teacherId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FlashcardDeck::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * One-shot fetch of flashcard decks belonging to the authenticated user.
     */
    suspend fun getUserDecks(): Result<List<FlashcardDeck>> {
        val path = "decks"
        return try {
            val uid = requireUserId()
            val snapshot = db.collection(path)
                .whereEqualTo("teacherId", uid)
                .get()
                .await()
            val decks = snapshot.toObjects(
                FlashcardDeck::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ).sortedByDescending { it.updatedAt ?: it.createdAt }
            Result.success(decks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Fetch a single flashcard deck by its document ID.
     */
    suspend fun getDeck(deckId: String): Result<FlashcardDeck?> {
        val path = "decks/$deckId"
        return try {
            requireUserId()
            require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
            val snapshot = db.collection("decks").document(deckId).get().await()
            val deck = if (snapshot.exists()) {
                snapshot.toObject(
                    FlashcardDeck::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            } else {
                null
            }
            Result.success(deck)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    /**
     * Add a new flashcard deck owned by the authenticated user.
     */
    suspend fun addDeck(deck: FlashcardDeck): Result<String> {
        var docPath = "decks"
        return try {
            val uid = requireUserId()
            val cleanTitle = deck.title.trim()
            val cleanSubject = deck.subject.trim().ifEmpty { "General" }
            val cleanDescription = deck.description.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) {
                "Title must be 1–100 characters."
            }
            require(cleanSubject.length <= 50) {
                "Subject must be 50 characters or fewer."
            }
            require(cleanDescription.length <= 300) {
                "Description must be 300 characters or fewer."
            }

            val cleanJoinCode = deck.joinCode.trim().uppercase()
                .takeIf { it.matches(Regex("^[A-Z0-9]{4,12}$")) }
                ?: generateJoinCode()

            val docRef = db.collection("decks").document()
            docPath = docRef.path

            val payload = mapOf(
                "teacherId" to uid,
                "teacherName" to deck.teacherName.trim().ifEmpty { currentDisplayName() }.take(80),
                "title" to cleanTitle,
                "subject" to cleanSubject,
                "description" to cleanDescription,
                "joinCode" to cleanJoinCode,
                "accentColorKey" to DeckAccent.fromRaw(deck.accentColorKey).name,
                "published" to deck.published,
                "flashcardCount" to deck.flashcardCount.coerceIn(0L, 1000L),
                "quizCount" to deck.quizCount.coerceIn(0L, 1000L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Update an existing flashcard deck while preserving immutable ownership and creation fields.
     */
    suspend fun updateDeck(
        deckId: String,
        title: String,
        subject: String,
        description: String,
        accentColorKey: String = DeckAccent.INDIGO.name,
        published: Boolean = true,
    ): Result<Unit> {
        val docPath = "decks/$deckId"
        return try {
            requireUserId()
            require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
            val cleanTitle = title.trim()
            val cleanSubject = subject.trim().ifEmpty { "General" }
            val cleanDescription = description.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) {
                "Title must be 1–100 characters."
            }
            require(cleanSubject.length <= 50) {
                "Subject must be 50 characters or fewer."
            }
            require(cleanDescription.length <= 300) {
                "Description must be 300 characters or fewer."
            }

            db.collection("decks").document(deckId).update(
                mapOf(
                    "title" to cleanTitle,
                    "subject" to cleanSubject,
                    "description" to cleanDescription,
                    "accentColorKey" to DeckAccent.fromRaw(accentColorKey).name,
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
}
