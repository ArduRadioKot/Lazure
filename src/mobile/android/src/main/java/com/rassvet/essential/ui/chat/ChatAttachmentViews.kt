package com.rassvet.essential.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
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
import com.rassvet.essential.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rassvet.essential.data.chat.ChatAttachment
import com.rassvet.essential.data.chat.PersistedAttachment
import com.rassvet.essential.data.chat.decodeAttachmentThumbnail
import com.rassvet.essential.ui.theme.LocalEssentialChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MessageThumbWidth = 96.dp
private val MessageThumbCorner = 12.dp
private val InputThumbSize = 56.dp

@Composable
private fun rememberAttachmentThumbnail(
    base64: String?,
    maxSidePx: Int = 384,
): ImageBitmap? {
    var bitmap by remember(base64) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(base64) {
        bitmap = null
        if (base64.isNullOrBlank()) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            decodeAttachmentThumbnail(base64, maxSidePx)?.asImageBitmap()
        }
    }
    return bitmap
}

@Composable
fun ChatUserMessageContent(
    text: String,
    attachments: List<PersistedAttachment>,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalEssentialChrome.current
    val images = attachments.filter { it.isImage }
    val files = attachments.filter { !it.isImage }
    val hasMedia = attachments.isNotEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        images.forEachIndexed { index, attachment ->
            ChatMessageImageThumbnail(
                attachment = attachment,
                modifier = Modifier.padding(
                    bottom = if (index < images.lastIndex || files.isNotEmpty()) 6.dp else 0.dp,
                ),
            )
        }
        files.forEach { attachment ->
            ChatAttachmentChip(
                attachment = attachment,
                surfaceColor = chrome.attachmentSurface,
                textColor = chrome.primaryText,
                iconTint = chrome.attachmentMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (text.isNotBlank()) {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (hasMedia) 6.dp else 0.dp),
                style = textStyle.copy(textAlign = TextAlign.End),
            )
        }
    }
}

@Composable
fun ChatMessageImageThumbnail(
    attachment: PersistedAttachment,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalEssentialChrome.current
    val bitmap = rememberAttachmentThumbnail(attachment.base64, maxSidePx = 320)
    val shape = RoundedCornerShape(MessageThumbCorner)
    val aspectRatio = remember(bitmap) {
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            (bitmap.width.toFloat() / bitmap.height).coerceIn(0.65f, 1.5f)
        } else {
            1f
        }
    }

    Box(
        modifier = modifier
            .width(MessageThumbWidth)
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(chrome.attachmentSurface),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            attachment.base64.isNullOrBlank() -> {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = chrome.attachmentMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = chrome.attachmentMuted,
                )
            }
        }
    }
}

@Composable
fun ChatInputImagePreview(
    attachment: ChatAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalEssentialChrome.current
    val bitmap = rememberAttachmentThumbnail(attachment.base64, maxSidePx = 256)
    Box(
        modifier = modifier.size(InputThumbSize),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(chrome.attachmentSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = attachment.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = chrome.attachmentMuted,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .background(chrome.inputSendBg, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_delete),
                tint = chrome.inputSendIcon,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
fun ChatInputFileChip(
    attachment: ChatAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalEssentialChrome.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = chrome.attachmentSurface,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (attachment.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = chrome.sheetIcon,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = attachment.displayName,
                color = chrome.primaryText,
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(120.dp),
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = chrome.attachmentMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
fun ChatAttachmentChip(
    attachment: PersistedAttachment,
    modifier: Modifier = Modifier,
    surfaceColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor,
        modifier = modifier.padding(bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (attachment.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = attachment.displayName,
                color = textColor,
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 1,
            )
        }
    }
}


