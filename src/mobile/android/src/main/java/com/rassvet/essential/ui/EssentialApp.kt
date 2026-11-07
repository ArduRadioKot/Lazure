package com.rassvet.essential.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import com.rassvet.essential.ui.theme.LazureIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.rassvet.essential.data.recording.RecordingRepository
import com.rassvet.essential.ui.chat.ChatScreen
import com.rassvet.essential.ui.home.MeetingHomeScreen
import com.rassvet.essential.ui.settings.SettingsScreen
import com.rassvet.essential.ui.recording.RecordingLibraryScreen
import com.rassvet.essential.ui.recording.LiveRecordingScreen
import com.rassvet.essential.ui.models.AIModelsScreen
import com.rassvet.essential.ui.splash.SplashScreen

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
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppRoutes.Splash,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(120)) },
        exitTransition = { fadeOut(tween(90)) },
        popEnterTransition = { fadeIn(tween(120)) },
        popExitTransition = { fadeOut(tween(90)) },
    ) {
        composable(AppRoutes.Splash) {
            var animationDone by remember { mutableStateOf(false) }
            var prepared by remember { mutableStateOf(false) }
            val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
            LaunchedEffect(Unit) {
                prefs.prepareOfflineMode()
                runCatching { com.rassvet.essential.data.llm.BundledModelInstaller.install(context, prefs) }
                prepared = true
            }
            LaunchedEffect(animationDone, prepared) {
                if (animationDone && prepared) navController.navigate(AppRoutes.Main) {
                    popUpTo(AppRoutes.Splash) { inclusive = true }
                }
            }

            SplashScreen(onAnimationComplete = { animationDone = true })
        }
        composable(AppRoutes.Main) {
            EssentialMainNav(
                db = db,
                prefs = prefs,
                indexRepository = indexRepository,
                chatEngine = chatEngine,
                localEngine = localEngine,
                networkMonitor = networkMonitor,
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
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val recordingRepository = remember(context, db) { RecordingRepository(context.applicationContext, db) }
    LaunchedEffect(recordingRepository) { runCatching { recordingRepository.recoverInterrupted() } }
    val recordings by recordingRepository.recordings.collectAsState(initial = emptyList())
    var chatRecordingId by remember { mutableStateOf<String?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topDestinations = listOf("home", "vault", "ai", "settings")
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in topDestinations && !keyboardVisible) {
                com.rassvet.essential.ui.components.LazureNavigationBar(currentRoute) { route ->
                    navController.navigateTopLevel(route)
                }
            }
        },
    ) { scaffoldPadding ->
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding).consumeWindowInsets(scaffoldPadding),
        enterTransition = { fadeIn(tween(120)) },
        exitTransition = { fadeOut(tween(90)) },
        popEnterTransition = { fadeIn(tween(120)) },
        popExitTransition = { fadeOut(tween(90)) },
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
            )
        }
        composable("vault") {
            RecordingLibraryScreen(repository = recordingRepository, onBack = { navController.navigateTopLevel("home") }, showBack = false, chatEngine = chatEngine, prefs = prefs, onAskAi = { id -> chatRecordingId = id; navController.navigateTopLevel("ai") })
        }
        composable("import") {
            RecordingLibraryScreen(repository = recordingRepository, onBack = { navController.popBackStack() }, startImportOnOpen = true, chatEngine = chatEngine, prefs = prefs, onAskAi = { id -> chatRecordingId = id; navController.navigateTopLevel("ai") })
        }
        composable(
            route = "recording/{recordingId}",
            arguments = listOf(navArgument("recordingId") { type = NavType.StringType }),
        ) { entry ->
            RecordingLibraryScreen(
                repository = recordingRepository,
                onBack = { navController.popBackStack() },
                initialRecordingId = entry.arguments?.getString("recordingId"),
                chatEngine = chatEngine, prefs = prefs,
                onAskAi = { id -> chatRecordingId = id; navController.navigateTopLevel("ai") },
            )
        }
        composable("ai") {
            ChatScreen(db = db, prefs = prefs, chatEngine = chatEngine, onBack = { navController.navigateTopLevel("home") }, onModels = { navController.navigate("models") },
                initialRecordingId = chatRecordingId, onInitialRecordingConsumed = { chatRecordingId = null })
        }
        composable("live") {
            LiveRecordingScreen(db = db, onBack = { navController.popBackStack() }, onSaved = { navController.navigateTopLevel("vault") })
        }
        composable("models") {
            AIModelsScreen(prefs = prefs, onBack = { navController.popBackStack() })
        }
        composable("recordings") {
            RecordingLibraryScreen(repository = recordingRepository, onBack = { navController.popBackStack() }, chatEngine = chatEngine, prefs = prefs, onAskAi = { id -> chatRecordingId = id; navController.navigateTopLevel("ai") })
        }
        composable("settings") {
            SettingsScreen(
                prefs = prefs,
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


