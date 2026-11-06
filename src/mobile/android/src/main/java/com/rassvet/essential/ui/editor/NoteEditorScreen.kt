package com.rassvet.essential.ui.editor

import androidx.compose.ui.text.font.FontWeight
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.rassvet.essential.R
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.local.BacklinkRow
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.vault.VaultDocuments
import com.rassvet.essential.ui.markdown.toggleTaskLineAtIndex
import com.rassvet.essential.ui.settings.rememberReaderTypography
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface NoteSaveResult {
    data object Success : NoteSaveResult
    data object Failed : NoteSaveResult
    data object NeedSafFolderPermission : NoteSaveResult
    data class RenameFailed(val reason: String) : NoteSaveResult
}

private val EditorBg = Color(0xFF000000)
private val EditorText = Color(0xFFFFFFFF)
private val EditorMuted = Color(0xFF8E8E93)

@Composable
fun NoteEditorScreen(
    prefs: VaultPreferencesRepository,
    db: AppDatabase,
    indexRepository: IndexRepository,
    noteUri: Uri,
    onAskAi: (title: String, body: String) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit = {},
    onDeleted: () -> Unit = {},
    onOpenNote: (Uri) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultStored by prefs.vaultTreeUri.collectAsState(initial = null)
    var pendingSaveAfterVaultGrant by remember { mutableStateOf(false) }
    var vaultAccessBump by remember { mutableIntStateOf(0) }
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var title by remember { mutableStateOf("") }
    var previewMode by remember { mutableStateOf(true) }
    var noteLoaded by remember { mutableStateOf(false) }
    var loadedSnapshot by remember { mutableStateOf<Pair<String, String>?>(null) }
    var backlinks by remember { mutableStateOf<List<BacklinkRow>>(emptyList()) }
    var noteFile by remember { mutableStateOf<DocumentFile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val defaultTitle = stringResource(R.string.editor_default_title)
    val saveFailed = stringResource(R.string.editor_save_failed)
    val reader = rememberReaderTypography(prefs)
    val titleStyle = reader.titleStyle(EditorText).let {
        it.copy(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )
    }
    val bodyStyle = reader.bodyStyle(EditorText).let {
        it.copy(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )
    }

    val treeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { picked ->
            if (picked == null) {
                pendingSaveAfterVaultGrant = false
                return@rememberLauncherForActivityResult
            }
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(picked, flags) }
            vaultAccessBump++
            scope.launch {
                prefs.setVaultTreeUri(picked.toString())
                Toast.makeText(context, context.getString(R.string.editor_toast_vault_access_saved), Toast.LENGTH_SHORT).show()
                if (pendingSaveAfterVaultGrant) {
                    pendingSaveAfterVaultGrant = false
                    when (val r = saveNoteToVault(context, prefs, noteUri, title, body.text)) {
                        NoteSaveResult.Success -> onBack()
                        NoteSaveResult.NeedSafFolderPermission ->
                            Toast.makeText(context, context.getString(R.string.editor_toast_still_no_access), Toast.LENGTH_LONG).show()
                        NoteSaveResult.Failed ->
                            Toast.makeText(context, saveFailed, Toast.LENGTH_LONG).show()
                        is NoteSaveResult.RenameFailed -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.editor_toast_saved_rename_failed, r.reason),
                                Toast.LENGTH_LONG,
                            ).show()
                            onBack()
                        }
                    }
                }
            }
        }

    LaunchedEffect(vaultStored) {
        val v = vaultStored ?: return@LaunchedEffect
        if (!VaultDocuments.needsSafFolderRegrant(context, v)) return@LaunchedEffect
        Toast.makeText(context, context.getString(R.string.editor_toast_open_system_folder_picker), Toast.LENGTH_LONG).show()
        treeLauncher.launch(null)
    }

    fun lookupTitleForBacklinks(): String =
        title.ifBlank {
            body.text.lineSequence()
                .firstOrNull { it.trim().isNotEmpty() }
                ?.trim()
                ?.removePrefix("#")
                ?.trim()
                ?: defaultTitle
        }

    suspend fun refreshBacklinks() {
        if (vaultStored == null) return
        val lookupTitle = lookupTitleForBacklinks()
        backlinks = withContext(Dispatchers.IO) {
            indexRepository.backlinksForTitle(lookupTitle)
        }
    }

    LaunchedEffect(noteUri, vaultAccessBump, noteLoaded) {
        if (!noteLoaded) return@LaunchedEffect
        refreshBacklinks()
    }

    LaunchedEffect(title, noteLoaded) {
        if (!noteLoaded) return@LaunchedEffect
        delay(400)
        refreshBacklinks()
    }

    LaunchedEffect(noteUri, vaultAccessBump) {
        noteLoaded = false
        loadedSnapshot = null
        noteFile = null
        val vault = prefs.vaultTreeUri.first()
        val file = VaultDocuments.singleDocumentUnderVault(context, vault, noteUri)
        noteFile = file
        if (file != null) {
            val raw = VaultDocuments.displayName(file)
                .removeSuffix(".md").removeSuffix(".MD")
            title = if (raw.startsWith("draft-")) "" else raw
            val text = runCatching { VaultDocuments.readText(context, file, vault) }.getOrDefault("")
            body = TextFieldValue(text)
            loadedSnapshot = title to text
            noteLoaded = true
        }
    }

    fun saveAndExit() {
        scope.launch {
            if (!noteLoaded) {
                onBack()
                return@launch
            }
            val vault = prefs.vaultTreeUri.first()
            if (VaultDocuments.needsSafFolderRegrant(context, vault)) {
                Toast.makeText(context, context.getString(R.string.editor_toast_grant_folder_on_save), Toast.LENGTH_LONG).show()
                pendingSaveAfterVaultGrant = true
                treeLauncher.launch(null)
                return@launch
            }
            val titleSnap = title
            val bodySnap = body.text
            val uriSnap = noteUri
            val unchanged = loadedSnapshot?.let { (t, b) ->
                t == titleSnap && b == bodySnap
            } == true
            if (unchanged) {
                onBack()
                return@launch
            }
            if (bodySnap.isBlank() && titleSnap.isBlank()) {
                onBack()
                return@launch
            }
            onBack()
            val activity = context as? ComponentActivity ?: return@launch
            activity.lifecycleScope.launch {
                when (val r = saveNoteToVault(context, prefs, uriSnap, titleSnap, bodySnap)) {
                    NoteSaveResult.Success -> onSaved()
                    NoteSaveResult.NeedSafFolderPermission ->
                        Toast.makeText(context, context.getString(R.string.editor_toast_still_no_access), Toast.LENGTH_LONG).show()
                    NoteSaveResult.Failed ->
                        Toast.makeText(context, saveFailed, Toast.LENGTH_LONG).show()
                    is NoteSaveResult.RenameFailed -> {
                        onSaved()
                        Toast.makeText(
                            context,
                            context.getString(R.string.editor_toast_saved_rename_failed, r.reason),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    BackHandler { saveAndExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBg)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { saveAndExit() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = EditorText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { previewMode = !previewMode }) {
                Icon(
                    imageVector = if (previewMode) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (previewMode) R.string.editor_edit else R.string.editor_preview,
                    ),
                    tint = EditorMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.editor_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }


        CenteredTitleField(
            value = title,
            onValueChange = { title = it },
            placeholder = defaultTitle,
            textStyle = titleStyle,
            textColor = EditorText,
            placeholderColor = EditorMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 16.dp)
                .heightIn(min = 36.dp, max = 56.dp),
        )


        AnimatedContent(
            targetState = previewMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "editorMode",
        ) { isPreview ->
            if (isPreview) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (backlinks.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.editor_backlinks_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = EditorMuted,
                            )
                            backlinks.forEach { bl ->
                                val tap = remember(bl.uri) { MutableInteractionSource() }
                                Text(
                                    text = "← ${bl.title}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable(
                                            interactionSource = tap,
                                            indication = null,
                                            onClick = { onOpenNote(Uri.parse(bl.uri)) },
                                        ),
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        MarkdownView(
                            text = body.text,
                            baseColor = EditorText,
                            mutedColor = EditorMuted,
                            reader = reader,
                            onTaskToggle = { lineIndex ->
                                body = body.copy(text = toggleTaskLineAtIndex(body.text, lineIndex))
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                        )
                    }
                }
            } else {
                BasicTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    textStyle = bodyStyle,
                    cursorBrush = SolidColor(EditorText),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (body.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.editor_placeholder),
                                    style = bodyStyle.copy(color = EditorMuted),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }


        AnimatedVisibility(
            visible = !previewMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 3.dp,
                    tonalElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FormatBtn(icon = Icons.Rounded.FormatBold, cd = stringResource(R.string.editor_format_bold)) {
                            body = insertAround(body, "**", "**")
                        }
                        FormatBtn(icon = Icons.Rounded.FormatItalic, cd = stringResource(R.string.editor_format_italic)) {
                            body = insertAround(body, "*", "*")
                        }
                        VerticalDivider(
                            modifier = Modifier
                                .height(20.dp)
                                .padding(horizontal = 2.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        FormatBtn(icon = Icons.Rounded.Title, cd = stringResource(R.string.editor_format_heading)) {
                            body = insertAtLineStart(body, "# ")
                        }
                        FormatBtn(icon = Icons.Rounded.Code, cd = stringResource(R.string.editor_format_code)) {
                            body = insertAround(body, "`", "`")
                        }
                        FormatBtn(icon = Icons.Rounded.FormatListBulleted, cd = stringResource(R.string.editor_format_list)) {
                            body = insertAtLineStart(body, "- ")
                        }
                        FormatBtn(icon = Icons.Rounded.CheckBox, cd = stringResource(R.string.editor_format_task)) {
                            body = insertAtLineStart(body, "- [ ] ")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val deleteLabel = title.trim().ifBlank {
            noteFile?.let { f ->
                VaultDocuments.displayName(f)
                    .removeSuffix(".md")
                    .removeSuffix(".MD")
            }?.takeIf { !it.startsWith("draft-") }
        } ?: defaultTitle
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.editor_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.editor_delete_confirm_body, deleteLabel))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            val ok = deleteNoteFromVault(
                                context = context,
                                prefs = prefs,
                                noteUri = noteUri,
                                noteFile = noteFile,
                            )
                            if (ok) {
                                onDeleted()
                                onBack()
                            } else if (
                                vaultStored?.let { VaultDocuments.needsSafFolderRegrant(context, it) } == true
                            ) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.editor_toast_grant_folder_on_save),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.editor_delete_failed),
                                    Toast.LENGTH_LONG,
                                ).show()
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
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.editor_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun CenteredTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    textColor: Color,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
) {
    val onValueChangeState = androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val fontFamily = textStyle.fontFamily ?: FontFamily.Default
    val fontWeight = textStyle.fontWeight ?: FontWeight.Normal
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxFontSp = textStyle.fontSize.value
    val minFontSp = 11f

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val maxHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val measureText = value.ifBlank { placeholder }
        val fittedSp = remember(measureText, maxWidthPx, maxHeightPx, maxFontSp, textStyle) {
            fitTitleFontSizeSp(
                measurer = textMeasurer,
                text = measureText,
                style = textStyle,
                maxWidthPx = maxWidthPx,
                maxHeightPx = maxHeightPx,
                minSp = minFontSp,
                maxSp = maxFontSp,
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                EditText(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
                    maxLines = 2
                    isSingleLine = false
                    setHorizontallyScrolling(false)
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    imeOptions = EditorInfo.IME_ACTION_NEXT
                    setPadding(0, 0, 0, 0)
                    includeFontPadding = false
                    setTextColor(textColor.toArgb())
                    setHintTextColor(placeholderColor.toArgb())
                    hint = placeholder
                    typeface = fontFamily.toAndroidTypeface(fontWeight)
                    addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                            override fun afterTextChanged(s: Editable?) {
                                onValueChangeState.value(s?.toString() ?: "")
                            }
                        },
                    )
                }
            },
            update = { editText ->
                editText.setTextColor(textColor.toArgb())
                editText.setHintTextColor(placeholderColor.toArgb())
                editText.hint = placeholder
                editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fittedSp)
                editText.typeface = fontFamily.toAndroidTypeface(fontWeight)
                val current = editText.text?.toString() ?: ""
                if (current != value) {
                    editText.setText(value)
                    editText.setSelection(editText.text?.length ?: 0)
                }
            },
        )
    }
}

