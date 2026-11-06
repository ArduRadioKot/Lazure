package com.rassvet.essential.ui

import android.net.Uri
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.R
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.chat.ChatEngine
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.network.NetworkMonitor
import com.rassvet.essential.data.llm.HybridLocalLlmEngine
import com.rassvet.essential.data.llm.ChatModelSelection
import com.rassvet.essential.data.recording.RecordingRepository
import com.rassvet.essential.ui.auth.AuthScreen
import com.rassvet.essential.ui.auth.PostRegisterWelcomeScreen
import com.rassvet.essential.ui.chat.ChatScreen
import com.rassvet.essential.ui.editor.NoteEditorScreen
import com.rassvet.essential.ui.graph.GraphScreen
import com.rassvet.essential.ui.home.HomeChatState
import com.rassvet.essential.ui.home.MainHomeScreen
import com.rassvet.essential.ui.home.MeetingHomeScreen
import com.rassvet.essential.ui.onboarding.CreateVaultScreen
import com.rassvet.essential.ui.onboarding.OnboardingScreen
import com.rassvet.essential.ui.settings.ChangeVaultScreen
import com.rassvet.essential.ui.settings.SettingsScreen
import com.rassvet.essential.ui.recording.RecordingLibraryScreen
import com.rassvet.essential.ui.recording.LiveRecordingScreen
import com.rassvet.essential.ui.models.AIModelsScreen
import com.rassvet.essential.ui.splash.SplashScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class SplashTarget {
    Onboarding,
    Main,
}

private fun androidx.navigation.NavHostController.openNoteEditor(noteUri: Uri) {
    val encoded = URLEncoder.encode(noteUri.toString(), StandardCharsets.UTF_8.toString())
    navigate("editor/$encoded") {
        launchSingleTop = true
    }
}


data class PendingNoteContext(
    val title: String,
    val body: String,
    val uri: Uri? = null,
)

