package com.rassvet.essential.data.chat

import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.llm.HybridLocalLlmEngine
import com.rassvet.essential.data.network.OnlineChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatEngineRoutingTest {
    @Test
    fun cloudModeOffline_returnsOfflineMessage() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = VaultPreferencesRepository(context)
        val index = IndexRepository(com.rassvet.essential.data.local.AppDatabase.build(context))
        val localEngine = HybridLocalLlmEngine(context, prefs)
        val offlineChecker = OnlineChecker { false }
        val engine = ChatEngine(context, prefs, index, localEngine, offlineChecker)

        val reply =
            engine.streamReply(
                ChatEngine.Request(
                    userVisibleText = "Hello",
                    queryForSearch = "Hello",
                    activeModel = null,
                    vaultStored = null,
                    cloudMode = true,
                ),
            ) { }

        assertTrue(reply.text.isNotBlank())
        assertTrue(
            reply.text.contains("сети", ignoreCase = true) ||
                reply.text.contains("network", ignoreCase = true),
        )
    }
}
