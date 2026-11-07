package com.rassvet.essential.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import com.rassvet.essential.data.llm.ChatModelSelection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.rassvet.essential.ui.theme.LazureIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.rassvet.essential.R
import com.rassvet.essential.data.api.EssentialApiErrors
import com.rassvet.essential.data.chat.ChatEngine
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.ui.editor.MarkdownContent
import com.rassvet.essential.ui.home.ChatModelPickerPill
import com.rassvet.essential.ui.home.ChatHistorySheet
import com.rassvet.essential.ui.home.ChatSessionUi
import com.rassvet.essential.data.local.ChatMessageEntity
import com.rassvet.essential.data.local.ChatSessionEntity
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    db: AppDatabase,
    prefs: VaultPreferencesRepository,
    chatEngine: ChatEngine,
    onBack: () -> Unit,
    onModels: () -> Unit = {},
    initialRecordingId: String? = null,
    onInitialRecordingConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val recRepo = remember(context, db) { com.rassvet.essential.data.recording.RecordingRepository(context.applicationContext, db) }
    var attached by remember { mutableStateOf<com.rassvet.essential.data.local.RecordingEntity?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val allRecordings by recRepo.recordings.collectAsState(initial = emptyList())
    LaunchedEffect(initialRecordingId, allRecordings) {
        if (initialRecordingId != null) {
            allRecordings.firstOrNull { it.id == initialRecordingId }?.let { attached = it; onInitialRecordingConsumed() }
        }
    }
    val scope = rememberCoroutineScope()
    val activeGguf by prefs.activeGgufName.collectAsState(initial = null)
    val streamReplies by prefs.streamReplies.collectAsState(initial = true)
    val hapticsEnabled by prefs.chatHapticsEnabled.collectAsState(initial = true)
    val haptics = rememberChatHaptics(hapticsEnabled)
    val notesSuggestion = stringResource(R.string.chat_notes_suggestion)
    var input by remember { mutableStateOf("") }
    var apiBase by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<Pair<String, String>>() }
    var isThinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // ---- chat history (Room: chat_sessions / chat_messages) ----
    val sessionDao = remember(db) { db.chatSessionDao() }
    val messageDao = remember(db) { db.chatMessageDao() }
    var sessionId by remember { mutableLongStateOf(0L) }
    var historyOpen by remember { mutableStateOf(false) }
    val sessions = remember { mutableStateListOf<ChatSessionUi>() }
    fun reloadSessions() {
        scope.launch(Dispatchers.IO) {
            val list = sessionDao.all().map { ChatSessionUi(id = it.id, title = it.title, lastUpdatedAt = it.lastUpdatedAt) }
            withContext(Dispatchers.Main) { sessions.clear(); sessions.addAll(list) }
        }
    }
    LaunchedEffect(db) { reloadSessions() }
    /** The attached conversation travels with the user's message (sources_json is unused for user messages). */
    fun attachmentMarker(rec: com.rassvet.essential.data.local.RecordingEntity?) = if (rec == null) "[]" else "{\"recordingId\":\"${rec.id}\"}"
    suspend fun persist(isUser: Boolean, text: String, rec: com.rassvet.essential.data.local.RecordingEntity?) = withContext(Dispatchers.IO) {
        var id = sessionId
        if (id == 0L) {
            id = sessionDao.insert(ChatSessionEntity(title = text.take(40).ifBlank { context.getString(R.string.chat_title_new) }, lastUpdatedAt = System.currentTimeMillis()))
            sessionId = id
        }
        messageDao.insert(ChatMessageEntity(sessionId = id, isUser = isUser, text = text, attachmentsJson = "[]",
            sourcesJson = if (isUser) attachmentMarker(rec) else "[]", createdAt = System.currentTimeMillis()))
        sessionDao.touch(id, sessionDao.all().firstOrNull { it.id == id }?.title ?: text.take(40), System.currentTimeMillis())
        reloadSessions()
    }
    fun startNewChat() { messages.clear(); sessionId = 0L; attached = null; input = "" }
    fun loadSession(id: Long) {
        scope.launch {
            val stored = withContext(Dispatchers.IO) { messageDao.forSession(id) }
            messages.clear()
            stored.forEach { messages.add((if (it.isUser) "user" else "assistant") to it.text) }
            sessionId = id
            val recId = stored.lastOrNull { it.isUser }?.sourcesJson?.let { Regex("\"recordingId\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            attached = recId?.let { r -> allRecordings.firstOrNull { it.id == r } }
        }
    }

    LaunchedEffect(prefs) {
        kotlinx.coroutines.coroutineScope {
            launch { prefs.apiBaseUrl.collect { apiBase = it } }
            launch { prefs.authToken.collect { token = it } }
        }
    }
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    val sendMessage: (String) -> Unit = { userText ->
        messages.add("user" to userText)
        isThinking = true
        scope.launch {
            runCatching { persist(true, userText, attached) }
            val assistantIdx = messages.size
            messages.add("assistant" to "")
            val history = messages.take(assistantIdx - 1).map { (role, content) -> ChatEngine.HistoryLine(role == "user", content) }
            val output = StringBuilder()
            var lastPublishedAt = 0L
            val rec = attached
            val reply = runCatching {
                if (rec != null) {
                    // Conversation as context: retrieve the most relevant transcript lines for this question.
                    val segs = recRepo.segmentsOnce(rec.id)
                    val names = com.rassvet.essential.data.recording.RecordingAi.nameMap(recRepo.speakersOnce(rec.id))
                    val hits = com.rassvet.essential.data.recording.RecordingAi.retrieve(userText, segs, if (activeGguf.orEmpty().contains("ekv1280", ignoreCase = true)) 4 else 6)
                    val ctx = hits.joinToString("\n") { com.rassvet.essential.data.recording.RecordingAi.line(it, names) }
                    val system = "You answer questions about the recorded conversation \"${rec.title}\". " +
                        "Answer in the language of the question, in 1-3 short sentences, in your own words. " +
                        "Use only the excerpts the user gives you; if the answer is not there, say so."
                    val grounded = "Excerpts:\n$ctx\n\nQuestion: $userText"
                    val answer = chatEngine.completeLocalIsolated(
                        system = system, user = grounded, history = history, modelId = activeGguf.orEmpty(),
                    ) { chunk ->
                        output.append(chunk)
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastPublishedAt >= 50L) { messages[assistantIdx] = "assistant" to output.toString(); lastPublishedAt = now }
                    }.trim()
                    val cited = hits.joinToString(", ") { "[" + com.rassvet.essential.data.recording.RecordingAi.timecode(it.startMs) + "]" }
                    if (answer.isBlank()) answer else answer + (if (cited.isNotEmpty()) "\n\n" + context.getString(R.string.chat_sources_prefix) + " " + cited else "")
                } else
                chatEngine.streamReply(
                    ChatEngine.Request(
                        userVisibleText = userText,
                        queryForSearch = userText,
                        history = history,
                        activeModel = activeGguf,
                        vaultStored = null,
                        streamReplies = streamReplies,
                        semanticSearchEnabled = false,
                        cloudMode = false,
                        cloudProvider = "local",
                        apiBase = apiBase,
                        authToken = token,
                        openAiBase = null,
                        openAiKey = null,
                        openAiModel = null,
                        geminiKey = null,
                        geminiModel = null,
                    ),
                ) { chunk ->
                    if (streamReplies) output.append(chunk) else output.setLength(0).also { output.append(chunk) }
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastPublishedAt >= 50L) {
                        messages[assistantIdx] = "assistant" to output.toString()
                        lastPublishedAt = now
                    }
                }.text
            }.getOrElse { EssentialApiErrors.chatMessage(context, it) }
            isThinking = false
            haptics.onGenerationComplete()
            val finalText = reply.ifBlank { context.getString(R.string.chat_error_no_response) }
            messages[assistantIdx] = "assistant" to finalText
            runCatching { persist(false, finalText, null) }
        }
    }

    if (showPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.chat_pick_conversation)) },
            text = {
                val done = allRecordings.filter { it.processingStatus == "completed" }
                if (done.isEmpty()) Text(stringResource(R.string.chat_no_conversations))
                else LazyColumn {
                    items(done.size) { i ->
                        val r = done[i]
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(r.createdAt))) },
                            modifier = Modifier.clickable { attached = r; showPicker = false },
                        )
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatHeader(
                modelName = activeGguf?.let(ChatModelSelection::shortLocalModelLabel),
                onBack = onBack,
                onModels = onModels,
                onHistory = { reloadSessions(); historyOpen = true },
                onNewChat = { if (!isThinking) startNewChat() },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attached?.let { rec ->
                    androidx.compose.material3.InputChip(
                        selected = true, onClick = { showPicker = true },
                        label = { Text(stringResource(R.string.chat_attached_conversation, rec.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(LazureIcons.AudioFile, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(LazureIcons.Close, stringResource(R.string.chat_detach_conversation), Modifier.size(18.dp).clickable { attached = null }) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(onClick = { showPicker = true }, enabled = !isThinking) {
                        Icon(LazureIcons.AudioFile, contentDescription = stringResource(R.string.chat_attach_conversation),
                            tint = if (attached != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                        shape = RoundedCornerShape(26.dp),
                        enabled = !isThinking,
                        maxLines = 5,
                    )
                    Surface(
                        onClick = {
                            val clean = input.trim()
                            if (clean.isNotEmpty() && !isThinking) {
                                input = ""
                                haptics.onMessageSent()
                                sendMessage(clean)
                            }
                        },
                        enabled = !isThinking && input.isNotBlank(),
                        shape = RoundedCornerShape(22.dp),
                        color = if (input.isNotBlank() && !isThinking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (input.isNotBlank() && !isThinking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(54.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(LazureIcons.Send, contentDescription = stringResource(R.string.chat_send))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(LazureIcons.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
                        }
                    }
                    Text(stringResource(R.string.chat_welcome_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
                    Text(
                        stringResource(R.string.chat_welcome_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { input = notesSuggestion }, label = { Text(stringResource(R.string.chat_notes_chip)) }, leadingIcon = { Icon(LazureIcons.AutoAwesome, null) })
                        AssistChip(onClick = onModels, label = { Text(stringResource(R.string.chat_models_chip)) }, leadingIcon = { Icon(LazureIcons.Memory, null) })
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(messages, key = { index, _ -> index }) { _, (role, content) -> ChatMessageItem(role, content) }
                    if (isThinking) item { ThinkingBubble() }
                }
            }
        }
    }
    ChatHistorySheet(
        visible = historyOpen,
        sessions = sessions,
        currentSessionId = sessionId,
        onDismiss = { historyOpen = false },
        onNewChat = { historyOpen = false; if (!isThinking) startNewChat() },
        onSelectSession = { id -> historyOpen = false; if (!isThinking) loadSession(id) },
        onSaveAsNote = { s ->
            scope.launch {
                val msgs = withContext(Dispatchers.IO) { messageDao.forSession(s.id) }
                val text = msgs.joinToString("\n\n") { (if (it.isUser) "— " else "") + it.text }
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText(s.title, text))
                Toast.makeText(context, R.string.toast_chat_copied, Toast.LENGTH_SHORT).show()
            }
        },
        onDeleteSession = { s ->
            scope.launch(Dispatchers.IO) {
                sessionDao.deleteMessagesFor(s.id); sessionDao.deleteById(s.id)
                withContext(Dispatchers.Main) { if (sessionId == s.id) startNewChat(); reloadSessions() }
            }
        },
    )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatHeader(modelName: String?, onBack: () -> Unit, onModels: () -> Unit, onHistory: () -> Unit = {}, onNewChat: () -> Unit = {}) {
    Column {
        TopAppBar(
            title = { Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(LazureIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
            actions = {
                IconButton(onClick = onNewChat, modifier = Modifier.size(48.dp)) { Icon(LazureIcons.Edit, contentDescription = stringResource(R.string.chat_history_new)) }
                IconButton(onClick = onHistory, modifier = Modifier.size(48.dp)) { Icon(LazureIcons.ChatBubbleOutline, contentDescription = stringResource(R.string.chat_history_title)) }
                IconButton(onClick = onModels, modifier = Modifier.size(48.dp)) { Icon(LazureIcons.Memory, contentDescription = stringResource(R.string.chat_models_chip)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        ChatModelPickerPill(
            label = modelName ?: stringResource(R.string.chat_models_chip),
            onClick = onModels,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun ChatMessageItem(role: String, content: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val assistant = role == "assistant"
    val bubble = if (assistant) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer
    val ink = if (assistant) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (assistant) Arrangement.Start else Arrangement.End) {
        Column(
            Modifier
                .widthIn(max = if (assistant) 360.dp else 320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(bubble)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (assistant) MarkdownContent(content, baseColor = ink, mutedColor = ink.copy(alpha = .66f))
            else Text(content, style = MaterialTheme.typography.bodyLarge, color = ink)
            if (assistant && content.isNotBlank()) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(content))
                        Toast.makeText(context, R.string.chat_copied, Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(0.dp),
                ) { Text(stringResource(R.string.chat_copy), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.thinking_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