@Composable
fun EssentialApp(
    db: AppDatabase,
    prefs: VaultPreferencesRepository,
    indexRepository: IndexRepository,
    chatEngine: ChatEngine,
    localEngine: HybridLocalLlmEngine,
    networkMonitor: NetworkMonitor,
) {
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppRoutes.Splash,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(300, easing = EaseOutCubic)) { it / 4 }
        },
        exitTransition = {
            fadeOut(tween(180)) + slideOutHorizontally(tween(300, easing = EaseOutCubic)) { -it / 4 }
        },
        popEnterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(300, easing = EaseOutCubic)) { -it / 4 }
        },
        popExitTransition = {
            fadeOut(tween(180)) + slideOutHorizontally(tween(300, easing = EaseOutCubic)) { it / 4 }
        },
    ) {
        composable(AppRoutes.Splash) {
            var animationDone by remember { mutableStateOf(false) }
            var splashTarget by remember { mutableStateOf<SplashTarget?>(null) }

            LaunchedEffect(Unit) {
                prefs.prepareOfflineMode()
                val vault = prefs.vaultTreeUri.first()
                splashTarget =
                    if (vault.isNullOrBlank()) SplashTarget.Onboarding else SplashTarget.Main
            }

            LaunchedEffect(animationDone, splashTarget) {
                if (!animationDone || splashTarget == null) return@LaunchedEffect
                when (splashTarget) {
                    SplashTarget.Onboarding -> navController.navigate(AppRoutes.Onboarding) {
                        popUpTo(AppRoutes.Splash) { inclusive = true }
                    }
                    SplashTarget.Main -> {
                        scope.launch {
                            navController.navigate(AppRoutes.Main) {
                                popUpTo(AppRoutes.Splash) { inclusive = true }
                            }
                        }
                    }
                    null -> Unit
                    }
            }

            SplashScreen(onAnimationComplete = { animationDone = true })
        }
        composable(AppRoutes.Auth) {
            AuthScreen(prefs = prefs) { isRegister ->
                if (isRegister) {
                    navController.navigate(AppRoutes.Welcome)
                } else {
                    scope.launch {
                        prefs.clearVaultSelection()
                        ChatModelSelection.applyDefaultEssentialAi(prefs)
                        navController.navigate(AppRoutes.Onboarding) {
                            popUpTo(AppRoutes.Auth) { inclusive = true }
                        }
                    }
                }
            }
        }
        composable(AppRoutes.Welcome) {
            PostRegisterWelcomeScreen(prefs = prefs) {
                navController.navigate(AppRoutes.Onboarding) {
                    popUpTo(AppRoutes.Welcome) { inclusive = true }
                }
            }
        }
        composable(AppRoutes.Onboarding) {
            OnboardingScreen(
                prefs = prefs,
                navController = navController,
                onVaultPickedToMain = {
                    navController.navigate(AppRoutes.Main) {
                        popUpTo(AppRoutes.Onboarding) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoutes.CreateVault) {
            CreateVaultScreen(
                prefs = prefs,
                onVaultReady = {
                    navController.navigate(AppRoutes.Main) {
                        popUpTo(AppRoutes.CreateVault) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.Main) {
            EssentialMainNav(
                db = db,
                prefs = prefs,
                indexRepository = indexRepository,
                chatEngine = chatEngine,
                localEngine = localEngine,
                networkMonitor = networkMonitor,
            onLogout = {},
            )
        }
    }
}

@Composable
private fun EssentialMainNav(
    db: AppDatabase,
    prefs: VaultPreferencesRepository,
    indexRepository: IndexRepository,
    chatEngine: ChatEngine,
    localEngine: HybridLocalLlmEngine,
    networkMonitor: NetworkMonitor,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    var vaultRefreshPulse by remember { mutableIntStateOf(0) }
    var pendingNoteContext by remember { mutableStateOf<PendingNoteContext?>(null) }
    var pendingNoteContextPulse by remember { mutableIntStateOf(0) }
    val homeChatState = remember { HomeChatState() }
    val context = LocalContext.current
    val recordingRepository = remember(context, db) { RecordingRepository(context.applicationContext, db) }
    val recordings by recordingRepository.recordings.collectAsState(initial = emptyList())
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topDestinations = listOf("home", "vault", "ai", "settings")
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in topDestinations) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp).clip(RoundedCornerShape(28.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    NavigationBarItem(selected = currentRoute == "home", onClick = { navController.navigateTopLevel("home") },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) }, label = { Text(stringResource(R.string.nav_home)) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer))
                    NavigationBarItem(selected = currentRoute == "vault", onClick = { navController.navigateTopLevel("vault") },
                        icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) }, label = { Text(stringResource(R.string.nav_vault)) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer))
                    NavigationBarItem(selected = currentRoute == "ai", onClick = { navController.navigateTopLevel("ai") },
                        icon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) }, label = { Text(stringResource(R.string.nav_ai)) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer))
                    NavigationBarItem(selected = currentRoute == "settings", onClick = { navController.navigateTopLevel("settings") },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) }, label = { Text(stringResource(R.string.nav_settings)) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer))
                }
            }
        },
    ) { scaffoldPadding ->
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        enterTransition = {
            fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 5 }
        },
        exitTransition = {
            fadeOut(tween(200, easing = EaseInOutCubic)) +
                slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 5 }
        },
        popEnterTransition = {
            fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 5 }
        },
        popExitTransition = {
            fadeOut(tween(200, easing = EaseInOutCubic)) +
                slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 5 }
        },
    ) {
        composable("home") {
            MeetingHomeScreen(
                recent = recordings,
                onLive = { navController.navigate("live") },
                onImport = { navController.navigate("import") },
                onModels = { navController.navigate("models") },
                onVault = { navController.navigateTopLevel("vault") },
                onSearch = { navController.navigateTopLevel("vault") },
                onOpenRecording = { id -> navController.navigate("recording/$id") },
                onOpenNotes = { navController.navigate("notes") },
            )
        }
        composable("notes") {
            MainHomeScreen(
                prefs = prefs,
                db = db,
                indexRepository = indexRepository,
                chatEngine = chatEngine,
                localEngine = localEngine,
                networkMonitor = networkMonitor,
                chatState = homeChatState,
                vaultRefreshPulse = vaultRefreshPulse,
                pendingNoteContext = pendingNoteContext,
                pendingNoteContextPulse = pendingNoteContextPulse,
                onPendingNoteContextConsumed = { pendingNoteContext = null },
                onOpenNote = { uri -> navController.openNoteEditor(uri) },
                onSettings = { navController.navigateTopLevel("settings") },
                onRecordings = { navController.navigate("recordings") },
            )
        }
        composable("vault") {
            RecordingLibraryScreen(repository = recordingRepository, onBack = { navController.navigateTopLevel("home") }, showBack = false)
        }
        composable("import") {
            RecordingLibraryScreen(repository = recordingRepository, onBack = { navController.popBackStack() }, startImportOnOpen = true)
        }
        composable(
            route = "recording/{recordingId}",
            arguments = listOf(navArgument("recordingId") { type = NavType.StringType }),
        ) { entry ->
            RecordingLibraryScreen(
                repository = recordingRepository,
                onBack = { navController.popBackStack() },
                initialRecordingId = entry.arguments?.getString("recordingId"),
            )
        }
        composable("ai") {
            ChatScreen(db = db, prefs = prefs, chatEngine = chatEngine, onBack = { navController.navigateTopLevel("home") }, onModels = { navController.navigate("models") })
        }
        composable("live") {
            LiveRecordingScreen(db = db, onBack = { navController.popBackStack() }, onSaved = { navController.navigateTopLevel("vault") })
        }
        composable("models") {
            AIModelsScreen(prefs = prefs, onBack = { navController.popBackStack() })
        }
        composable("recordings") {
            RecordingLibraryScreen(repository = recordingRepository, onBack = { navController.popBackStack() })
        }
        composable("graph") {
            GraphScreen(
                db = db,
                prefs = prefs,
                onOpenNote = { uri -> navController.openNoteEditor(uri) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "editor/{noteUri}",
            arguments =
                listOf(
                    navArgument("noteUri") { type = NavType.StringType },
                ),
            enterTransition = {
                fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
            },
            exitTransition = {
                fadeOut(tween(220, easing = EaseInOutCubic)) +
                    slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 }
            },
            popEnterTransition = {
                fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth / 4 }
            },
            popExitTransition = {
                fadeOut(tween(220, easing = EaseInOutCubic)) +
                    slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
            },
        ) { entry ->
            val encoded = entry.arguments?.getString("noteUri") ?: return@composable
            val uri = Uri.parse(java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString()))
            key(uri) {
                NoteEditorScreen(
                    prefs = prefs,
                    db = db,
                    indexRepository = indexRepository,
                    noteUri = uri,
                    onAskAi = { title, body ->
                        pendingNoteContext = PendingNoteContext(title, body, uri)
                        pendingNoteContextPulse++
                        navController.popBackStack()
                    },
                    onOpenNote = { linkedUri -> navController.openNoteEditor(linkedUri) },
                    onBack = { navController.popBackStack() },
                    onSaved = { vaultRefreshPulse++ },
                    onDeleted = { vaultRefreshPulse++ },
                )
            }
        }
        composable("chat") {
            ChatScreen(
                db = db,
                prefs = prefs,
                chatEngine = chatEngine,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                prefs = prefs,
                onBack = { navController.popBackStack() },
                onLogout = onLogout,
                onChangeVault = { navController.navigate("change_vault") },
            )
        }
        composable("change_vault") {
            ChangeVaultScreen(
                prefs = prefs,
                onBack = { navController.popBackStack() },
                onCreateNewVault = { navController.navigate("create_vault_from_settings") },
                onVaultChanged = { vaultRefreshPulse++ },
            )
        }
        composable("create_vault_from_settings") {
            CreateVaultScreen(
                prefs = prefs,
                onVaultReady = {
                    vaultRefreshPulse++
                    navController.popBackStack("home", false)
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
    }
}

private fun androidx.navigation.NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}


