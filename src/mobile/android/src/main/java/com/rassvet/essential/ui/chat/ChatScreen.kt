package com.rassvet.essential.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    db: AppDatabase,
    prefs: VaultPreferencesRepository,
    chatEngine: ChatEngine,
    onBack: () -> Unit,
    onModels: () -> Unit = {},
) {
    val context = LocalContext.current
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
            val assistantIdx = messages.size
            messages.add("assistant" to "")
            val history = messages.take(assistantIdx - 1).map { (role, content) -> ChatEngine.HistoryLine(role == "user", content) }
            val output = StringBuilder()
            var lastPublishedAt = 0L
            val reply = runCatching {
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
            messages[assistantIdx] = "assistant" to reply.ifBlank { context.getString(R.string.chat_error_no_response) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatHeader(
                modelName = activeGguf?.let(ChatModelSelection::shortLocalModelLabel),
                onBack = onBack,
                onModels = onModels,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatHeader(modelName: String?, onBack: () -> Unit, onModels: () -> Unit) {
    Column {
        TopAppBar(
            title = { Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(LazureIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back)) } },
            actions = { IconButton(onClick = onModels, modifier = Modifier.size(48.dp)) { Icon(LazureIcons.Memory, contentDescription = stringResource(R.string.chat_models_chip)) } },
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
