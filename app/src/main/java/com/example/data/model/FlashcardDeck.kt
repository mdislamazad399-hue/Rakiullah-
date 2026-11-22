package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FlashcardDeck(
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