private fun fitTitleFontSizeSp(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    maxHeightPx: Int,
    minSp: Float,
    maxSp: Float,
): Float {
    if (text.isBlank() || maxWidthPx <= 0) return maxSp
    var low = minSp
    var high = maxSp
    var best = minSp
    repeat(14) {
        val mid = (low + high) / 2f
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = style.copy(
                fontSize = mid.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
            overflow = TextOverflow.Clip,
            constraints = Constraints(maxWidth = maxWidthPx),
        )
        val fits = layout.size.width <= maxWidthPx && layout.size.height <= maxHeightPx
        if (fits) {
            best = mid
            low = mid + 0.25f
        } else {
            high = mid - 0.25f
        }
    }
    return best.coerceIn(minSp, maxSp)
}

private fun FontFamily.toAndroidTypeface(weight: FontWeight): Typeface {
    val style = when (weight) {
        FontWeight.Light, FontWeight.ExtraLight, FontWeight.Thin -> Typeface.NORMAL
        FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black -> Typeface.BOLD
        FontWeight.SemiBold, FontWeight.Medium -> Typeface.BOLD
        else -> Typeface.NORMAL
    }
    val base = when (this) {
        FontFamily.Serif -> Typeface.SERIF
        FontFamily.SansSerif -> Typeface.SANS_SERIF
        FontFamily.Monospace -> Typeface.MONOSPACE
        FontFamily.Cursive -> Typeface.SERIF
        else -> Typeface.DEFAULT
    }
    return Typeface.create(base, style)
}

