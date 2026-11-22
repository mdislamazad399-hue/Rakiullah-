package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.media.StudyVideoStorage
import com.example.data.model.DeckProgress
import com.example.data.model.Flashcard
import com.example.data.model.LiveSession
import com.example.data.model.QuizQuestion
import com.example.data.model.StudyDeck
import com.example.data.model.StudyVideo
import com.example.data.model.UiState
import com.example.ui.components.CreateOrEditDeckDialog
import com.example.ui.components.FlashcardEditorDialog
import com.example.ui.components.JoinCodePill
import com.example.ui.components.QuestionEditorDialog
import com.example.ui.components.StudyVideoPlayerDialog
import com.example.ui.components.SubjectBadge
import com.example.ui.theme.deckAccentPalette
import com.example.ui.viewmodel.DeckDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    viewModel: DeckDetailViewModel,
    currentUserId: String,
    onBack: () -> Unit,
    onStartFlashcards: () -> Unit,
    onStartQuiz: () -> Unit,
    onStartLiveClass: () -> Unit,
    onAddFlashcard: () -> Unit,
    onUploadVideo: () -> Unit,
) {
    val deckState by viewModel.deck.collectAsStateWithLifecycle()
    val flashcardsState by viewModel.flashcards.collectAsStateWithLifecycle()
    val questionsState by viewModel.questions.collectAsStateWithLifecycle()
    val videosState by viewModel.videos.collectAsStateWithLifecycle()
    val myProgressState by viewModel.myProgress.collectAsStateWithLifecycle()
    val classProgressState by viewModel.classProgress.collectAsStateWithLifecycle()
    val liveSessionState by viewModel.liveSession.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDeckDialog by remember { mutableStateOf(false) }
    var showFlashcardDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var showQuestionDialog by remember { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf<QuizQuestion?>(null) }
    var playingVideo by remember { mutableStateOf<StudyVideo?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val currentDeck = (deckState as? UiState.Success)?.data
    val isTeacher = currentDeck?.teacherId == currentUserId
    val flashcards = (flashcardsState as? UiState.Success)?.data.orEmpty()
    val questions = (questionsState as? UiState.Success)?.data.orEmpty()
    val videos = (videosState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentDeck?.title ?: "Study Deck",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (isTeacher) {
                        IconButton(
                            onClick = { showEditDeckDialog = true },
                            modifier = Modifier.testTag("edit_deck_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit deck details",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (deckState) {
                UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = (deckState as UiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                is UiState.Success -> {
                    if (currentDeck == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Deck not found.")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            val activeLiveSession = (liveSessionState as? UiState.Success)?.data
                            if (activeLiveSession?.active == true) {
                                item {
                                    LiveClassActiveBanner(liveSession = activeLiveSession)
                                }
                            }
                            item {
                                DeckHeaderCard(
                                    deck = currentDeck,
                                    isTeacher = isTeacher,
                                    flashcardCount = flashcards.size,
                                    questionCount = questions.size,
                                    myProgress = (myProgressState as? UiState.Success)?.data,
                                    onCopyCode = {
                                        clipboard.setText(AnnotatedString(currentDeck.joinCode))
                                    },
                                    onShareCode = {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Join my StudyDeck \"${currentDeck.title}\" using code: ${currentDeck.joinCode}",
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Deck Code"))
                                    },
                                    onStartFlashcards = onStartFlashcards,
                                    onStartQuiz = onStartQuiz,
                                    onStartLiveClass = onStartLiveClass,
                                )
                            }

                            item {
                                val tabs = if (isTeacher) {
                                    listOf(
                                        "Cards (${flashcards.size})",
                                        "Quiz (${questions.size})",
                                        "Videos (${videos.size})",
                                        "Students",
                                    )
                                } else {
                                    listOf(
                                        "Cards (${flashcards.size})",
                                        "Quiz (${questions.size})",
                                        "Videos (${videos.size})",
                                    )
                                }
                                SecondaryTabRow(
                                    selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex),
                                    containerColor = MaterialTheme.colorScheme.background,
                                ) {
                                    tabs.forEachIndexed { idx, label ->
                                        Tab(
                                            selected = selectedTab == idx,
                                            onClick = { selectedTab = idx },
                                            text = {
                                                Text(
                                                    text = label,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            when (selectedTab) {
                                0 -> {
                                    if (isTeacher) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                Button(
                                                    onClick = onAddFlashcard,
                                                    modifier = Modifier
                                                        .height(48.dp)
                                                        .testTag("add_flashcard_button"),
                                                ) {
                                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Add Flashcard")
                                                }
                                            }
                                        }
                                    }
                                    if (flashcards.isEmpty()) {
                                        item {
                                            EmptySectionCard(
                                                title = "No Flashcards Yet",
                                                body = if (isTeacher) {
                                                    "Tap \"Add Flashcard\" to teach key terms and concepts."
                                                } else {
                                                    "Your teacher hasn't added flashcards to this deck yet."
                                                },
                                            )
                                        }
                                    } else {
                                        itemsIndexed(flashcards, key = { _, c -> c.id }) { idx, card ->
                                            FlashcardItemCard(
                                                index = idx + 1,
                                                card = card,
                                                isTeacher = isTeacher,
                                                onEdit = {
                                                    editingCard = card
                                                    showFlashcardDialog = true
                                                },
                                                onDelete = {
                                                    viewModel.deleteFlashcard(card.id, currentDeck.flashcardCount)
                                                },
                                            )
                                        }
                                    }
                                }
                                1 -> {
                                    if (isTeacher) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                Button(
                                                    onClick = {
                                                        editingQuestion = null
                                                        showQuestionDialog = true
                                                    },
                                                    modifier = Modifier.testTag("add_question_button"),
                                                ) {
                                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Add Question")
                                                }
                                            }
                                        }
                                    }
                                    if (questions.isEmpty()) {
                                        item {
                                            EmptySectionCard(
                                                title = "No Quiz Questions Yet",
                                                body = if (isTeacher) {
                                                    "Tap \"Add Question\" to build an interactive multiple-choice quiz."
                                                } else {
                                                    "Your teacher hasn't added quiz questions to this deck yet."
                                                },
                                            )
                                        }
                                    } else {
                                        itemsIndexed(questions, key = { _, q -> q.id }) { idx, q ->
                                            QuestionItemCard(
                                                index = idx + 1,
                                                question = q,
                                                isTeacher = isTeacher,
                                                onEdit = {
                                                    editingQuestion = q
                                                    showQuestionDialog = true
                                                },
                                                onDelete = {
                                                    viewModel.deleteQuestion(q.id, currentDeck.quizCount)
                                                },
                                            )
                                        }
                                    }
                                }
                                2 -> {
                                    if (isTeacher) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                Button(
                                                    onClick = onUploadVideo,
                                                    modifier = Modifier
                                                        .height(48.dp)
                                                        .testTag("deck_upload_video_button"),
                                                ) {
                                                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Upload Video")
                                                }
                                            }
                                        }
                                    }
                                    if (videos.isEmpty()) {
                                        item {
                                            EmptySectionCard(
                                                title = "No Study Videos Yet",
                                                body = if (isTeacher) {
                                                    "Tap \"Upload Video\" to attach a recorded lesson for fast review."
                                                } else {
                                                    "Your teacher hasn't uploaded study videos to this deck yet."
                                                },
                                            )
                                        }
                                    } else {
                                        itemsIndexed(videos, key = { _, v -> v.id }) { idx, video ->
                                            VideoLessonCard(
                                                index = idx + 1,
                                                video = video,
                                                isTeacher = isTeacher,
                                                onPlay = { playingVideo = video },
                                                onDelete = { viewModel.deleteVideo(context, video) },
                                            )
                                        }
                                    }
                                }
                                3 -> {
                                    val classList = (classProgressState as? UiState.Success)?.data.orEmpty()
                                    if (classList.isEmpty()) {
                                        item {
                                            EmptySectionCard(
                                                title = "No Student Activity Yet",
                                                body = "Share join code ${currentDeck.joinCode} with students. Their flashcard mastery and quiz scores will appear here live.",
                                            )
                                        }
                                    } else {
                                        item {
                                            ClassSummaryBanner(progressList = classList)
                                        }
                                        items(classList, key = { it.studentId }) { studentProg ->
                                            StudentProgressRowCard(progress = studentProg)
                                        }
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showEditDeckDialog && currentDeck != null) {
        CreateOrEditDeckDialog(
            initialDeck = currentDeck,
            onDismiss = { showEditDeckDialog = false },
            onSave = { title, subject, desc, accent, published ->
                viewModel.updateDeck(
                    title = title,
                    subject = subject,
                    description = desc,
                    accent = accent,
                    published = published,
                    previousPublished = currentDeck.published,
                )
                showEditDeckDialog = false
            },
        )
    }

    if (showFlashcardDialog && currentDeck != null) {
        FlashcardEditorDialog(
            initialCard = editingCard,
            onDismiss = { showFlashcardDialog = false },
            onSave = { front, back, hint ->
                val target = editingCard
                if (target == null) {
                    viewModel.addFlashcard(
                        front = front,
                        back = back,
                        hint = hint,
                        published = currentDeck.published,
                        nextOrder = flashcards.size,
                    )
                } else {
                    viewModel.updateFlashcard(
                        cardId = target.id,
                        front = front,
                        back = back,
                        hint = hint,
                        orderIndex = target.orderIndex.toInt(),
                    )
                }
                showFlashcardDialog = false
            },
        )
    }

    if (showQuestionDialog && currentDeck != null) {
        QuestionEditorDialog(
            initialQuestion = editingQuestion,
            onDismiss = { showQuestionDialog = false },
            onSave = { prompt, options, correctIdx, explanation ->
                val target = editingQuestion
                if (target == null) {
                    viewModel.addQuestion(
                        prompt = prompt,
                        options = options,
                        correctIndex = correctIdx,
                        explanation = explanation,
                        published = currentDeck.published,
                        nextOrder = questions.size,
                    )
                } else {
                    viewModel.updateQuestion(
                        questionId = target.id,
                        prompt = prompt,
                        options = options,
                        correctIndex = correctIdx,
                        explanation = explanation,
                        orderIndex = target.orderIndex.toInt(),
                    )
                }
                showQuestionDialog = false
            },
        )
    }

    playingVideo?.let { video ->
        StudyVideoPlayerDialog(
            video = video,
            onDismiss = { playingVideo = null },
        )
    }
}

@Composable
private fun VideoLessonCard(
    index: Int,
    video: StudyVideo,
    isTeacher: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("play_video_${video.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = "Play ${video.title}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "LESSON #$index • ${StudyVideoStorage.formatDuration(video.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (video.notes.isNotBlank()) {
                    Text(
                        text = video.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isTeacher) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete video",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckHeaderCard(
    deck: StudyDeck,
    isTeacher: Boolean,
    flashcardCount: Int,
    questionCount: Int,
    myProgress: DeckProgress?,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onStartFlashcards: () -> Unit,
    onStartQuiz: () -> Unit,
    onStartLiveClass: () -> Unit,
) {
    val palette = deckAccentPalette(deck.accentColorKey, isSystemInDarkTheme())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubjectBadge(subject = deck.subject, accentKey = deck.accentColorKey)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    JoinCodePill(joinCode = deck.joinCode, onCopyClick = onCopyCode)
                    IconButton(onClick = onShareCode, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share join code",
                            modifier = Modifier.size(18.dp),
                            tint = palette.accent,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = deck.title,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Taught by ${deck.teacherName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (deck.description.isNotBlank()) {
                    Text(
                        text = deck.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (myProgress != null) {
                val mastered = myProgress.masteredCardIds.size
                val total = flashcardCount.coerceAtLeast(1)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Your Mastery: $mastered/$flashcardCount cards",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = "Best Quiz: ${myProgress.bestQuizPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onStartFlashcards,
                    enabled = flashcardCount > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("start_flashcards_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Flashcards ($flashcardCount)")
                }
                Button(
                    onClick = onStartQuiz,
                    enabled = questionCount > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("start_quiz_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Quiz ($questionCount)")
                }
            }

            if (isTeacher) {
                FilledTonalButton(
                    onClick = onStartLiveClass,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("start_live_class_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Live Video Class")
                }
            }
        }
    }
}

@Composable
private fun LiveClassActiveBanner(liveSession: LiveSession) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_class_active_banner"),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Videocam,
                contentDescription = "Live class active",
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "LIVE NOW: ${liveSession.teacherName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
                if (liveSession.activePrompt.isNotBlank()) {
                    Text(
                        text = "Presenting: ${liveSession.activePrompt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlashcardItemCard(
    index: Int,
    card: Flashcard,
    isTeacher: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CARD #$index",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (isTeacher) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit card", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete card",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Text(
                text = card.front,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = card.back,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (card.hint.isNotBlank()) {
                Text(
                    text = "Hint: ${card.hint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun QuestionItemCard(
    index: Int,
    question: QuizQuestion,
    isTeacher: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "QUESTION #$index",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (isTeacher) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit question", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete question",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleMedium,
            )
            if (isTeacher) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    question.options.forEachIndexed { idx, opt ->
                        val isCorrect = idx.toLong() == question.correctIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (isCorrect) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Correct option",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            Text(
                                text = opt,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isCorrect) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCorrect) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "${question.options.size} multiple-choice options",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ClassSummaryBanner(progressList: List<DeckProgress>) {
    val avgBest = if (progressList.isEmpty()) 0 else {
        (progressList.sumOf { it.bestQuizPercent } / progressList.size.toLong()).toInt()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column {
                    Text(
                        text = "${progressList.size} Active Student${if (progressList.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Live class quiz & flashcard tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Text(
                text = "Avg: $avgBest%",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StudentProgressRowCard(progress: DeckProgress) {
    val totalCards = progress.totalCardsCount.coerceAtLeast(1L)
    val masteryFraction = (progress.masteredCardIds.size.toFloat() / totalCards.toFloat()).coerceIn(0f, 1f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = progress.studentName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Best ${progress.bestQuizPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { masteryFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "${progress.masteredCardIds.size}/${progress.totalCardsCount} cards mastered • ${progress.quizzesTaken} quizzes • Last: ${progress.lastQuizPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptySectionCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
