package com.rassvet.essential.ui.home

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.rassvet.essential.BuildConfig
import com.rassvet.essential.R
import com.rassvet.essential.locale.PlanDisplayLabels
import com.rassvet.essential.data.chat.AttachmentTooLargeException
import com.rassvet.essential.data.chat.ChatAttachment
import com.rassvet.essential.data.chat.ChatEngine
import com.rassvet.essential.ui.chat.ChatAssistantMessageEnter
import com.rassvet.essential.ui.chat.ChatSharedTransitionLayout
import com.rassvet.essential.ui.chat.ChatSourceCardsRow
import com.rassvet.essential.ui.chat.ChatUserMessageEnter
import com.rassvet.essential.ui.chat.NoteCreatedChip
import com.rassvet.essential.ui.chat.rememberChatHaptics
import com.rassvet.essential.data.chat.PersistedAttachment
import com.rassvet.essential.data.chat.attachmentsFromJson
import com.rassvet.essential.data.chat.attachmentsToJson
import com.rassvet.essential.data.chat.sourcesFromJson
import com.rassvet.essential.data.chat.sourcesToJson
import com.rassvet.essential.data.chat.encodeAttachment
import com.rassvet.essential.data.chat.createNoteFromMarkdown
import com.rassvet.essential.data.chat.generateChatTitle
import com.rassvet.essential.data.chat.saveFullChatAsNote
import com.rassvet.essential.data.chat.toPersistedForStorage
import com.rassvet.essential.data.chat.refreshVaultSearchIndex
import com.rassvet.essential.data.api.EssentialApiErrors
import com.rassvet.essential.data.api.GeminiDefaults
import com.rassvet.essential.data.api.fetchAndCacheMeAccount
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.network.NetworkMonitor
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.local.ChatMessageEntity
import com.rassvet.essential.data.local.ChatSessionEntity
import com.rassvet.essential.data.local.UsageActivity
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.llm.ChatModelSelection
import com.rassvet.essential.data.llm.HybridLocalLlmEngine
import com.rassvet.essential.data.llm.LocalModelCatalog
import com.rassvet.essential.data.llm.GgufDownloadPolicy
import com.rassvet.essential.data.llm.GgufRepository
import com.rassvet.essential.data.llm.LocalModelFormats
import com.rassvet.essential.data.llm.LocalModelDownloadController
import com.rassvet.essential.data.llm.LocalModelDownloadState
import com.rassvet.essential.data.llm.LocalLlmGenerationMetrics
import com.rassvet.essential.data.vault.VaultDocuments
import com.rassvet.essential.ui.PendingNoteContext
import com.rassvet.essential.ui.settings.ReaderTypographySettings
import com.rassvet.essential.ui.settings.rememberReaderTypography
import androidx.compose.ui.graphics.lerp
import com.rassvet.essential.ui.components.ThinkingStatusRow
import com.rassvet.essential.ui.theme.EssentialBrand
import com.rassvet.essential.ui.theme.LocalEssentialChrome
import com.rassvet.essential.ui.theme.EssentialDisplayFontFamily
import com.rassvet.essential.ui.chat.ChatUserMessageContent
import com.rassvet.essential.ui.chat.ChatInputFileChip
import com.rassvet.essential.ui.chat.ChatInputImagePreview
import com.rassvet.essential.ui.editor.MarkdownContent
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun inputTextStyle(color: Color, reader: ReaderTypographySettings): TextStyle =
    reader.bodyStyle(color).copy(
        lineHeightStyle =
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

@Composable
private fun EssentialChatInputBar(
    draftText: String,
    onDraftChange: (String) -> Unit,
    attachments: List<ChatAttachment>,
    attachmentEncoding: Boolean,
    onRemoveAttachment: (Int) -> Unit,
    activeNote: PendingNoteContext?,
    onPickNote: () -> Unit,
    onClearNote: () -> Unit,
    onAttachFiles: () -> Unit,
    modelLabel: String,
    modelSpeedLabel: String? = null,
    onPickModel: () -> Unit,
    onSpeech: () -> Unit,
    onSubmit: () -> Unit,
    canSend: Boolean,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    readerTypography: ReaderTypographySettings,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalEssentialChrome.current
    val textStartInset = 12.dp
    val inputFontSize = 17.sp
    val inputLineHeight = 24.sp
    val inputShape = RoundedCornerShape(28.dp)
    val imageAttachments = attachments.filter { it.isImage }
    val fileAttachments = attachments.filter { !it.isImage }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, chrome.inputBorder, inputShape),
        shape = inputShape,
        color = chrome.inputSurface,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        ) {
            if (activeNote != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = null,
                        tint = chrome.inputIcon,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = activeNote.title,
                        color = chrome.hint,
                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onClearNote,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_remove_note),
                            tint = chrome.inputIcon,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (imageAttachments.isNotEmpty() || attachmentEncoding) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = textStartInset, bottom = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEachIndexed { idx, att ->
                        if (att.isImage) {
                            ChatInputImagePreview(
                                attachment = att,
                                onRemove = { onRemoveAttachment(idx) },
                            )
                        }
                    }
                    if (attachmentEncoding && imageAttachments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(chrome.sheetSelectedBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "…",
                                color = chrome.hint,
                                style = TextStyle(fontSize = 20.sp),
                            )
                        }
                    }
                }
            }

            if (fileAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = textStartInset, bottom = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachments.forEachIndexed { idx, att ->
                        if (!att.isImage) {
                            ChatInputFileChip(
                                attachment = att,
                                onRemove = { onRemoveAttachment(idx) },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draftText,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 22.dp, max = 120.dp)
                        .padding(start = textStartInset)
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChange(it.isFocused) },
                    textStyle = inputTextStyle(chrome.primaryText, readerTypography).copy(
                        fontSize = inputFontSize,
                        lineHeight = inputLineHeight,
                    ),
                    cursorBrush = SolidColor(
                        if (isFocused) chrome.primaryText else Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                    singleLine = false,
                    maxLines = 4,
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 22.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (draftText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.home_ask_lazure_hint),
                                    style = inputTextStyle(chrome.hint, readerTypography).copy(
                                        fontSize = inputFontSize,
                                        lineHeight = inputLineHeight,
                                    ),
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )

                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (canSend) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(chrome.inputSendBg, CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onSubmit,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = stringResource(R.string.chat_send),
                                tint = chrome.inputSendIcon,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAttachFiles,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.cd_attach),
                        tint = chrome.inputIcon,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))
                ChatModelPickerPill(
                    label = modelLabel,
                    speedLabel = modelSpeedLabel,
                    onClick = onPickModel,
                )

                Spacer(Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSpeech,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = stringResource(R.string.cd_voice_input),
                            tint = chrome.inputIcon,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPickNote,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = stringResource(R.string.cd_attach_note),
                            tint = chrome.inputIcon,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun MainHomeScreen(
    prefs: VaultPreferencesRepository,
    db: AppDatabase,
    indexRepository: IndexRepository,
    chatEngine: ChatEngine,
    localEngine: HybridLocalLlmEngine,
    networkMonitor: NetworkMonitor,
    chatState: HomeChatState,
    vaultRefreshPulse: Int,
    pendingNoteContext: PendingNoteContext?,
    pendingNoteContextPulse: Int,
    onPendingNoteContextConsumed: () -> Unit,
    onOpenNote: (Uri) -> Unit,
    onSettings: () -> Unit,
    onRecordings: () -> Unit,
) {
    val context = LocalContext.current
    val chrome = LocalEssentialChrome.current
    val messages = chatState.messages
    val attachments = chatState.attachments
    val activeGguf by prefs.activeGgufName.collectAsState(initial = null)
    val homeChatMode by prefs.homeChatMode.collectAsState(initial = "local")
    val geminiKeyPref by prefs.geminiApiKey.collectAsState(initial = null)
    val geminiModelPref by prefs.geminiModel.collectAsState(initial = null)
    val compressImagesPref by prefs.compressImages.collectAsState(initial = true)
    val autoTitleChatsPref by prefs.autoTitleChats.collectAsState(initial = true)
    val typewriterEffectPref by prefs.typewriterEffect.collectAsState(initial = true)
    val semanticSearchPref by prefs.semanticSearchEnabled.collectAsState(initial = true)
    val streamRepliesPref by prefs.streamReplies.collectAsState(initial = true)
    val chatHapticsPref by prefs.chatHapticsEnabled.collectAsState(initial = true)
    val chatHaptics = rememberChatHaptics(chatHapticsPref)
    val usageHeatmap by UsageActivity.observeHeatmap(context).collectAsState(initial = List(30) { 0 })
    val cloudProviderPref by prefs.cloudLlmProvider.collectAsState(initial = "essential")
    val openAiBasePref by prefs.openAiCompatBaseUrl.collectAsState(initial = null)
    val openAiKeyPref by prefs.openAiCompatApiKey.collectAsState(initial = null)
    val openAiModelPref by prefs.openAiCompatModel.collectAsState(initial = null)
    var apiBaseState by remember { mutableStateOf<String?>(null) }
    var authTokenState by remember { mutableStateOf<String?>(null) }
    var authAccountEmailState by remember { mutableStateOf<String?>(null) }
    var authSubscriptionStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()


    var attachmentSheetOpen by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    val modelDownloadController = remember { LocalModelDownloadController.get(context) }
    val modelDownloadState by modelDownloadController.state.collectAsState()
    val ggufAllowCellularPref by prefs.ggufAllowCellular.collectAsState(initial = true)
    var pendingDownloadOption by remember { mutableStateOf<ChatModelSelection.Option?>(null) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var attachmentEncoding by remember { mutableStateOf(false) }
    var noteSourcePickerOpen by remember { mutableStateOf(false) }
    var installedGguf by remember { mutableStateOf<List<String>>(emptyList()) }
    val ggufRepo = remember { GgufRepository(context) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }


    var noteMenuTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var renameDialogFor by remember { mutableStateOf<DocumentFile?>(null) }
    var renameDialogText by remember { mutableStateOf("") }
    var deleteConfirmFor by remember { mutableStateOf<DocumentFile?>(null) }
    var deleteModelConfirmFor by remember { mutableStateOf<ChatModelSelection.Option?>(null) }


    var chatHistoryOpen by remember { mutableStateOf(false) }
    val chatSessions = remember { mutableStateListOf<ChatSessionUi>() }
    val sessionDao = remember { db.chatSessionDao() }
    val messageDao = remember { db.chatMessageDao() }

    fun reloadSessions() {
        scope.launch(Dispatchers.IO) {
            val list = sessionDao.all().map {
                ChatSessionUi(id = it.id, title = it.title, lastUpdatedAt = it.lastUpdatedAt)
            }
            withContext(Dispatchers.Main) {
                chatSessions.clear()
                chatSessions.addAll(list)
            }
        }
    }

    suspend fun ensureSessionSuspend(firstUserText: String): Long =
        withContext(Dispatchers.IO) {
            val existing = chatState.currentSessionId
            if (existing != 0L) return@withContext existing
            val title = firstUserText.take(40).ifBlank { context.getString(R.string.chat_title_new) }
            val newId = sessionDao.insert(
                ChatSessionEntity(
                    title = title,
                    lastUpdatedAt = System.currentTimeMillis(),
                ),
            )
            withContext(Dispatchers.Main) { chatState.currentSessionId = newId }
            newId
        }

    suspend fun persistMessageSuspend(
        sessionId: Long,
        isUser: Boolean,
        text: String,
        attachments: List<PersistedAttachment>,
        sources: List<com.rassvet.essential.data.chat.ChatSourceNote> = emptyList(),
        updateTitleTo: String? = null,
    ) = withContext(Dispatchers.IO) {
        messageDao.insert(
            ChatMessageEntity(
                sessionId = sessionId,
                isUser = isUser,
                text = text,
                attachmentsJson = attachmentsToJson(attachments),
                sourcesJson = sourcesToJson(sources),
                createdAt = System.currentTimeMillis(),
            ),
        )
        val currentTitle = sessionDao.all().firstOrNull { it.id == sessionId }?.title
            ?: context.getString(R.string.chat_title_default)
        val newTitle = updateTitleTo?.take(40)?.ifBlank { null } ?: currentTitle
        sessionDao.touch(sessionId, newTitle, System.currentTimeMillis())
    }

    fun loadSession(sessionId: Long) {
        scope.launch(Dispatchers.IO) {
            val stored = messageDao.forSession(sessionId)
            var id = chatState.nextId
            val lines = stored.map { e ->
                ChatLine(
                    id = ++id,
                    isUser = e.isUser,
                    text = e.text,
                    attachments = attachmentsFromJson(e.attachmentsJson),
                    sources = if (e.isUser) emptyList() else sourcesFromJson(e.sourcesJson),
                )
            }
            withContext(Dispatchers.Main) {
                chatState.nextId = id
                messages.clear()
                messages.addAll(lines)
                chatState.currentSessionId = sessionId
                lines.forEach { chatState.markTypewriterDone(it.id) }
            }
        }
    }

    var emptyPromptNonce by remember { mutableIntStateOf(0) }
    val emptyQuickPrompts =
        remember(emptyPromptNonce) {
            pickEmptyChatQuickPrompts(context.resources)
        }

    fun startNewChat() {
        chatState.startNewChat()
        emptyPromptNonce++
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        attachmentEncoding = true
        scope.launch {
            try {
                val att = encodeAttachment(context, uri, compressImagesPref)
                attachments.add(att)
            } catch (e: AttachmentTooLargeException) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_attach_failed, t.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                attachmentEncoding = false
            }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        attachmentEncoding = true
        scope.launch {
            try {
                val att = encodeAttachment(context, uri, compressImagesPref)
                attachments.add(att)
            } catch (e: AttachmentTooLargeException) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_attach_failed, t.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                attachmentEncoding = false
            }
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
            try {
                val att = encodeAttachment(context, uri, compressImagesPref)
                attachments.add(att)
            } catch (e: AttachmentTooLargeException) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_attach_failed, t.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                attachmentEncoding = false
            }
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
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_camera_unavailable) +
                                (it.message?.let { msg -> ": $msg" } ?: ""),
                            Toast.LENGTH_LONG,
                        ).show()
                        pendingCameraUri = null
                    }
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken =
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
            if (!spoken.isNullOrBlank()) {
                chatState.draftText =
                    if (chatState.draftText.isBlank()) {
                        spoken
                    } else {
                        "${chatState.draftText.trimEnd()} $spoken"
                    }
            }
        }
    }

    fun launchSpeechInput() {
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val locales = AppCompatDelegate.getApplicationLocales()
                val speechTag =
                    if (!locales.isEmpty) {
                        locales[0]?.toLanguageTag() ?: java.util.Locale.getDefault().toLanguageTag()
                    } else {
                        java.util.Locale.getDefault().toLanguageTag()
                    }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechTag)
                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.speech_recognition_prompt))
            }
        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.toast_speech_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(pendingNoteContextPulse) {
        if (pendingNoteContextPulse > 0 && pendingNoteContext != null) {
            chatState.activeNoteContext = pendingNoteContext
            onPendingNoteContextConsumed()
        }
    }

    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val clipboard = LocalClipboard.current
    val readerTypography = rememberReaderTypography(prefs)
    val copiedToast = stringResource(R.string.chat_copied)
    val copyLabel = stringResource(R.string.chat_copy)
    val copyCd = stringResource(R.string.cd_copy)
    val shareCd = stringResource(R.string.cd_share)
    val shareChooserTitle = stringResource(R.string.chat_share)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val vaultStored by prefs.vaultTreeUri.collectAsState(initial = null)
    var vaultTitle by remember { mutableStateOf("Lazure") }
    var vaultTiles by remember { mutableStateOf<List<NoteVaultTile>>(emptyList()) }

    suspend fun buildVaultTiles(files: List<DocumentFile>, stored: String): List<NoteVaultTile> {
        val indexMap = indexRepository.allNotes().associateBy { it.uri }
        var pruned = false
        val tiles = ArrayList<NoteVaultTile>(files.size)
        for (file in files) {
            val text = runCatching { VaultDocuments.readText(context, file, stored) }.getOrDefault("")
            if (isVaultNoteEmpty(text)) {
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    pruned = true
                }
                continue
            }
            val untitled = context.getString(R.string.note_untitled)
            val title = displayTitleForFile(context, file).takeIf { it != untitled }
                ?: indexMap[file.uri.toString()]?.title?.takeIf { it.isNotBlank() }
                ?: untitled
            val summary = extractNoteSummary(text, title)?.ifBlank { null }
                ?: summaryFromIndexPreview(indexMap[file.uri.toString()]?.preview ?: "")
            tiles.add(
                NoteVaultTile(
                    uri = file.uri,
                    file = file,
                    title = title,
                    summary = summary,
                ),
            )
        }
        if (pruned) {
            indexRepository.rebuild(context, stored)
        }
        return tiles
    }

    fun loadVaultFilesList(rebuildIndex: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val s = vaultStored
            if (s == null) {
                withContext(Dispatchers.Main) { vaultTiles = emptyList() }
                return@launch
            }
            if (rebuildIndex) {
                indexRepository.rebuild(context, s)
                refreshVaultSearchIndex(context, s, indexRepository)
            }
            val root = VaultDocuments.resolveRoot(context, s)
            val files =
                if (root != null) {
                    VaultDocuments.listMarkdown(root)
                        .sortedBy { it.name?.lowercase(Locale.getDefault()) }
                } else {
                    emptyList()
                }
            val tiles = buildVaultTiles(files, s)
            withContext(Dispatchers.Main) { vaultTiles = tiles }
        }
    }

    fun selectNoteSource(tile: NoteVaultTile) {
        scope.launch {
            val s = vaultStored ?: return@launch
            val body = withContext(Dispatchers.IO) {
                when (val file = tile.file) {
                    null -> runCatching {
                        VaultDocuments.readDocumentText(context, s, tile.uri)
                    }.getOrDefault("")
                    else -> runCatching { VaultDocuments.readText(context, file, s) }.getOrDefault("")
                }
            }
            chatState.activeNoteContext = PendingNoteContext(
                title = tile.title,
                body = body,
                uri = tile.uri,
            )
        }
    }

    LaunchedEffect(vaultStored) {
        val s = vaultStored
        if (s == null) {
            vaultTitle = "Lazure"
            vaultTiles = emptyList()
        } else {
            val (title, tiles) =
                withContext(Dispatchers.IO) {
                    indexRepository.rebuild(context, s)
                    refreshVaultSearchIndex(context, s, indexRepository)
                    val t = VaultDocuments.vaultDisplayName(context, s)
                    val root = VaultDocuments.resolveRoot(context, s)
                    val f =
                        if (root != null) {
                            VaultDocuments.listMarkdown(root)
                                .sortedBy { it.name?.lowercase(Locale.getDefault()) }
                        } else {
                            emptyList()
                        }
                    Pair(t, buildVaultTiles(f, s))
                }
            vaultTitle = title
            vaultTiles = tiles
        }
    }

    LaunchedEffect(vaultRefreshPulse) {
        if (vaultRefreshPulse > 0) {
            loadVaultFilesList(rebuildIndex = true)
        }
    }

    suspend fun refreshInstalledGguf(): List<String> =
        withContext(Dispatchers.IO) {
            ggufRepo.pruneIncompleteModels()
            ggufRepo.listVerifiedLocalModelFileNames()
        }

    LaunchedEffect(Unit) {
        ChatEngine.restoreModelFromHomeChatMode(prefs)
        val list = refreshInstalledGguf()
        installedGguf = list
    }

    LaunchedEffect(activeGguf, homeChatMode, chatState.currentSessionId) {
        if (ChatModelSelection.isCloudMode(homeChatMode, activeGguf)) {
            withContext(Dispatchers.Default) { localEngine.releaseModel() }
            return@LaunchedEffect
        }
        val modelId = activeGguf?.trim().orEmpty()
        if (modelId.isBlank() || !LocalModelFormats.isLocalModelFileName(modelId)) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            localEngine.warmupModel(
                modelId = modelId,
                chatSessionKey = chatState.currentSessionId,
            )
        }
    }

    LaunchedEffect(modelPickerOpen) {
        if (modelPickerOpen) {
            installedGguf = refreshInstalledGguf()
        }
    }

    val modelPillLabel =
        remember(homeChatMode, activeGguf, cloudProviderPref, installedGguf) {
            ChatModelSelection.pillLabel(
                context = context,
                homeChatMode = homeChatMode,
                activeLocalModel = activeGguf,
                cloudProvider = cloudProviderPref,
                installedLocalModels = installedGguf,
            )
        }
    val localMetrics by LocalLlmGenerationMetrics.snapshot.collectAsState()
    val isLocalChatMode =
        remember(homeChatMode, activeGguf) {
            !ChatModelSelection.isCloudMode(homeChatMode, activeGguf)
        }
    LaunchedEffect(homeChatMode, activeGguf) {
        if (
            ChatModelSelection.isCloudMode(homeChatMode, activeGguf) &&
            !networkMonitor.isOnline()
        ) {
            Toast.makeText(
                context,
                context.getString(R.string.chat_offline_cloud_hint),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val isLocalGenerating =
        isLocalChatMode &&
            messages.any { !it.isUser && !it.streamComplete }
    val localGenLoadingHint = stringResource(R.string.local_gen_loading)
    val localGenWeightsHint = stringResource(R.string.local_gen_weights_loading)
    val localGenPrefillHint = stringResource(R.string.local_gen_prefill)
    val modelSpeedLabel =
        if (isLocalGenerating) {
            LocalLlmGenerationMetrics.formatProgressLabel(
                snap = localMetrics,
                loadingHint = localGenLoadingHint,
                weightsLoadingHint = localGenWeightsHint,
                prefillHint = localGenPrefillHint,
            )
        } else {
            null
        }

    val modelOptions =
        remember(homeChatMode, activeGguf, cloudProviderPref, installedGguf) {
            ChatModelSelection.buildOptions(
                context = context,
                installedLocalModels = installedGguf,
                homeChatMode = homeChatMode,
                activeLocalModel = activeGguf,
                cloudProvider = cloudProviderPref,
            )
        }

    val selectedModelId =
        remember(homeChatMode, activeGguf, cloudProviderPref) {
            ChatModelSelection.currentSelectionId(homeChatMode, activeGguf, cloudProviderPref)
        }

    val deviceRamMb = remember { ChatModelSelection.deviceTotalRamMb(context) }

    fun modelDownloadErrorMessage(errorKey: String?, throwable: Throwable?): String =
        when (errorKey) {
            "wifi_required" -> context.getString(R.string.chat_model_download_wifi)
            "storage_required" -> context.getString(R.string.chat_model_download_storage)
            else ->
                context.getString(
                    R.string.chat_model_download_failed,
                    throwable?.message ?: errorKey ?: "",
                )
        }

    fun onModelDownloadFinished(result: Result<Unit>, preset: LocalModelCatalog.Preset) {
        if (result.isSuccess) {
            scope.launch {
                installedGguf = refreshInstalledGguf()
                Toast.makeText(context, R.string.chat_model_download_done, Toast.LENGTH_SHORT).show()
                ChatModelSelection.applySelection(
                    prefs,
                    ChatModelSelection.Option(
                        id = "local:${preset.fileName}",
                        pillLabel = ChatModelSelection.shortLocalModelLabel(preset.fileName),
                        title = preset.label,
                        subtitle = preset.info,
                        description = preset.description,
                        sizeLabel = preset.sizeLabel,
                        kind = ChatModelSelection.Kind.LocalModel,
                        localFileName = preset.fileName,
                    ),
                )
            }
            return
        }
        val err = result.exceptionOrNull()
        if (err is CancellationException) return
        Toast.makeText(
            context,
            modelDownloadErrorMessage(modelDownloadState.errorMessage, err),
            Toast.LENGTH_LONG,
        ).show()
    }

    fun startModelDownload(option: ChatModelSelection.Option) {
        val preset = ChatModelSelection.presetForOption(option) ?: return
        if (modelDownloadState.isActive &&
            modelDownloadState.fileName != null &&
            modelDownloadState.fileName != preset.fileName
        ) {
            return
        }
        modelDownloadController.start(preset, ggufAllowCellularPref) { result, finishedPreset ->
            onModelDownloadFinished(result, finishedPreset)
        }
    }

    fun resumeModelDownload(option: ChatModelSelection.Option) {
        val preset = ChatModelSelection.presetForOption(option) ?: return
        modelDownloadController.resume(preset, ggufAllowCellularPref) { result, finishedPreset ->
            onModelDownloadFinished(result, finishedPreset)
        }
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionGranted = granted
            pendingDownloadOption?.let { option ->
                pendingDownloadOption = null
                if (granted) {
                    startModelDownload(option)
                }
            }
        }

    fun downloadGgufModel(option: ChatModelSelection.Option) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
            pendingDownloadOption = option
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startModelDownload(option)
    }

    LaunchedEffect(prefs) {
        kotlinx.coroutines.coroutineScope {
            launch { prefs.apiBaseUrl.collect { apiBaseState = it } }
            launch { prefs.authToken.collect { authTokenState = it } }
            launch { prefs.authAccountEmail.collect { authAccountEmailState = it } }
        }
    }

    val defaultApiBase = stringResource(R.string.default_api_base)
    LaunchedEffect(authTokenState, apiBaseState, defaultApiBase) {
        val token = authTokenState?.trim().orEmpty()
        val base =
            com.rassvet.essential.data.api.ApiBaseUrls.normalize(
                apiBaseState?.trim()?.takeIf { it.isNotBlank() } ?: defaultApiBase.trim(),
            )
        if (token.isEmpty()) {
            authSubscriptionStatus = null
            return@LaunchedEffect
        }
        authSubscriptionStatus =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    prefs.fetchAndCacheMeAccount(base, token).subscriptionStatus
                }.getOrNull()
            }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { UsageActivity.syncFromServer(context, base, token) }
        }
    }

    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    LaunchedEffect(imeInsets, density) {
        var imeWasVisible = false
        snapshotFlow { imeInsets.getBottom(density) }
            .collect { imeBottom ->
                if (imeBottom > 0) {
                    imeWasVisible = true
                } else if (imeWasVisible) {
                    focusManager.clearFocus(force = true)
                    imeWasVisible = false
                }
            }
    }

    fun submitMessage(raw: String) {
        val trimmed = raw.trim()
        val pendingAttachments = attachments.toList()
        if (trimmed.isEmpty() && pendingAttachments.isEmpty()) return

        val noteCtx = chatState.activeNoteContext
        val hasAttachments = pendingAttachments.isNotEmpty()
        val userVisibleText = ChatEngine.buildUserVisibleText(trimmed, noteCtx, hasAttachments)

        chatState.draftText = ""
        attachments.clear()
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        chatHaptics.onMessageSent()

        val queryForSuggestion = trimmed
        val isCloud = ChatModelSelection.isCloudMode(homeChatMode, activeGguf)
        val resolvedCloudProvider =
            ChatModelSelection.resolveCloudProvider(homeChatMode, activeGguf, cloudProviderPref)

        scope.launch {
            withContext(Dispatchers.IO) {
                UsageActivity.recordPrompt(context)
            }
            val persistedAtts = withContext(Dispatchers.IO) {
                pendingAttachments.map { it.toPersistedForStorage() }
            }

            val uId = ++chatState.nextId
            val aId = ++chatState.nextId
            messages.add(
                ChatLine(
                    id = uId,
                    isUser = true,
                    text = userVisibleText,
                    attachments = persistedAtts,
                    animateEnter = true,
                ),
            )
            messages.add(
                ChatLine(
                    id = aId,
                    isUser = false,
                    text = "",
                    streamComplete = false,
                    animateEnter = true,
                ),
            )

            val history = messages
                .take((messages.size - 2).coerceAtLeast(0))
                .map { ChatEngine.HistoryLine(it.isUser, it.text) }

            val sessionId = ensureSessionSuspend(userVisibleText)
            persistMessageSuspend(
                sessionId = sessionId,
                isUser = true,
                text = userVisibleText,
                attachments = persistedAtts,
                updateTitleTo = userVisibleText,
            )

            var replySources = emptyList<com.rassvet.essential.data.chat.ChatSourceNote>()
            try {
                val streamResult =
                    chatEngine.streamReply(
                    ChatEngine.Request(
                        userVisibleText = userVisibleText,
                        queryForSearch = trimmed,
                        noteContext = noteCtx,
                        attachments = pendingAttachments,
                        history = history,
                        activeModel = activeGguf,
                        vaultStored = vaultStored,
                        chatSessionKey = sessionId,
                        streamReplies = streamRepliesPref,
                        semanticSearchEnabled = semanticSearchPref,
                        cloudMode = isCloud,
                        cloudProvider = if (isCloud) resolvedCloudProvider else cloudProviderPref,
                        apiBase = apiBaseState,
                        authToken = authTokenState,
                        openAiBase = openAiBasePref,
                        openAiKey = openAiKeyPref,
                        openAiModel = openAiModelPref,
                        geminiKey = geminiKeyPref,
                        geminiModel = geminiModelPref,
                    ),
                ) { chunk ->
                    scope.launch(Dispatchers.Main.immediate) {
                        val idx = messages.indexOfFirst { it.id == aId }
                        if (idx >= 0) {
                            val line = messages[idx]
                            messages[idx] =
                                if (streamRepliesPref) {
                                    line.copy(text = line.text + chunk)
                                } else {
                                    line.copy(text = chunk)
                                }
                        }
                    }
                }
                replySources = streamResult.sources
            } catch (t: Throwable) {
                val idx = messages.indexOfFirst { it.id == aId }
                if (idx >= 0) {
                    val errMsg = EssentialApiErrors.chatMessage(context, t)
                    val current = messages[idx].text
                    val merged = if (current.isEmpty()) errMsg else "$current\n\n$errMsg"
                    messages[idx] = messages[idx].copy(text = merged)
                }
            }
            val finalIdx = messages.indexOfFirst { it.id == aId }
            if (finalIdx >= 0 && messages[finalIdx].text.isEmpty()) {
                val fallback = context.getString(R.string.chat_empty_model_response)
                messages[finalIdx] = messages[finalIdx].copy(text = fallback)
            }
            val finalReply = if (finalIdx >= 0) messages[finalIdx].text else ""
            val editSuggestion = noteCtx?.let { buildNoteEditSuggestion(queryForSuggestion, finalReply, it) }
            val createSuggestion = if (noteCtx == null) {
                buildNoteCreateSuggestion(queryForSuggestion, finalReply)
            } else {
                null
            }
            var createdUri: android.net.Uri? = null
            var createdTitle: String? = null
            var pendingCreate = createSuggestion
            if (pendingCreate != null && looksLikeNoteCreateRequest(queryForSuggestion)) {
                createdUri = withContext(Dispatchers.IO) {
                    createNoteFromMarkdown(context, vaultStored, pendingCreate!!.markdown)
                }
                if (createdUri != null) {
                    createdTitle = pendingCreate.title
                    loadVaultFilesList()
                    pendingCreate = null
                }
            }
            if (finalIdx >= 0) {
                messages[finalIdx] = messages[finalIdx].copy(
                    noteSuggestion = editSuggestion,
                    noteCreateSuggestion = pendingCreate,
                    createdNoteUri = createdUri,
                    createdNoteTitle = createdTitle,
                    sources = replySources,
                    streamComplete = true,
                )
                chatState.markTypewriterDone(aId)
            }
            chatHaptics.onGenerationComplete()
            persistMessageSuspend(
                sessionId = sessionId,
                isUser = false,
                text = finalReply,
                attachments = emptyList(),
                sources = replySources,
            )

            val isFirstTurn = messages.size == 2
            if (autoTitleChatsPref && isFirstTurn && finalReply.isNotBlank() && finalReply.length < 6000) {
                scope.launch {
                    val title = generateChatTitle(
                        prefs = prefs,
                        userText = userVisibleText,
                        assistantText = finalReply,
                        geminiKey = geminiKeyPref?.trim()?.takeIf { it.isNotBlank() }
                            ?: BuildConfig.GEMINI_API_KEY_LOCAL.trim().takeIf { it.isNotBlank() },
                        geminiModel = geminiModelPref?.trim()?.takeIf { it.isNotBlank() }
                            ?: GeminiDefaults.MODEL_ID,
                        timewebKey = openAiKeyPref?.trim()?.takeIf { it.isNotBlank() }
                            ?: BuildConfig.OPENAI_COMPAT_API_KEY_LOCAL.trim().takeIf { it.isNotBlank() },
                        preferTimeweb = isCloud,
                    )
                    if (!title.isNullOrBlank()) {
                        withContext(Dispatchers.IO) {
                            sessionDao.touch(sessionId, title, System.currentTimeMillis())
                        }
                        reloadSessions()
                    }
                }
            }
        }
    }

    fun deleteGgufModel(option: ChatModelSelection.Option) {
        val fileName = option.localFileName ?: return
        if (modelDownloadState.fileName == fileName && modelDownloadState.isActive) {
            modelDownloadController.cancel()
        }
        scope.launch {
            val deleted = withContext(Dispatchers.IO) { ggufRepo.deleteModel(fileName) }
            if (!deleted) {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.chat_model_download_failed,
                        context.getString(R.string.chat_model_delete_file_failed),
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            installedGguf = refreshInstalledGguf()
            if (activeGguf == fileName) {
                val fallback =
                    if (installedGguf.isNotEmpty()) {
                        val name = installedGguf.first()
                        val preset = LocalModelCatalog.presetForFileName(name)
                        ChatModelSelection.Option(
                            id = "local:$name",
                            pillLabel = ChatModelSelection.shortLocalModelLabel(name),
                            title = preset?.label ?: name,
                            subtitle = preset?.info,
                            description = preset?.description,
                            sizeLabel = preset?.sizeLabel,
                            kind = ChatModelSelection.Kind.LocalModel,
                            localFileName = name,
                        )
                    } else {
                        null
                    }
                if (fallback != null) {
                    ChatModelSelection.applySelection(prefs, fallback)
                } else {
                    prefs.setActiveGguf(null)
                    prefs.setHomeChatMode("local")
                }
            }
            Toast.makeText(context, R.string.chat_model_delete_done, Toast.LENGTH_SHORT).show()
            deleteModelConfirmFor = null
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = messages.lastOrNull()
            Triple(
                last?.id ?: 0L,
                last?.text?.length ?: 0,
                last?.let { !it.isUser && !it.streamComplete } == true,
            )
        }.collect { (_, _, streaming) ->
            if (messages.isEmpty()) return@collect
            val idx = messages.lastIndex
            if (streaming) {
                listState.scrollToItem(idx, scrollOffset = Int.MAX_VALUE / 4)
            } else {
                listState.animateScrollToItem(idx)
            }
        }
    }


    val horizontalPadding = 12.dp
    val bottomPadding = 10.dp

    val createNewNote: () -> Unit = {
        scope.launch {
            val s = vaultStored
            if (s == null) {
                Toast.makeText(context, R.string.settings_toast_pick_vault, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val root = withContext(Dispatchers.IO) { VaultDocuments.resolveRoot(context, s) }
            if (root == null || !root.isDirectory) {
                Toast.makeText(
                    context,
                    context.getString(R.string.vault_error_unavailable),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            if (!root.canWrite()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.vault_error_no_write),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val createdUri = withContext(Dispatchers.IO) {
                val viaDocFile = VaultDocuments.createNewMarkdownFile(root)
                viaDocFile?.uri
                    ?: VaultDocuments.createMarkdownDocumentRaw(context, s, "Untitled.md")
            }
            if (createdUri == null) {
                Toast.makeText(
                    context,
                    "SAF не позволил создать файл (провайдер вернул null)",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                UsageActivity.recordNoteCreated(context)
            }
            loadVaultFilesList()
            drawerState.close()
            onOpenNote(createdUri)
        }
        Unit
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {


            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = androidx.compose.ui.graphics.RectangleShape,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { scope.launch { drawerState.close() } },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                text = vaultTitle.ifBlank { stringResource(R.string.notes_title) },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = TextStyle(
                                    fontSize = 22.sp,
                                    lineHeight = 28.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.4).sp,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.drawer_notes_count,
                                    vaultTiles.size,
                                    vaultTiles.size,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                            )
                        }
                        FilledTonalIconButton(onClick = createNewNote) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.home_drawer_new_note),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }


                    if (vaultTiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.home_drawer_no_files),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                            )
                        }
                    } else {
                        NoteVaultTileGrid(
                            tiles = vaultTiles,
                            modifier = Modifier.weight(1f),
                            onOpen = { uri ->
                                scope.launch {
                                    drawerState.close()
                                    onOpenNote(uri)
                                }
                            },
                            onLongPress = { file -> noteMenuTarget = file },
                        )
                    }


                    val isSignedIn = !authTokenState.isNullOrBlank()
                    val accountEmail = authAccountEmailState?.trim().orEmpty()
                    val accountLabel =
                        when {
                            isSignedIn && accountEmail.isNotEmpty() -> accountEmail
                            isSignedIn -> stringResource(R.string.home_drawer_account_default)
                            else -> stringResource(R.string.home_drawer_account_guest)
                        }
                    val planLabel =
                        when {
                            !isSignedIn -> null
                            authSubscriptionStatus == "subscription" -> PlanDisplayLabels.PRO
                            else -> PlanDisplayLabels.FREE
                        }
                    DrawerAccountBar(
                        accountLabel = accountLabel,
                        planLabel = planLabel,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        onSettings = {
                            scope.launch { drawerState.close() }
                            onSettings()
                        },
                        onRecordings = {
                            scope.launch { drawerState.close() }
                            onRecordings()
                        },
                    )
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding(),
            ) {


            val onSurfaceTitle = MaterialTheme.colorScheme.onSurface
            val streamingTitleColor = lerp(onSurfaceTitle, EssentialBrand, 0.82f)
            val essentialTitleColor by animateColorAsState(
                targetValue = if (isLocalGenerating) streamingTitleColor else onSurfaceTitle,
                animationSpec = tween(
                    durationMillis = if (isLocalGenerating) 420 else 300,
                    easing = FastOutSlowInEasing,
                ),
                label = "essentialTitleStream",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        loadVaultFilesList()
                        scope.launch { drawerState.open() }
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = stringResource(R.string.cd_open_menu),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val titleTap = remember { MutableInteractionSource() }
                    Text(
                        text = "Lazure",
                        color = essentialTitleColor,
                        style = TextStyle(
                            fontFamily = EssentialDisplayFontFamily,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.4).sp,
                        ),
                        modifier = Modifier
                            .clickable(
                                interactionSource = titleTap,
                                indication = null,
                                onClick = {
                                    keyboard?.hide()
                                    reloadSessions()
                                    chatHistoryOpen = true
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                IconButton(
                    onClick = { startNewChat() },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddComment,
                        contentDescription = stringResource(R.string.cd_new_chat),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
            val restLift = maxHeight * 0.1f
            val focusExtraLift = (LocalConfiguration.current.screenHeightDp / 6).dp
            val focusedLift = restLift + focusExtraLift
            val emptyChatPrompt =
                remember(emptyPromptNonce, messages.isEmpty()) {
                    if (messages.isEmpty()) {
                        randomEmptyChatPrompt(context.resources)
                    } else {
                        ""
                    }
                }
            val emptyPlaceholderYOffset by animateDpAsState(
                targetValue = if (isFocused) focusedLift else restLift,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "emptyChatLift",
            )
            ChatSharedTransitionLayout(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .fillMaxWidth(),
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 176.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(
                    items = messages,
                    key = { it.id },
                ) { line ->
                    val copyInteraction = remember(line.id) { MutableInteractionSource() }
                    if (line.isUser) {
                        ChatUserMessageEnter(
                            messageId = line.id,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp),
                        ) {
                            ChatUserMessageContent(
                                text = line.text,
                                attachments = line.attachments,
                                textStyle = readerTypography.bodyStyle(chrome.primaryText),
                            )
                        }
                    } else {
                        ChatAssistantMessageEnter(
                            messageId = line.id,
                            animate = line.animateEnter,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    ),
                        ) {
                            if (line.text.isEmpty()) {
                                ThinkingIndicator(reader = readerTypography)
                            } else {
                                TypewriterMarkdown(
                                    messageId = line.id,
                                    target = line.text,
                                    streamComplete = line.streamComplete,
                                    typewriterDoneIds = chatState.typewriterDoneIds,
                                    onTypewriterDone = chatState::markTypewriterDone,
                                    modifier = Modifier.fillMaxWidth(),
                                    baseColor = chrome.assistantText,
                                    mutedColor = chrome.hint,
                                    enableTypewriter = typewriterEffectPref,
                                    reader = readerTypography,
                                )
                                val showSources = line.streamComplete && line.sources.isNotEmpty()
                                val showCreateSuggestion = line.noteCreateSuggestion != null
                                if (showSources || showCreateSuggestion) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .offset(y = (-6).dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (showSources) {
                                            ChatSourceCardsRow(
                                                sources = line.sources,
                                                visible = true,
                                                onOpenNote = onOpenNote,
                                            )
                                        }
                                        line.noteCreateSuggestion?.let { createSuggestion ->
                                            NoteCreateSuggestionCard(
                                                suggestion = createSuggestion,
                                                assistantText = chrome.assistantText,
                                                mutedColor = chrome.hint,
                                                onCreate = {
                                                    scope.launch {
                                                        val uri = withContext(Dispatchers.IO) {
                                                            createNoteFromMarkdown(
                                                                context = context,
                                                                vaultStored = vaultStored,
                                                                markdown = createSuggestion.markdown,
                                                            )
                                                        }
                                                        if (uri != null) {
                                                            loadVaultFilesList()
                                                            val i = messages.indexOfFirst { it.id == line.id }
                                                            if (i >= 0) {
                                                                messages[i] = messages[i].copy(
                                                                    noteCreateSuggestion = null,
                                                                    createdNoteUri = uri,
                                                                    createdNoteTitle = createSuggestion.title,
                                                                )
                                                            }
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                R.string.toast_create_note_failed,
                                                                Toast.LENGTH_LONG,
                                                            ).show()
                                                        }
                                                    }
                                                },
                                                onDismiss = {
                                                    val i = messages.indexOfFirst { it.id == line.id }
                                                    if (i >= 0) messages[i] = messages[i].copy(noteCreateSuggestion = null)
                                                },
                                            )
                                        }
                                    }
                                }
                                line.noteSuggestion?.let { suggestion ->
                                    NoteEditSuggestionCard(
                                        suggestion = suggestion,
                                        assistantText = chrome.assistantText,
                                        mutedColor = chrome.hint,
                                        onApplyReplace = {
                                            scope.launch {
                                                val ctx = chatState.activeNoteContext
                                                val ok = applyNoteSourcePatch(
                                                    context = context,
                                                    vaultStored = vaultStored,
                                                    uri = suggestion.noteUri,
                                                    originalBody = ctx?.body ?: "",
                                                    suggestedMarkdown = suggestion.suggestedMarkdown,
                                                    mode = NotePatchMode.Replace,
                                                )
                                                if (ok) {
                                                    val updated = suggestion.suggestedMarkdown.trim()
                                                    chatState.activeNoteContext = ctx?.copy(body = updated)
                                                        ?: PendingNoteContext(
                                                            suggestion.noteTitle,
                                                            updated,
                                                            suggestion.noteUri,
                                                        )
                                                    loadVaultFilesList()
                                                    Toast.makeText(context, R.string.toast_note_updated, Toast.LENGTH_SHORT).show()
                                                    val i = messages.indexOfFirst { it.id == line.id }
                                                    if (i >= 0) messages[i] = messages[i].copy(noteSuggestion = null)
                                                } else {
                                                    Toast.makeText(context, R.string.toast_save_failed, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        onApplyAppend = {
                                            scope.launch {
                                                val ctx = chatState.activeNoteContext
                                                val base = ctx?.body ?: ""
                                                val ok = applyNoteSourcePatch(
                                                    context = context,
                                                    vaultStored = vaultStored,
                                                    uri = suggestion.noteUri,
                                                    originalBody = base,
                                                    suggestedMarkdown = suggestion.suggestedMarkdown,
                                                    mode = NotePatchMode.Append,
                                                )
                                                if (ok) {
                                                    val updated = if (base.trim().isEmpty()) {
                                                        suggestion.suggestedMarkdown.trim()
                                                    } else {
                                                        "${base.trimEnd()}\n\n${suggestion.suggestedMarkdown.trim()}"
                                                    }
                                                    chatState.activeNoteContext = ctx?.copy(body = updated)
                                                        ?: PendingNoteContext(
                                                            suggestion.noteTitle,
                                                            updated,
                                                            suggestion.noteUri,
                                                        )
                                                    loadVaultFilesList()
                                                    Toast.makeText(context, R.string.toast_note_appended, Toast.LENGTH_SHORT).show()
                                                    val i = messages.indexOfFirst { it.id == line.id }
                                                    if (i >= 0) messages[i] = messages[i].copy(noteSuggestion = null)
                                                } else {
                                                    Toast.makeText(context, R.string.toast_save_failed, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        onOpenEditor = { onOpenNote(suggestion.noteUri) },
                                        onDismiss = {
                                            val i = messages.indexOfFirst { it.id == line.id }
                                            if (i >= 0) messages[i] = messages[i].copy(noteSuggestion = null)
                                        },
                                    )
                                }
                                line.createdNoteUri?.let { uri ->
                                    NoteCreatedChip(
                                        title = line.createdNoteTitle,
                                        onClick = { onOpenNote(uri) },
                                    )
                                }
                                if (line.streamComplete) {
                                Row(
                                    modifier = Modifier
                                        .then(
                                            if (showSources || showCreateSuggestion) {
                                                Modifier.offset(y = (-4).dp)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .padding(
                                            top = if (showSources || showCreateSuggestion) 0.dp else 8.dp,
                                            bottom = if (showSources || showCreateSuggestion) 0.dp else 4.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    val copyTap = remember(line.id) { MutableInteractionSource() }
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = copyCd,
                                        tint = chrome.hint,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable(
                                                interactionSource = copyTap,
                                                indication = null,
                                                onClick = {
                                                    scope.launch {
                                                        clipboard.setClipEntry(
                                                            ClipEntry(
                                                                ClipData.newPlainText(
                                                                    copyLabel,
                                                                    line.text,
                                                                ),
                                                            ),
                                                        )
                                                    }
                                                    Toast.makeText(
                                                        context, copiedToast, Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                            ),
                                    )
                                    val shareTap = remember(line.id) { MutableInteractionSource() }
                                    Icon(
                                        imageVector = Icons.Outlined.IosShare,
                                        contentDescription = shareCd,
                                        tint = chrome.hint,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable(
                                                interactionSource = shareTap,
                                                indication = null,
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, line.text)
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(intent, shareChooserTitle),
                                                    )
                                                },
                                            ),
                                    )
                                }
                                }
                            }
                        }
                        }
                    }
                }
            }
            }
            if (messages.isEmpty()) {
                EmptyChatPlaceholder(
                    prompt = emptyChatPrompt,
                    promptColor = chrome.hint,
                    reader = readerTypography,
                    quickPrompts = emptyQuickPrompts,
                    onQuickPrompt = { submitMessage(it) },
                    usageDailyTotals = usageHeatmap,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = -emptyPlaceholderYOffset),
                )
            }
            }
            }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = bottomPadding,
                    ),
        ) {
            EssentialChatInputBar(
                draftText = chatState.draftText,
                onDraftChange = { chatState.draftText = it },
                attachments = attachments,
                attachmentEncoding = attachmentEncoding,
                onRemoveAttachment = { idx -> attachments.removeAt(idx) },
                activeNote = chatState.activeNoteContext,
                onPickNote = {
                    if (vaultStored == null) {
                        Toast.makeText(context, R.string.settings_toast_pick_vault, Toast.LENGTH_SHORT).show()
                    } else {
                        loadVaultFilesList()
                        noteSourcePickerOpen = true
                    }
                },
                onClearNote = { chatState.activeNoteContext = null },
                onAttachFiles = {
                    keyboard?.hide()
                    attachmentSheetOpen = true
                },
                modelLabel = modelPillLabel,
                modelSpeedLabel = modelSpeedLabel,
                onPickModel = {
                    keyboard?.hide()
                    modelPickerOpen = true
                },
                onSpeech = { launchSpeechInput() },
                onSubmit = { submitMessage(chatState.draftText) },
                canSend = chatState.draftText.isNotBlank() || attachments.isNotEmpty(),
                isFocused = isFocused,
                onFocusChange = { isFocused = it },
                focusRequester = focusRequester,
                readerTypography = readerTypography,
            )
        }


            BackHandler(enabled = attachmentSheetOpen) { attachmentSheetOpen = false }
            AnimatedVisibility(
                visible = attachmentSheetOpen,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(chrome.overlayScrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { attachmentSheetOpen = false },
                        ),
                ) {}
            }
            AnimatedVisibility(
                visible = attachmentSheetOpen,
                enter = slideInVertically(tween(250)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                AddToChatSheet(
                    onDismiss = { attachmentSheetOpen = false },
                    onCamera = {
                        attachmentSheetOpen = false
                        launchCamera()
                    },
                    onPhotos = {
                        attachmentSheetOpen = false
                        photoLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onFiles = {
                        attachmentSheetOpen = false
                        fileLauncher.launch(arrayOf("*/*"))
                    },
                )
            }

            ChatHistorySheet(
                visible = chatHistoryOpen,
                sessions = chatSessions,
                currentSessionId = chatState.currentSessionId,
                onDismiss = { chatHistoryOpen = false },
                onNewChat = {
                    chatHistoryOpen = false
                    startNewChat()
                },
                onSelectSession = { id ->
                    chatHistoryOpen = false
                    loadSession(id)
                },
                onSaveAsNote = { s ->
                    scope.launch {
                        val msgs = withContext(Dispatchers.IO) {
                            messageDao.forSession(s.id)
                        }
                        val saved = saveFullChatAsNote(
                            context = context,
                            vaultStored = vaultStored,
                            sessionTitle = s.title,
                            messages = msgs,
                        )
                        if (saved != null) {
                            Toast.makeText(
                                context,
                                R.string.toast_chat_saved_as_note,
                                Toast.LENGTH_SHORT,
                            ).show()
                            loadVaultFilesList()
                        } else {
                            Toast.makeText(
                                context,
                                R.string.toast_save_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
                onDeleteSession = { s ->
                    scope.launch(Dispatchers.IO) {
                        sessionDao.deleteMessagesFor(s.id)
                        sessionDao.deleteById(s.id)
                        withContext(Dispatchers.Main) {
                            if (chatState.currentSessionId == s.id) {
                                startNewChat()
                            }
                            reloadSessions()
                        }
                    }
                },
            )

            val target = noteMenuTarget
            BackHandler(enabled = target != null) { noteMenuTarget = null }
            AnimatedVisibility(
                visible = target != null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(chrome.overlayScrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { noteMenuTarget = null },
                        ),
                ) {}
            }
            AnimatedVisibility(
                visible = target != null,
                enter = slideInVertically(tween(250)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(top = 10.dp, bottom = 10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = target?.let { VaultDocuments.displayName(it).removeSuffix(".md").removeSuffix(".MD") }
                                ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
                            modifier = Modifier.padding(horizontal = 20.dp),
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(6.dp))
                        AttachmentMenuItem(
                            icon = Icons.Outlined.DriveFileRenameOutline,
                            title = stringResource(R.string.menu_rename),
                            onClick = {
                                val t = noteMenuTarget ?: return@AttachmentMenuItem
                                renameDialogText = displayTitleForFile(context, t)
                                renameDialogFor = t
                                noteMenuTarget = null
                            },
                        )
                        AttachmentMenuItem(
                            icon = Icons.Outlined.DeleteOutline,
                            title = stringResource(R.string.action_delete),
                            onClick = {
                                deleteConfirmFor = noteMenuTarget
                                noteMenuTarget = null
                            },
                        )
                    }
                }
            }


            val rTarget = renameDialogFor
            if (rTarget != null) {
                AlertDialog(
                    onDismissRequest = { renameDialogFor = null },
                    title = { Text(stringResource(R.string.drawer_rename_note_title)) },
                    text = {
                        OutlinedTextField(
                            value = renameDialogText,
                            onValueChange = { renameDialogText = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.drawer_rename_field_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = renameDialogText.isNotBlank(),
                            onClick = {
                                val newBase = renameDialogText.trim()
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        VaultDocuments.renameMarkdownFile(
                                            context, vaultStored, rTarget.uri, newBase,
                                        )
                                    }
                                    renameDialogFor = null
                                    if (result.ok) {
                                        loadVaultFilesList()
                                        Toast.makeText(context, R.string.toast_renamed, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            result.error ?: context.getString(R.string.toast_rename_failed),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.action_save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameDialogFor = null }) {
                            Text(stringResource(R.string.editor_delete_cancel))
                        }
                    },
                )
            }


            val dTarget = deleteConfirmFor
            if (dTarget != null) {
                AlertDialog(
                    onDismissRequest = { deleteConfirmFor = null },
                    title = { Text(stringResource(R.string.editor_delete_confirm_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.editor_delete_confirm_body,
                                VaultDocuments.displayName(dTarget).removeSuffix(".md").removeSuffix(".MD"),
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        VaultDocuments.deleteDocumentFromVault(
                                            context,
                                            vaultStored,
                                            dTarget.uri,
                                        )
                                    }
                                    deleteConfirmFor = null
                                    if (ok) {
                                        loadVaultFilesList(rebuildIndex = true)
                                        Toast.makeText(context, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, R.string.toast_delete_failed, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.editor_delete_confirm),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmFor = null }) {
                            Text(stringResource(R.string.editor_delete_cancel))
                        }
                    },
                )
            }

            NoteSourcePickerSheet(
                visible = noteSourcePickerOpen,
                tiles = vaultTiles,
                onDismiss = { noteSourcePickerOpen = false },
                onSelect = { selectNoteSource(it) },
            )

            BackHandler(enabled = modelPickerOpen) { modelPickerOpen = false }
            deleteModelConfirmFor?.let { toDelete ->
                AlertDialog(
                    onDismissRequest = { deleteModelConfirmFor = null },
                    title = { Text(stringResource(R.string.chat_model_delete_confirm_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.chat_model_delete_confirm_body,
                                toDelete.title,
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { deleteGgufModel(toDelete) }) {
                            Text(stringResource(R.string.chat_model_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteModelConfirmFor = null }) {
                            Text(stringResource(R.string.cd_close))
                        }
                    },
                )
            }
            ChatModelPickerSheet(
                visible = modelPickerOpen,
                options = modelOptions,
                selectedId = selectedModelId,
                deviceRamMb = deviceRamMb,
                modelDownload = modelDownloadState,
                onDismiss = { modelPickerOpen = false },
                onSelect = { option ->
                    modelPickerOpen = false
                    scope.launch {
                        ChatModelSelection.applySelection(prefs, option)
                    }
                },
                onDownload = { option -> downloadGgufModel(option) },
                onPauseDownload = { modelDownloadController.pause() },
                onResumeDownload = { option -> resumeModelDownload(option) },
                onCancelDownload = { modelDownloadController.cancel() },
                onDelete = { option -> deleteModelConfirmFor = option },
            )
        }
    }
}

@Composable
private fun DrawerAccountBar(
    accountLabel: String,
    planLabel: String? = null,
    onSettings: () -> Unit,
    onRecordings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = accountLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!planLabel.isNullOrBlank()) {
                    Text(
                        text = planLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onRecordings) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = "Recordings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.cd_open_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun AddToChatSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
) {
    val chrome = LocalEssentialChrome.current
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val tileShape = RoundedCornerShape(16.dp)
    val tileBorder = chrome.tileBorder

    Surface(
        shape = sheetShape,
        color = chrome.sheetSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(chrome.hint.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = chrome.primaryText,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.chat_add_to_chat),
                    color = chrome.primaryText,
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp,
                    ),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AddChatTile(
                    icon = Icons.Outlined.CameraAlt,
                    label = stringResource(R.string.chat_add_camera),
                    onClick = onCamera,
                    borderColor = tileBorder,
                    shape = tileShape,
                    modifier = Modifier.weight(1f),
                )
                AddChatTile(
                    icon = Icons.Outlined.Image,
                    label = stringResource(R.string.chat_add_photos),
                    onClick = onPhotos,
                    borderColor = tileBorder,
                    shape = tileShape,
                    modifier = Modifier.weight(1f),
                )
                AddChatTile(
                    icon = Icons.Outlined.Description,
                    label = stringResource(R.string.chat_add_files),
                    onClick = onFiles,
                    borderColor = tileBorder,
                    shape = tileShape,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AddChatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    borderColor: Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalEssentialChrome.current
    val tap = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .clickable(interactionSource = tap, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = chrome.primaryText,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            color = chrome.hint,
            style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
            maxLines = 1,
        )
    }
}

@Composable
private fun AttachmentMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    @Suppress("UNUSED_PARAMETER") subtitle: String = "",
    onClick: () -> Unit,
) {
    val chrome = LocalEssentialChrome.current
    val tap = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = tap, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = chrome.primaryText,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = chrome.primaryText,
            style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
            modifier = Modifier.weight(1f),
        )
    }
}


@Composable
private fun TypewriterMarkdown(
    messageId: Long,
    target: String,
    streamComplete: Boolean,
    typewriterDoneIds: Set<Long>,
    onTypewriterDone: (Long) -> Unit,
    modifier: Modifier = Modifier,
    baseColor: Color,
    mutedColor: Color,
    enableTypewriter: Boolean = true,
    reader: ReaderTypographySettings = ReaderTypographySettings(),
) {
    val latestTarget by rememberUpdatedState(target)
    val latestStreamComplete by rememberUpdatedState(streamComplete)
    val latestEnableTypewriter by rememberUpdatedState(enableTypewriter)
    val latestDoneIds by rememberUpdatedState(typewriterDoneIds)

    var displayed by remember(messageId) {
        mutableStateOf(
            when {
                messageId in typewriterDoneIds -> target
                !enableTypewriter -> target
                streamComplete -> target
                else -> ""
            },
        )
    }

    DisposableEffect(messageId) {
        onDispose {
            if (target.isNotEmpty()) onTypewriterDone(messageId)
        }
    }

    LaunchedEffect(messageId) {
        while (isActive) {
            val t = latestTarget
            val done = latestStreamComplete
            val enabled = latestEnableTypewriter
            if (messageId in latestDoneIds) {
                displayed = t
                break
            }
            if (!enabled) {
                displayed = t
                onTypewriterDone(messageId)
                break
            }
            if (!done) {

                if (displayed != t) displayed = t
                delay(32L)
                continue
            }
            if (displayed.length < t.length) {
                val gap = t.length - displayed.length
                val step = when {
                    gap > 200 -> 5
                    gap > 80 -> 3
                    else -> 2
                }
                displayed = t.substring(0, (displayed.length + step).coerceAtMost(t.length))
                delay(14L)
                continue
            }
            displayed = t
            onTypewriterDone(messageId)
            break
        }
    }

    MarkdownContent(
        text = displayed,
        modifier = modifier,
        baseColor = baseColor,
        mutedColor = mutedColor,
        reader = reader,
    )
}

@Composable
private fun ThinkingIndicator(reader: ReaderTypographySettings = ReaderTypographySettings()) {
    val chrome = LocalEssentialChrome.current
    val transition = rememberInfiniteTransition(label = "thinking_text")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    ThinkingStatusRow(
        color = chrome.thinkingMuted.copy(alpha = pulseAlpha),
        style = reader.bodyStyle(chrome.thinkingMuted),
        logoSize = 34.dp,
        spacing = 3.dp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}