@Composable
private fun FormatBtn(icon: ImageVector, cd: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = cd,
            modifier = Modifier.size(20.dp),
        )
    }
}


private fun insertAround(tfv: TextFieldValue, prefix: String, suffix: String): TextFieldValue {
    val sel = tfv.selection
    val text = tfv.text
    val newText = text.substring(0, sel.start) + prefix + text.substring(sel.start, sel.end) + suffix + text.substring(sel.end)
    val cursor = if (sel.collapsed) sel.start + prefix.length else sel.end + prefix.length + suffix.length
    return TextFieldValue(newText, TextRange(cursor))
}

private fun insertAtLineStart(tfv: TextFieldValue, prefix: String): TextFieldValue {
    val text = tfv.text
    val pos = tfv.selection.start
    val lineStart = (text.lastIndexOf('\n', pos - 1) + 1).coerceAtLeast(0)
    return TextFieldValue(
        text.substring(0, lineStart) + prefix + text.substring(lineStart),
        TextRange(pos + prefix.length),
    )
}


private suspend fun deleteNoteFromVault(
    context: Context,
    prefs: VaultPreferencesRepository,
    noteUri: Uri,
    noteFile: DocumentFile?,
): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val vaultStored = prefs.vaultTreeUri.first()
            if (vaultStored.isNullOrBlank()) return@withContext false
            if (VaultDocuments.needsSafFolderRegrant(context, vaultStored)) {
                return@withContext false
            }
            val targetUri = noteFile?.uri ?: noteUri
            VaultDocuments.deleteDocumentFromVault(context, vaultStored, targetUri)
        } catch (_: Exception) {
            false
        }
    }


