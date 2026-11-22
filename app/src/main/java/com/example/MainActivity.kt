package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.auth.attemptAutoSignIn
import com.example.data.auth.signOut
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.GUEST_USER_ID
import com.example.data.repository.StudyRepository
import com.example.ui.components.CreateOrEditDeckDialog
import com.example.ui.screens.AddBatchScreen
import com.example.ui.screens.AddFlashcardScreen
import com.example.ui.screens.AiCoachScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BatchDetailScreen
import com.example.ui.screens.BatchesScreen
import com.example.ui.screens.DeckDetailScreen
import com.example.ui.screens.FlashcardSessionScreen
import com.example.ui.screens.LiveClassScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.QuizSessionScreen
import com.example.ui.screens.StudyScreen
import com.example.ui.screens.TeachScreen
import com.example.ui.screens.UploadVideoScreen
import com.example.ui.theme.StudyDeckTheme
import com.example.ui.viewmodel.AiCoachViewModel
import com.example.ui.viewmodel.BatchDetailViewModel
import com.example.ui.viewmodel.DeckDetailViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            StudyDeckTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
    var isGuestMode by rememberSaveable { mutableStateOf(false) }
    var showAuthFromGuest by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                isGuestMode = false
                showAuthFromGuest = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    isGuestMode = false
                    showAuthFromGuest = false
                },
                onUnauthenticated = {},
                scope = scope,
            )
        }
    }

    val user = currentUser
    when {
        user != null -> {
            key(user.uid) {
                StudyDeckApp(
                    currentUser = user,
                    isGuestMode = false,
                    onRequestSignIn = {},
                    onSignOut = {
                        signOut(credentialManager, scope) {
                            currentUser = null
                            isGuestMode = false
                            showAuthFromGuest = false
                        }
                    },
                )
            }
        }
        isGuestMode && !showAuthFromGuest -> {
            key(GUEST_USER_ID) {
                StudyDeckApp(
                    currentUser = null,
                    isGuestMode = true,
                    onRequestSignIn = { showAuthFromGuest = true },
                    onSignOut = {
                        isGuestMode = false
                        showAuthFromGuest = false
                    },
                )
            }
        }
        else -> {
            AuthScreen(
                showBackToGuest = isGuestMode && showAuthFromGuest,
                onBackToGuest = { showAuthFromGuest = false },
                onContinueAsGuest = {
                    isGuestMode = true
                    showAuthFromGuest = false
                },
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    isGuestMode = false
                    showAuthFromGuest = false
                },
            )
        }
    }
}

