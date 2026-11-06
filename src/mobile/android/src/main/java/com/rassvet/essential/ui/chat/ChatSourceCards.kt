@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.rassvet.essential.ui.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.R
import com.rassvet.essential.data.chat.ChatSourceNote

@Composable
fun ChatSourceCardsRow(
    sources: List<ChatSourceNote>,
    visible: Boolean,
    onOpenNote: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && sources.isNotEmpty(),
        modifier = modifier,
        enter =
            fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideInHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) { it / 4 },
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            item(key = "sources-label") {
                Text(
                    text = stringResource(R.string.chat_sources_label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
            items(items = sources, key = { it.uri }) { source ->
                ChatSourceMiniCard(
                    source = source,
                    onClick = { onOpenNote(Uri.parse(source.uri)) },
                )
            }
        }
    }
}

@Composable
private fun ChatSourceMiniCard(
    source: ChatSourceNote,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector =
                    if (source.kind == ChatSourceNote.Kind.PINNED) {
                        Icons.Outlined.PushPin
                    } else {
                        Icons.Outlined.Description
                    },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = source.title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 104.dp),
            )
        }
    }
}

@Composable
fun NoteCreatedChip(
    title: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(top = 8.dp),
        enter =
            scaleIn(
                initialScale = 0.86f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            ) + fadeIn(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text =
                    if (title.isNullOrBlank()) {
                        stringResource(R.string.chat_note_created_chip)
                    } else {
                        stringResource(R.string.chat_note_created_chip_named, title)
                    },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}


