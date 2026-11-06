package com.rassvet.essential.ui.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import com.rassvet.essential.data.chat.extractNoteMarkdownFromReply
import com.rassvet.essential.data.chat.normalizeNoteMarkdown
import com.rassvet.essential.data.chat.prepareNoteFromAiReply
import com.rassvet.essential.data.vault.VaultDocuments
import com.rassvet.essential.ui.PendingNoteContext
import com.rassvet.essential.ui.theme.EssentialBrand
import com.rassvet.essential.ui.theme.LocalEssentialChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SuggestionShape = RoundedCornerShape(16.dp)

@Composable
private fun ChatSuggestionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val chrome = LocalEssentialChrome.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(SuggestionShape)
            .background(chrome.sheetSurface)
            .border(1.dp, chrome.sheetBorder, SuggestionShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
private fun ChatSuggestionAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalEssentialChrome.current
    Text(
        text = label,
        color = chrome.sheetText,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}


data class NoteCreateSuggestion(
    val title: String,
    val markdown: String,
)


data class NoteEditSuggestion(
    val noteUri: Uri,
    val noteTitle: String,
    val suggestedMarkdown: String,
)

enum class NotePatchMode {
    Replace,
    Append,
}

fun looksLikeNoteEditRequest(query: String): Boolean {
    val q = query.lowercase()
    if (looksLikeNoteCreateRequest(query)) return false
    return listOf(
        "распиш", "дополни", "измени", "перепиш", "отредактир",
        "в заметк", "обнови", "заполни", "расшир", "структурир", "оформи",
        "улучши", "исправ", "впиш",
    ).any { q.contains(it) }
}

fun looksLikeNoteCreateRequest(query: String): Boolean {
    val q = query.lowercase()
    return listOf(
        "новую заметк", "новая заметк", "новой заметк",
        "создай замет", "создать замет", "сделай замет",
        "добавь замет", "добавить замет",
        "добавь как замет", "добавить как замет",
        "сохрани как замет", "сохранить как замет",
        "запиши как замет", "запиши в замет",
        "сохрани в хранилищ", "добавь в хранилищ", "в хранилищ",
        "как заметку", "как новую замет",
    ).any { q.contains(it) }
}

fun buildNoteCreateSuggestion(query: String, reply: String): NoteCreateSuggestion? {
    val explicit = looksLikeNoteCreateRequest(query)
    val hasNoteFence = Regex("```(?:note|markdown|md)", RegexOption.IGNORE_CASE).containsMatchIn(reply)
    if (!explicit && !hasNoteFence) return null
    val prepared = prepareNoteFromAiReply(reply) ?: return null
    return NoteCreateSuggestion(title = prepared.first, markdown = prepared.second)
}

fun buildNoteEditSuggestion(
    query: String,
    reply: String,
    noteCtx: PendingNoteContext,
): NoteEditSuggestion? {
    val uri = noteCtx.uri ?: return null
    val extracted = extractNoteMarkdownFromReply(reply)
    val markdown = when {
        extracted != null -> normalizeNoteMarkdown(extracted)
        looksLikeNoteEditRequest(query) && reply.trim().length >= 40 ->
            normalizeNoteMarkdown(reply.trim())
        else -> return null
    }
    return NoteEditSuggestion(
        noteUri = uri,
        noteTitle = noteCtx.title.ifBlank { "Без названия" },
        suggestedMarkdown = markdown,
    )
}

suspend fun applyNoteSourcePatch(
    context: Context,
    vaultStored: String?,
    uri: Uri,
    originalBody: String,
    suggestedMarkdown: String,
    mode: NotePatchMode,
): Boolean = withContext(Dispatchers.IO) {
    if (vaultStored.isNullOrBlank()) return@withContext false
    val newBody = when (mode) {
        NotePatchMode.Replace -> suggestedMarkdown.trim()
        NotePatchMode.Append -> {
            val base = originalBody.trimEnd()
            if (base.isEmpty()) suggestedMarkdown.trim()
            else "$base\n\n${suggestedMarkdown.trim()}"
        }
    }
    VaultDocuments.writeDocumentText(context, vaultStored, uri, newBody)
}

@Composable
fun ChatSourcesBar(
    activeNote: PendingNoteContext?,
    onPickNote: () -> Unit,
    onClearNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    if (activeNote == null) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = barBg,
            modifier = modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPickNote,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.note_sources_short),
                    color = onSurface,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.1).sp,
                    ),
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = barBg,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPickNote,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.note_sources_short),
                    color = onSurface,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = activeNote.title.ifBlank { stringResource(R.string.note_untitled) },
                    color = onSurface,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                IconButton(
                    onClick = onClearNote,
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_remove_note),
                        tint = muted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun BoxScope.NoteSourcePickerSheet(
    visible: Boolean,
    tiles: List<NoteVaultTile>,
    onDismiss: () -> Unit,
    onSelect: (NoteVaultTile) -> Unit,
) {
    val chrome = LocalEssentialChrome.current
    BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(chrome.overlayScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(250)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
            val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            Surface(
                shape = sheetShape,
                color = chrome.sheetSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 6.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .background(chrome.sheetMuted.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.CenterStart),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = chrome.sheetText,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.chat_pick_note),
                            color = chrome.sheetText,
                            style = TextStyle(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 22.sp,
                            ),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (tiles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_pick_note_empty),
                            color = chrome.sheetMuted,
                            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                        ) {
                            items(tiles, key = { it.uri.toString() }) { tile ->
                                val tap = remember(tile.uri) { MutableInteractionSource() }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(interactionSource = tap, indication = null) {
                                            onSelect(tile)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = chrome.sheetIcon,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tile.title,
                                            color = chrome.sheetText,
                                            style = TextStyle(
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal,
                                                lineHeight = 22.sp,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!tile.summary.isNullOrBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            HorizontalDivider(
                                                color = chrome.sheetDivider.copy(alpha = 0.5f),
                                                thickness = 0.5.dp,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = tile.summary,
                                                color = chrome.sheetMuted,
                                                style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = chrome.sheetIcon,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                HorizontalDivider(
                                    color = chrome.sheetDivider.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                }
            }
        }
    }
}

@Composable
fun NoteCreateSuggestionCard(
    suggestion: NoteCreateSuggestion,
    assistantText: Color,
    mutedColor: Color,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = suggestion.title,
            color = mutedColor,
            style = TextStyle(fontSize = 13.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onCreate,
            modifier = Modifier.padding(horizontal = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.suggestion_create_note_action),
                color = EssentialBrand,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_hide),
                tint = mutedColor.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun NoteEditSuggestionCard(
    suggestion: NoteEditSuggestion,
    assistantText: Color,
    mutedColor: Color,
    onApplyReplace: () -> Unit,
    onApplyAppend: () -> Unit,
    onOpenEditor: () -> Unit,
    onDismiss: () -> Unit,
) {
    val chrome = LocalEssentialChrome.current
    ChatSuggestionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.suggestion_changes_for, suggestion.noteTitle),
                color = assistantText,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.cd_hide),
                    tint = chrome.sheetIcon,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = suggestionPreview(suggestion.suggestedMarkdown),
            color = mutedColor,
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
            maxLines = 3,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChatSuggestionAction(label = stringResource(R.string.action_replace), onClick = onApplyReplace)
            ChatSuggestionAction(label = stringResource(R.string.action_append_to_end), onClick = onApplyAppend)
            ChatSuggestionAction(label = stringResource(R.string.action_open), onClick = onOpenEditor)
        }
    }
}

private fun suggestionPreview(markdown: String): String =
    markdown.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("```") }
        .take(3)
        .joinToString("\n")
        .let { if (it.length > 180) it.take(177) + "…" else it }