@Composable
private fun StudyDeckApp(
    currentUser: FirebaseUser?,
    isGuestMode: Boolean,
    onRequestSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val uid = currentUser?.uid ?: GUEST_USER_ID
    val vmScopeKey = if (isGuestMode) GUEST_USER_ID else uid

    val resolvedDisplayName = if (isGuestMode) {
        "Guest Scholar"
    } else {
        currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: currentUser?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: "Scholar"
    }

    fun buildRepository(app: android.app.Application): StudyRepository {
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        return StudyRepository(db = db, isGuest = isGuestMode)
    }

    val homeViewModel: HomeViewModel = viewModel(
        key = vmScopeKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                HomeViewModel(buildRepository(app), uid, resolvedDisplayName)
            }
        },
    )
    val coachViewModel: AiCoachViewModel = viewModel(
        key = "${vmScopeKey}_coach",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                AiCoachViewModel(buildRepository(app), uid)
            }
        },
    )

    NavHost(
        navController = navController,
        startDestination = "main",
    ) {
        composable("main") {
            MainTabsScaffold(
                homeViewModel = homeViewModel,
                coachViewModel = coachViewModel,
                currentUserId = uid,
                userDisplayName = resolvedDisplayName,
                isGuestMode = isGuestMode,
                onRequestSignIn = onRequestSignIn,
                onOpenDeck = { deckId -> navController.navigate("deck/$deckId") },
                onStartFlashcards = { deckId -> navController.navigate("flashcards/$deckId") },
                onStartQuiz = { deckId -> navController.navigate("quiz/$deckId") },
                onStartLiveClass = { deckId -> navController.navigate("liveclass/$deckId") },
                onAddFlashcard = { navController.navigate("add_flashcard") },
                onUploadVideo = { navController.navigate("upload_video") },
                onAddBatch = { navController.navigate("add_batch") },
                onEditBatch = { batchId -> navController.navigate("add_batch?batchId=$batchId") },
                onOpenBatch = { batchId -> navController.navigate("batch/$batchId") },
                onSignOut = onSignOut,
            )
        }
        composable(
            route = "add_flashcard?deckId={deckId}",
            arguments = listOf(
                navArgument("deckId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val initialDeckId = backStackEntry.arguments?.getString("deckId")
            AddFlashcardScreen(
                homeViewModel = homeViewModel,
                initialDeckId = initialDeckId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "upload_video?deckId={deckId}",
            arguments = listOf(
                navArgument("deckId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val initialDeckId = backStackEntry.arguments?.getString("deckId")
            UploadVideoScreen(
                homeViewModel = homeViewModel,
                initialDeckId = initialDeckId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "deck/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId") ?: return@composable
            val detailVm: DeckDetailViewModel = viewModel(
                key = "${vmScopeKey}_$deckId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        DeckDetailViewModel(buildRepository(app), uid, deckId)
                    }
                },
            )
            DeckDetailScreen(
                viewModel = detailVm,
                currentUserId = uid,
                onBack = { navController.popBackStack() },
                onStartFlashcards = { navController.navigate("flashcards/$deckId") },
                onStartQuiz = { navController.navigate("quiz/$deckId") },
                onStartLiveClass = { navController.navigate("liveclass/$deckId") },
                onAddFlashcard = { navController.navigate("add_flashcard?deckId=$deckId") },
                onUploadVideo = { navController.navigate("upload_video?deckId=$deckId") },
            )
        }
        composable(
            route = "flashcards/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId") ?: return@composable
            val detailVm: DeckDetailViewModel = viewModel(
                key = "${vmScopeKey}_fc_$deckId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        DeckDetailViewModel(buildRepository(app), uid, deckId)
                    }
                },
            )
            FlashcardSessionScreen(
                viewModel = detailVm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "quiz/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId") ?: return@composable
            val detailVm: DeckDetailViewModel = viewModel(
                key = "${vmScopeKey}_qz_$deckId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        DeckDetailViewModel(buildRepository(app), uid, deckId)
                    }
                },
            )
            QuizSessionScreen(
                viewModel = detailVm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "liveclass/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId") ?: return@composable
            val detailVm: DeckDetailViewModel = viewModel(
                key = "${vmScopeKey}_live_$deckId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        DeckDetailViewModel(buildRepository(app), uid, deckId)
                    }
                },
            )
            LiveClassScreen(
                viewModel = detailVm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "add_batch?batchId={batchId}",
            arguments = listOf(
                navArgument("batchId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val editingBatchId = backStackEntry.arguments?.getString("batchId")
            AddBatchScreen(
                homeViewModel = homeViewModel,
                editingBatchId = editingBatchId,
                onBack = { navController.popBackStack() },
                onBatchSaved = { savedId ->
                    navController.popBackStack()
                    navController.navigate("batch/$savedId")
                },
            )
        }
        composable(
            route = "batch/{batchId}",
            arguments = listOf(navArgument("batchId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val batchId = backStackEntry.arguments?.getString("batchId") ?: return@composable
            val batchDetailVm: BatchDetailViewModel = viewModel(
                key = "${vmScopeKey}_batch_$batchId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        BatchDetailViewModel(buildRepository(app), uid, batchId)
                    }
                },
            )
            BatchDetailScreen(
                viewModel = batchDetailVm,
                currentUserId = uid,
                onBack = { navController.popBackStack() },
                onEditBatch = { targetId -> navController.navigate("add_batch?batchId=$targetId") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabsScaffold(
    homeViewModel: HomeViewModel,
    coachViewModel: AiCoachViewModel,
    currentUserId: String,
    userDisplayName: String,
    isGuestMode: Boolean,
    onRequestSignIn: () -> Unit,
    onOpenDeck: (String) -> Unit,
    onStartFlashcards: (String) -> Unit,
    onStartQuiz: (String) -> Unit,
    onStartLiveClass: (String) -> Unit,
    onAddFlashcard: () -> Unit,
    onUploadVideo: () -> Unit,
    onAddBatch: () -> Unit,
    onEditBatch: (String) -> Unit,
    onOpenBatch: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val profileState by homeViewModel.userProfile.collectAsStateWithLifecycle()
    val teacherDecksState by homeViewModel.teacherDecks.collectAsStateWithLifecycle()
    val publishedDecksState by homeViewModel.publishedDecks.collectAsStateWithLifecycle()
    val enrollmentsState by homeViewModel.enrollments.collectAsStateWithLifecycle()
    val logsState by homeViewModel.studyLogs.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCreateDeckDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        homeViewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navItems = listOf(
        Triple(stringResource(R.string.tab_teach), Icons.Rounded.School, "tab_teach"),
        Triple(stringResource(R.string.tab_batches), Icons.Rounded.Groups, "tab_batches"),
        Triple(stringResource(R.string.tab_study), Icons.Rounded.Style, "tab_study"),
        Triple(stringResource(R.string.tab_ai_coach), Icons.Rounded.AutoAwesome, "tab_ai_coach"),
        Triple(stringResource(R.string.tab_progress), Icons.Rounded.Insights, "tab_progress"),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    actions = {
                        val badgeLabel = when {
                            isGuestMode -> stringResource(R.string.guest_mode_label)
                            userDisplayName.startsWith("+") && userDisplayName.length > 6 ->
                                "••${userDisplayName.takeLast(4)}"
                            else ->
                                userDisplayName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "Scholar"
                        }
                        Surface(
                            color = if (isGuestMode) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isGuestMode) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        if (isGuestMode) {
                            androidx.compose.material3.TextButton(
                                onClick = onRequestSignIn,
                                modifier = Modifier.testTag("guest_sign_in_topbar_button"),
                            ) {
                                Text(stringResource(R.string.sign_in_now))
                            }
                        } else {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("account_menu_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = "Account options",
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sign Out ($userDisplayName)") },
                                    onClick = {
                                        showMenu = false
                                        onSignOut()
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        navItems.forEachIndexed { idx, (label, icon, tag) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail {
                        navItems.forEachIndexed { idx, (label, icon, tag) ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tag),
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> TeachScreen(
                            decksState = teacherDecksState,
                            onCreateDeckClick = { showCreateDeckDialog = true },
                            onAddFlashcardClick = onAddFlashcard,
                            onUploadVideoClick = onUploadVideo,
                            onAddBatchClick = onAddBatch,
                            onOpenDeck = onOpenDeck,
                            onStartLiveClass = onStartLiveClass,
                            onDeleteDeck = { homeViewModel.deleteDeck(it) },
                            onCopiedCode = { code ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied join code $code")
                                }
                            },
                        )
                        1 -> BatchesScreen(
                            homeViewModel = homeViewModel,
                            currentUserId = currentUserId,
                            onAddBatchClick = onAddBatch,
                            onEditBatchClick = onEditBatch,
                            onOpenBatch = onOpenBatch,
                            onCopiedCode = { code ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied batch code $code")
                                }
                            },
                        )
                        2 -> StudyScreen(
                            enrollmentsState = enrollmentsState,
                            publishedDecksState = publishedDecksState,
                            onJoinByCode = { code ->
                                homeViewModel.joinDeckByCode(code) { deck ->
                                    onOpenDeck(deck.id)
                                }
                            },
                            onOpenDeck = onOpenDeck,
                            onStartFlashcards = onStartFlashcards,
                            onStartQuiz = onStartQuiz,
                            onEnrollAndOpen = { deck ->
                                homeViewModel.enrollInDeck(deck) {
                                    onOpenDeck(deck.id)
                                }
                            },
                        )
                        3 -> AiCoachScreen(
                            coachViewModel = coachViewModel,
                            profileState = profileState,
                            teacherDecksState = teacherDecksState,
                            enrollmentsState = enrollmentsState,
                            publishedDecksState = publishedDecksState,
                            currentUserId = currentUserId,
                        )
                        4 -> ProgressScreen(
                            profileState = profileState,
                            enrollmentsState = enrollmentsState,
                            logsState = logsState,
                        )
                    }
                }
            }
        }
    }

    if (showCreateDeckDialog) {
        CreateOrEditDeckDialog(
            onDismiss = { showCreateDeckDialog = false },
            onSave = { title, subject, desc, accent, published ->
                homeViewModel.createDeck(title, subject, desc, accent, published) { deckId ->
                    showCreateDeckDialog = false
                    onOpenDeck(deckId)
                }
            },
        )
    }
}
