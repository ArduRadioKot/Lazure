package com.rassvet.essential

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.rassvet.essential.data.chat.ChatEngine
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.network.NetworkMonitor
import com.rassvet.essential.data.llm.HybridLocalLlmEngine
import com.rassvet.essential.ui.EssentialApp
import com.rassvet.essential.ui.theme.EssentialSystemBars
import com.rassvet.essential.ui.theme.EssentialTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var prefs: VaultPreferencesRepository

    @Inject
    lateinit var indexRepository: IndexRepository

    @Inject
    lateinit var chatEngine: ChatEngine

    @Inject
    lateinit var localEngine: HybridLocalLlmEngine

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "system")
            val dynamicColor by prefs.dynamicColor.collectAsState(initial = true)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            EssentialTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                EssentialSystemBars(darkTheme = darkTheme)
                Surface(modifier = Modifier.fillMaxSize()) {
                    EssentialApp(
                        db = appDatabase,
                        prefs = prefs,
                        indexRepository = indexRepository,
                        chatEngine = chatEngine,
                        localEngine = localEngine,
                        networkMonitor = networkMonitor,
                    )
                }
            }
        }
    }
}
