package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole {
    TEACHER,
    STUDENT;

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: TEACHER
    }
}

enum class StudyMode {
    QUIZ,
    FLASHCARDS;

    companion object {
        fun fromRaw(raw: String?): StudyMode =
            entries.firstOrNull { it.name == raw } ?: QUIZ
    }
}

enum class DeckAccent(val label: String) {
    INDIGO("Indigo"),
    TERRACOTTA("Terracotta"),
    SAGE("Sage"),
    AMBER("Amber"),
    PLUM("Plum");

    companion object {
        fun fromRaw(raw: String?): DeckAccent =
            entries.firstOrNull { it.name == raw } ?: INDIGO
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val role: String = UserRole.TEACHER.name,
    val totalXp: Long = 0L,
    val quizzesCompleted: Long = 0L,
    val cardsReviewed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StudyDeck(
    @DocumentId val id: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val title: String = "",
    val subject: String = "",
    val description: String = "",
    val joinCode: String = "",
    val accentColorKey: String = DeckAccent.INDIGO.name,
    val published: Boolean = true,
    val flashcardCount: Long = 0L,
    val quizCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Flashcard(
    @DocumentId val id: String = "",
    val deckId: String = "",
    val teacherId: String = "",
    val front: String = "",
    val back: String = "",
    val hint: String = "",
    val orderIndex: Long = 0L,
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class QuizQuestion(
    @DocumentId val id: String = "",
    val deckId: String = "",
    val teacherId: String = "",
    val prompt: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Long = 0L,
    val explanation: String = "",
    val orderIndex: Long = 0L,
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DeckProgress(
    @DocumentId val id: String = "",
    val deckId: String = "",
    val teacherId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val masteredCardIds: List<String> = emptyList(),
    val reviewedCardsCount: Long = 0L,
    val totalCardsCount: Long = 0L,
    val quizzesTaken: Long = 0L,
    val bestQuizPercent: Long = 0L,
    val lastQuizPercent: Long = 0L,
    val lastQuizCorrect: Long = 0L,
    val lastQuizTotal: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Enrollment(
    @DocumentId val id: String = "",
    val deckId: String = "",
    val deckTitle: String = "",
    val subject: String = "",
    val teacherName: String = "",
    val accentColorKey: String = DeckAccent.INDIGO.name,
    val masteredCount: Long = 0L,
    val totalCards: Long = 0L,
    val bestQuizPercent: Long = 0L,
    val quizzesTaken: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StudyLog(
    @DocumentId val id: String = "",
    val deckId: String = "",
    val deckTitle: String = "",
    val mode: String = StudyMode.QUIZ.name,
    val scorePercent: Long = 0L,
    val correctCount: Long = 0L,
    val totalCount: Long = 0L,
    val xpEarned: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

enum class LiveItemType {
    NONE,
    FLASHCARD,
    QUIZ;

    companion object {
        fun fromRaw(raw: String?): LiveItemType =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

@Keep
@IgnoreExtraProperties
data class LiveSession(
    @DocumentId val id: String = "",
    val deckId: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val active: Boolean = false,
    val published: Boolean = true,
    val activeItemType: String = LiveItemType.NONE.name,
    val activeItemIndex: Long = 0L,
    val activePrompt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StudyVideo(
    @DocumentId val id: String = "",
    val deckId: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val title: String = "",
    val notes: String = "",
    val fileName: String = "",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class WalletTxType {
    TOP_UP,
    BATCH_PAYMENT;

    companion object {
        fun fromRaw(raw: String?): WalletTxType =
            entries.firstOrNull { it.name == raw } ?: TOP_UP
    }
}

@Keep
@IgnoreExtraProperties
data class StudyWallet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val balanceInr: Long = 0L,
    val totalAddedInr: Long = 0L,
    val totalSpentInr: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class WalletTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = WalletTxType.TOP_UP.name,
    val amountInr: Long = 0L,
    val title: String = "",
    val batchId: String = "",
    val paymentMethod: String = "WALLET",
    val status: String = "COMPLETED",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StudyBatch(
    @DocumentId val id: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val title: String = "",
    val subject: String = "",
    val description: String = "",
    val scheduleText: String = "",
    val batchCode: String = "",
    val priceInr: Long = 0L,
    val upiId: String = "",
    val coverImageData: String = "",
    val accentColorKey: String = DeckAccent.INDIGO.name,
    val published: Boolean = true,
    val enrolledCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class BatchEnrollment(
    @DocumentId val id: String = "",
    val batchId: String = "",
    val batchTitle: String = "",
    val subject: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val scheduleText: String = "",
    val accentColorKey: String = DeckAccent.INDIGO.name,
    val coverImageData: String = "",
    val amountPaidInr: Long = 0L,
    val paymentMethod: String = "FREE",
    val transactionId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class BatchMember(
    @DocumentId val id: String = "",
    val batchId: String = "",
    val teacherId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val amountPaidInr: Long = 0L,
    val paymentMethod: String = "FREE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