private suspend fun saveNoteToVault(
    context: Context,
    prefs: VaultPreferencesRepository,
    noteUri: Uri,
    title: String,
    body: String,
): NoteSaveResult =
    withContext(Dispatchers.IO) {
        try {
            val vaultStored = prefs.vaultTreeUri.first()
            if (vaultStored.isNullOrBlank()) {
                return@withContext NoteSaveResult.Failed
            }
            if (VaultDocuments.needsSafFolderRegrant(context, vaultStored)) {
                return@withContext NoteSaveResult.NeedSafFolderPermission
            }

            var writableUri = VaultDocuments.writableUriForVaultDocument(context, vaultStored, noteUri)


            if (!VaultDocuments.writeDocumentText(context, vaultStored, writableUri, body)) {
                return@withContext NoteSaveResult.Failed
            }

            val currentName = VaultDocuments.resolveDocumentDisplayName(context, vaultStored, noteUri)
            val isDraft = currentName.startsWith("draft-")
            val sanitizedTitle = title.trim()
            val targetBase = when {
                sanitizedTitle.isNotEmpty() -> sanitizedTitle
                isDraft -> {
                    val ts = java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH-mm", java.util.Locale.US,
                    ).format(java.util.Date())
                    "${context.getString(R.string.note_untitled)} $ts"
                }
                else -> null
            }
            var renameError: String? = null
            if (targetBase != null) {
                val rename = VaultDocuments.renameMarkdownFile(context, vaultStored, noteUri, targetBase)
                if (!rename.ok) {
                    renameError = rename.error
                }
            }

            if (renameError != null) NoteSaveResult.RenameFailed(renameError) else NoteSaveResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            NoteSaveResult.NeedSafFolderPermission
        } catch (_: Exception) {
            NoteSaveResult.Failed
        }
    }


