package com.rassvet.essential.ui.recording

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rassvet.essential.R
import com.rassvet.essential.data.chat.ChatEngine
import com.rassvet.essential.data.local.TranscriptSegmentEntity
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.recording.RecordingAi
import com.rassvet.essential.data.recording.RecordingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatTurn(val user: Boolean, text: String, val sources: List<TranscriptSegmentEntity> = emptyList()) {
    var text by mutableStateOf(text)
}

/** State holder for the per-recording Summarize and Ask-AI features. */
class RecordingAiState(
    private val repo: RecordingRepository, private val chat: ChatEngine,
    private val prefs: VaultPreferencesRepository, private val recordingId: String,
) {
    var summarizing by mutableStateOf(false); private set
    var progress by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null)
    val turns = mutableStateListOf<ChatTurn>()
    var thinking by mutableStateOf(false); private set

    private suspend fun model(): String? = prefs.activeGgufName.first()?.takeIf { it.isNotBlank() }

    suspend fun summarize() {
        error = null
        val m = model() ?: run { error = NO_MODEL; return }
        summarizing = true
        try {
            val text = RecordingAi.summarize(chat, m, repo.segmentsOnce(recordingId), repo.speakersOnce(recordingId)) { progress = it }
            if (text.isBlank()) error = "Empty answer from model" else repo.saveSummary(recordingId, text, m)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Throwable) { error = e.message ?: "Summary failed" } finally { summarizing = false }
    }

    private var loaded = false

    /** Restores the persisted conversation (once). Sources are re-resolved against the current transcript. */
    suspend fun load() {
        if (loaded) return
        loaded = true
        val segs = repo.segmentsOnce(recordingId)
        repo.chatMessages(recordingId).forEach { m ->
            val starts = m.sourceStarts.split(',').mapNotNull { it.toLongOrNull() }.toSet()
            turns += ChatTurn(m.role == "user", m.text, segs.filter { it.startMs in starts })
        }
    }

    suspend fun clear() { repo.clearChat(recordingId); turns.clear() }

    suspend fun ask(q: String) {
        val m = model()
        turns += ChatTurn(true, q)
        repo.addChatMessage(recordingId, "user", q, emptyList())
        if (m == null) { turns += ChatTurn(false, NO_MODEL); return }
        thinking = true
        val reply = ChatTurn(false, "")
        val history = turns.dropLast(1).map { ChatEngine.HistoryLine(it.user, it.text) }
        try {
            val segs = repo.segmentsOnce(recordingId)
            val (text, hits) = RecordingAi.answer(chat, m, q, segs, repo.speakersOnce(recordingId), history) { }
            turns += ChatTurn(false, text.ifBlank { "—" }, hits)
            repo.addChatMessage(recordingId, "assistant", text.ifBlank { "—" }, hits.map { it.startMs })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Throwable) { turns += ChatTurn(false, e.message ?: "Error") } finally { thinking = false }
    }

    private companion object { const val NO_MODEL = "Select a local language model in the AI tab first." }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingChatSheet(state: RecordingAiState, onDismiss: () -> Unit, onSeek: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    LaunchedEffect(state) { state.load() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 640.dp).padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
            if (state.turns.isNotEmpty()) TextButton(onClick = { scope.launch { state.clear() } }, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                Text(stringResource(R.string.rec_clear_chat))
            }
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.turns) { t ->
                    Card(colors = CardDefaults.cardColors(containerColor =
                        if (t.user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.text)
                            if (t.sources.isNotEmpty()) {
                                Text(stringResource(R.string.rec_sources), style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 8.dp))
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    t.sources.forEach { s ->
                                        AssistChip(onClick = { onSeek(s.startMs) }, label = {
                                            Text(RecordingAi.timecode(s.startMs) + " " + s.text.take(28))
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.thinking) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), maxLines = 3,
                    placeholder = { Text(stringResource(R.string.rec_ask_hint)) })
                Button(enabled = input.isNotBlank() && !state.thinking, onClick = {
                    val q = input.trim(); input = ""; scope.launch { state.ask(q) }
                }) { Text(stringResource(R.string.rec_send)) }
            }
        }
    }
}
