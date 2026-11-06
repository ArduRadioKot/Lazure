package com.rassvet.essential.ui.editor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rassvet.essential.R
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.BuildConfig
import com.rassvet.essential.data.api.AttachmentPayload
import com.rassvet.essential.data.api.ChatMessageJson
import com.rassvet.essential.data.api.CloudLlmClient
import com.rassvet.essential.data.api.GeminiDefaults
import com.rassvet.essential.data.api.TimewebCloudAiPreset
import com.rassvet.essential.data.api.openAiChatCompletionsStream
import com.rassvet.essential.data.chat.AttachmentTooLargeException
import com.rassvet.essential.data.chat.ChatAttachment
import com.rassvet.essential.data.chat.encodeAttachment
import androidx.compose.runtime.collectAsState
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.ui.chat.rememberChatHaptics
import com.rassvet.essential.ui.components.ThinkingStatusRow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ChatTurn(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val attachmentNames: List<String> = emptyList(),
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteChatSheet(
    noteTitle: String,
    noteBody: String,
    prefs: VaultPreferencesRepository,
    onApply: (newBody: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val chatHapticsEnabled by prefs.chatHapticsEnabled.collectAsState(initial = true)
    val chatHaptics = rememberChatHaptics(chatHapticsEnabled)

    val messages = remember { mutableStateListOf<ChatTurn>() }
    var nextId by remember { mutableLongStateOf(0L) }
    var input by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()


    val attachments = remember { mutableStateListOf<ChatAttachment>() }
    var attachmentMenuOpen by remember { mutableStateOf(false) }
    var attachmentEncoding by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        attachmentEncoding = true
        scope.launch {
            val compress = prefs.compressImages.first()
            try {
                attachments.add(encodeAttachment(context, uri, compress))
            } catch (e: AttachmentTooLargeException) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_attach_failed, t.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            } finally { attachmentEncoding = false }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        attachmentEncoding = true
        scope.launch {
            val compress = prefs.compressImages.first()
            try {
                attachments.add(encodeAttachment(context, uri, compress))
            } catch (e: AttachmentTooLargeException) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_attach_failed, t.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            } finally { attachmentEncoding = false }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (!ok || uri == null) return@rememberLauncherForActivityResult
        attachmentEncoding = true
        scope.launch {
            val compress = prefs.compressImages.first()
            try {
                attachments.add(encodeAttachment(context, uri, compress))
            } catch (e: AttachmentTooLargeException) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_attach_failed, t.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            } finally { attachmentEncoding = false }
        }
    }

    fun launchCamera() {
        scope.launch(Dispatchers.IO) {
            val dir = java.io.File(context.filesDir, "capture").apply { mkdirs() }
            val file = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            withContext(Dispatchers.Main) {
                pendingCameraUri = uri
                runCatching { cameraLauncher.launch(uri) }
                    .onFailure {
                        Toast.makeText(context, R.string.toast_camera_unavailable, Toast.LENGTH_LONG).show()
                        pendingCameraUri = null
                    }
            }
        }
    }

    val systemPrompt =
        "Ты — редактор заметки пользователя. Заголовок: «${noteTitle.ifBlank { "Без названия" }}».\n" +
            "Содержимое заметки:\n---\n$noteBody\n---\n" +
            "Отвечай кратко на русском. Если уместно, предлагай конкретные правки. " +
            "Если пользователь приложил файл — учитывай его содержимое."

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        val pendingAtt = attachments.toList()
        if (trimmed.isEmpty() && pendingAtt.isEmpty()) return
        chatHaptics.onMessageSent()
        val uId = ++nextId
        val aId = ++nextId
        val visibleText = trimmed.ifBlank { context.getString(R.string.sheet_files_question) }
        messages.add(
            ChatTurn(
                id = uId, isUser = true, text = visibleText,
                attachmentNames = pendingAtt.map { it.displayName },
            ),
        )
        messages.add(ChatTurn(id = aId, isUser = false, text = ""))
        input = ""
        attachments.clear()
        scope.launch {
            try {
                val timewebKey = BuildConfig.OPENAI_COMPAT_API_KEY_LOCAL.trim().takeIf { it.isNotBlank() }
                val geminiKey = (prefs.geminiApiKey.first()?.trim()?.takeIf { it.isNotBlank() }
                    ?: BuildConfig.GEMINI_API_KEY_LOCAL.trim().takeIf { it.isNotBlank() })
                if (timewebKey == null && geminiKey == null) {
                    val idx = messages.indexOfFirst { it.id == aId }
                    if (idx >= 0) messages[idx] = messages[idx].copy(
                        text = context.getString(R.string.sheet_api_key_note_chat),
                    )
                    return@launch
                }
                val history = mutableListOf<ChatMessageJson>()
                history.add(ChatMessageJson("system", systemPrompt))
                messages.dropLast(2).forEach { line ->
                    history.add(ChatMessageJson(if (line.isUser) "user" else "assistant", line.text))
                }
                history.add(ChatMessageJson("user", visibleText))
                val temp = withContext(Dispatchers.IO) { prefs.getLlmRuntimeParams().temperature }
                val payloads = pendingAtt.map { AttachmentPayload(it.mimeType, it.base64, it.displayName) }

                if (timewebKey != null) {
                    openAiChatCompletionsStream(
                        TimewebCloudAiPreset.OPENAI_COMPAT_BASE,
                        timewebKey,
                        TimewebCloudAiPreset.OPENAI_COMPAT_MODEL,
                        history, temp, payloads,
                    ).collect { chunk ->
                        val idx = messages.indexOfFirst { it.id == aId }
                        if (idx >= 0) {
                            messages[idx] = messages[idx].copy(text = messages[idx].text + chunk)
                        }
                    }
                } else if (geminiKey != null) {
                    val geminiModel = prefs.geminiModel.first()?.trim()?.takeIf { it.isNotBlank() }
                        ?: GeminiDefaults.MODEL_ID
                    val reply = withContext(Dispatchers.IO) {
                        val client = CloudLlmClient()
                        try { client.geminiGenerate(geminiKey, geminiModel, history, temp, payloads) }
                        finally { client.close() }
                    }
                    val idx = messages.indexOfFirst { it.id == aId }
                    if (idx >= 0) messages[idx] = messages[idx].copy(text = reply)
                }
            } catch (t: Throwable) {
                val idx = messages.indexOfFirst { it.id == aId }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(
                        text = context.getString(
                            R.string.sheet_error_with_message,
                            t.message ?: context.getString(R.string.sheet_error_generic),
                        ),
                    )
                }
            } finally {
                val finalIdx = messages.indexOfFirst { it.id == aId }
                if (finalIdx >= 0 && messages[finalIdx].text.isEmpty()) {
                    messages[finalIdx] = messages[finalIdx].copy(
                        text = context.getString(R.string.sheet_empty_response),
                    )
                }
                chatHaptics.onGenerationComplete()
            }
        }
    }

    fun applyEdits() {
        if (applying) return
        applying = true
        val applyPrompt =
            "Перепиши заметку целиком, применив все обсуждённые правки. " +
                "Верни ТОЛЬКО новый текст заметки без объяснений, без префиксов, без обрамления."
        val uId = ++nextId
        val aId = ++nextId
        messages.add(
            ChatTurn(
                id = uId,
                isUser = true,
                text = context.getString(R.string.sheet_apply_edits_user),
            ),
        )
        messages.add(ChatTurn(id = aId, isUser = false, text = ""))
        scope.launch {
            try {
                val timewebKey = BuildConfig.OPENAI_COMPAT_API_KEY_LOCAL.trim().takeIf { it.isNotBlank() }
                val geminiKey = (prefs.geminiApiKey.first()?.trim()?.takeIf { it.isNotBlank() }
                    ?: BuildConfig.GEMINI_API_KEY_LOCAL.trim().takeIf { it.isNotBlank() })
                if (timewebKey == null && geminiKey == null) {
                    Toast.makeText(context, R.string.sheet_api_key_missing, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val history = mutableListOf<ChatMessageJson>()
                history.add(ChatMessageJson("system", systemPrompt))
                messages.dropLast(2).forEach { line ->
                    history.add(ChatMessageJson(if (line.isUser) "user" else "assistant", line.text))
                }
                history.add(ChatMessageJson("user", applyPrompt))
                val temp = withContext(Dispatchers.IO) { prefs.getLlmRuntimeParams().temperature }
                val collected = StringBuilder()
                if (timewebKey != null) {
                    openAiChatCompletionsStream(
                        TimewebCloudAiPreset.OPENAI_COMPAT_BASE,
                        timewebKey, TimewebCloudAiPreset.OPENAI_COMPAT_MODEL,
                        history, temp,
                    ).collect { chunk ->
                        collected.append(chunk)
                        val idx = messages.indexOfFirst { it.id == aId }
                        if (idx >= 0) messages[idx] = messages[idx].copy(text = messages[idx].text + chunk)
                    }
                } else if (geminiKey != null) {
                    val geminiModel = prefs.geminiModel.first()?.trim()?.takeIf { it.isNotBlank() }
                        ?: GeminiDefaults.MODEL_ID
                    val reply = withContext(Dispatchers.IO) {
                        val client = CloudLlmClient()
                        try { client.geminiGenerate(geminiKey, geminiModel, history, temp) }
                        finally { client.close() }
                    }
                    collected.append(reply)
                    val idx = messages.indexOfFirst { it.id == aId }
                    if (idx >= 0) messages[idx] = messages[idx].copy(text = reply)
                }
                val newBody = collected.toString().trim()
                if (newBody.isNotEmpty()) onApply(newBody)
            } catch (t: Throwable) {
                val idx = messages.indexOfFirst { it.id == aId }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(
                        text = context.getString(
                            R.string.sheet_error_with_message,
                            t.message ?: context.getString(R.string.sheet_error_generic),
                        ),
                    )
                }
            } finally {
                applying = false
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val lastAssistantHasReply = messages.lastOrNull { !it.isUser }?.text?.isNotBlank() == true
    val showApplyRow = lastAssistantHasReply && !applying

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .fillMaxWidth(),
        ) {

            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp),
                        ),
                )
            }


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = noteTitle.ifBlank { stringResource(R.string.note_untitled) },
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }


            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (messages.isEmpty()) {
                    item("empty") {
                        Text(
                            text = stringResource(R.string.sheet_note_chat_examples),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )
                    }
                }
                items(messages, key = { it.id }) { turn ->
                    if (turn.isUser) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 48.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            if (turn.attachmentNames.isNotEmpty()) {
                                turn.attachmentNames.forEach { name ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AttachFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = name,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = turn.text,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.End),
                            )
                        }
                    } else {
                        if (turn.text.isEmpty()) {
                            SheetThinking()
                        } else {
                            TypewriterMarkdownInSheet(
                                target = turn.text,
                                modifier = Modifier.fillMaxWidth().padding(end = 32.dp),
                            )
                        }
                    }
                }
                if (showApplyRow) {
                    item("apply_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { applyEdits() },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sheet_action_apply))
                            }
                            FilledTonalButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.editor_delete_cancel))
                            }
                        }
                    }
                }
            }


            AnimatedVisibility(
                visible = attachments.isNotEmpty() || attachmentEncoding,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachments.forEachIndexed { idx, att ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = when {
                                        att.isImage -> Icons.Outlined.Image
                                        att.isPdf -> Icons.Outlined.PictureAsPdf
                                        else -> Icons.Outlined.AttachFile
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = att.displayName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                                    maxLines = 1,
                                )
                                IconButton(
                                    onClick = { attachments.removeAt(idx) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.cd_remove_note),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (attachmentEncoding) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = stringResource(R.string.sheet_loading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }


            val borderAlpha by animateFloatAsState(
                targetValue = if (isFocused) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "sheet_input_border",
            )
            val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha * 0.8f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(20.dp),
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                keyboard?.hide()
                                attachmentMenuOpen = true
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.cd_attach),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused },
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp, lineHeight = 22.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None,
                                ),
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send,
                            ),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
                                    if (input.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.sheet_note_chat_hint),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
                                        )
                                    }
                                    inner()
                                }
                            },
                            maxLines = 4,
                        )
                        val canSend = input.isNotBlank() || attachments.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(40.dp)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = {
                                    keyboard?.hide()
                                    sendMessage(input)
                                },
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    containerColor = Color.Transparent,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowUpward,
                                    contentDescription = stringResource(R.string.cd_send),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    if (attachmentMenuOpen) {
        val attachmentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { attachmentMenuOpen = false },
            sheetState = attachmentSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 10.dp, bottom = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 14.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
                SheetAttachmentItem(Icons.Outlined.CameraAlt, stringResource(R.string.chat_add_camera)) {
                    attachmentMenuOpen = false
                    launchCamera()
                }
                SheetAttachmentItem(Icons.Outlined.Image, stringResource(R.string.chat_add_photos)) {
                    attachmentMenuOpen = false
                    photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                SheetAttachmentItem(Icons.Outlined.PictureAsPdf, "PDF-документ") {
                    attachmentMenuOpen = false
                    fileLauncher.launch(arrayOf("application/pdf"))
                }
                SheetAttachmentItem(Icons.Outlined.AttachFile, stringResource(R.string.chat_add_files)) {
                    attachmentMenuOpen = false
                    fileLauncher.launch(arrayOf("*/*"))
                }
            }
        }
    }
}

