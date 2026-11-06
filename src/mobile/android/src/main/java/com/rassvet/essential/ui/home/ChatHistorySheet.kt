package com.rassvet.essential.ui.home

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.rassvet.essential.locale.formatRelativeTime
import com.rassvet.essential.R
import com.rassvet.essential.ui.theme.EssentialBrand
import com.rassvet.essential.ui.theme.LocalEssentialChrome

@Composable
fun BoxScope.ChatHistorySheet(
    visible: Boolean,
    sessions: List<ChatSessionUi>,
    currentSessionId: Long,
    onDismiss: () -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onSaveAsNote: (ChatSessionUi) -> Unit,
    onDeleteSession: (ChatSessionUi) -> Unit,
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
        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(150)),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        val sheetShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        Surface(
            shape = sheetShape,
            color = chrome.sheetSurface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 12.dp),
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
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = chrome.sheetText,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.chat_history_title),
                        color = chrome.sheetText,
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp,
                        ),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    val newTap = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(chrome.sheetSelectedBg)
                            .clickable(interactionSource = newTap, indication = null, onClick = onNewChat)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.chat_history_new),
                            tint = chrome.sheetText,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.chat_history_new),
                            color = chrome.sheetText,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }

                if (sessions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_history_empty),
                        color = chrome.sheetMuted,
                        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            ChatHistoryRow(
                                session = session,
                                selected = session.id == currentSessionId,
                                onClick = { onSelectSession(session.id) },
                                onSaveAsNote = { onSaveAsNote(session) },
                                onDelete = { onDeleteSession(session) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryRow(
    session: ChatSessionUi,
    selected: Boolean,
    onClick: () -> Unit,
    onSaveAsNote: () -> Unit,
    onDelete: () -> Unit,
) {
    val chrome = LocalEssentialChrome.current
    val shape = RoundedCornerShape(14.dp)
    val tap = remember(session.id) { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) chrome.sheetSelectedBg else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) EssentialBrand.copy(alpha = 0.5f) else chrome.sheetBorder,
                shape = shape,
            )
            .clickable(interactionSource = tap, indication = null, onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                color = if (selected) chrome.sheetText else chrome.sheetText.copy(alpha = 0.92f),
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatRelativeTime(LocalContext.current, session.lastUpdatedAt),
                color = chrome.sheetMuted,
                style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val saveTap = remember(session.id) { MutableInteractionSource() }
            Icon(
                imageVector = Icons.Outlined.NoteAdd,
                contentDescription = stringResource(R.string.chat_history_save_note),
                tint = chrome.sheetMuted,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = saveTap, indication = null, onClick = onSaveAsNote)
                    .padding(7.dp),
            )
            val delTap = remember(session.id) { MutableInteractionSource() }
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.chat_history_delete),
                tint = chrome.sheetMuted,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = delTap, indication = null, onClick = onDelete)
                    .padding(7.dp),
            )
        }
    }
}


