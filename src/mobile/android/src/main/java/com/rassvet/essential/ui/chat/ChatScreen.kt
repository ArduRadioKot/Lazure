package com.rassvet.essential.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Memory
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
import com.rassvet.essential.ui.components.ThinkingStatusRow
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
    val semanticSearch by prefs.semanticSearchEnabled.collectAsState(initial = true)
    val hapticsEnabled by prefs.chatHapticsEnabled.collectAsState(initial = true)
    val haptics = rememberChatHaptics(hapticsEnabled)
    val notesSuggestion = stringResource(R.string.chat_notes_suggestion)
    var input by remember { mutableStateOf("") }
    var apiBase by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf<String?>(null) }
    var vaultUri by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<Pair<String, String>>() }
    var isThinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(prefs) {
        kotlinx.coroutines.coroutineScope {
            launch { prefs.apiBaseUrl.collect { apiBase = it } }
            launch { prefs.authToken.collect { token = it } }
            launch { prefs.vaultTreeUri.collect { vaultUri = it } }
        }
    }
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val sendMessage: (String) -> Unit = { userText ->
        messages.add("user" to userText)
        isThinking = true
        scope.launch {
            val assistantIdx = messages.size
            messages.add("assistant" to "")
            val history = messages.take(assistantIdx - 1).map { (role, content) -> ChatEngine.HistoryLine(role == "user", content) }
            val output = StringBuilder()
            val reply = runCatching {
                chatEngine.streamReply(
                    ChatEngine.Request(
                        userVisibleText = userText,
                        queryForSearch = userText,
                        history = history,
                        activeModel = activeGguf,
                        vaultStored = vaultUri,
                        streamReplies = streamReplies,
                        semanticSearchEnabled = semanticSearch,
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
                    messages[assistantIdx] = "assistant" to output.toString()
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
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.chat_local_assistant), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    ChatModelPickerPill(
                        label = activeGguf?.substringBeforeLast('.')?.take(20)
                            ?: stringResource(R.string.models_none_installed),
                        onClick = onModels,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.chat_hint_local),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
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
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
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
                        AssistChip(onClick = { input = notesSuggestion }, label = { Text(stringResource(R.string.chat_notes_chip)) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) })
                        AssistChip(onClick = onModels, label = { Text(stringResource(R.string.chat_models_chip)) }, leadingIcon = { Icon(Icons.Outlined.Memory, null) })
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(messages) { _, (role, content) -> ChatMessageItem(role, content) }
                    if (isThinking) item { ThinkingBubble() }
                }
            }
        }
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
        ThinkingStatusRow(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            logoSize = 30.dp,
            spacing = 8.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