@Composable
private fun SheetAttachmentItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    val tap = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = tap, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TypewriterMarkdownInSheet(target: String, modifier: Modifier = Modifier) {
    var displayed by remember { mutableStateOf("") }
    LaunchedEffect(target) {
        if (target.length < displayed.length) {
            displayed = target
            return@LaunchedEffect
        }
        while (displayed.length < target.length) {
            val gap = target.length - displayed.length
            val step = when {
                gap > 200 -> (gap / 25).coerceAtLeast(1)
                gap > 50 -> 3
                gap > 20 -> 2
                else -> 1
            }
            displayed = target.substring(0, (displayed.length + step).coerceAtMost(target.length))
            delay(12L)
        }
    }
    MarkdownContent(
        text = displayed,
        modifier = modifier,
        baseColor = MaterialTheme.colorScheme.onSurface,
        mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SheetThinking() {
    val transition = rememberInfiniteTransition(label = "sheet_thinking")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    ThinkingStatusRow(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = pulseAlpha),
        style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
        logoSize = 22.dp,
        spacing = 10.dp,
    )
}

@Composable
private fun SheetMorphBlob(phase: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = minOf(size.width, size.height) * 0.4f
        val n = 8
        val pts = Array(n) { i ->
            val angle = (2.0 * PI * i / n).toFloat()
            val warp = sin(phase + i * 1.3f) * base * 0.28f +
                cos(phase * 0.65f + i * 2.0f) * base * 0.14f
            val r = base + warp
            Offset(cx + r * cos(angle), cy + r * sin(angle))
        }
        val path = Path()
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 0 until n) {
            val prev = pts[(i - 1 + n) % n]
            val curr = pts[i]
            val next = pts[(i + 1) % n]
            val next2 = pts[(i + 2) % n]
            path.cubicTo(
                curr.x + (next.x - prev.x) / 5f,
                curr.y + (next.y - prev.y) / 5f,
                next.x - (next2.x - curr.x) / 5f,
                next.y - (next2.y - curr.y) / 5f,
                next.x,
                next.y,
            )
        }
        path.close()
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF6E6E6E), Color(0xFFCFCFCF)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
        )
    }
}


