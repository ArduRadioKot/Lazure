@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
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
import com.rassvet.essential.data.llm.ChatModelSelection
import com.rassvet.essential.data.llm.LocalModelCatalog
import com.rassvet.essential.data.llm.LocalModelDownloadState
import com.rassvet.essential.ui.theme.LocalEssentialChrome

@Composable
fun ChatModelPickerPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    speedLabel: String? = null,
) {
    val chrome = LocalEssentialChrome.current
    val shape = RoundedCornerShape(999.dp)
    val tap = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(shape)
            .background(chrome.sheetContainer)
            .border(1.dp, chrome.sheetBorder, shape)
            .clickable(interactionSource = tap, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = chrome.sheetText,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!speedLabel.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = speedLabel,
                color = chrome.sheetMuted,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun BoxScope.ChatModelPickerSheet(
    visible: Boolean,
    options: List<ChatModelSelection.Option>,
    selectedId: String,
    deviceRamMb: Long,
    modelDownload: LocalModelDownloadState,
    onDismiss: () -> Unit,
    onSelect: (ChatModelSelection.Option) -> Unit,
    onDownload: (ChatModelSelection.Option) -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: (ChatModelSelection.Option) -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: (ChatModelSelection.Option) -> Unit,
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
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = chrome.sheetText,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.chat_pick_model),
                        color = chrome.sheetText,
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp,
                        ),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                Text(
                    text = stringResource(R.string.chat_pick_model_device_hint, deviceRamMb),
                    color = chrome.sheetMuted,
                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val cloud = options.filter { it.kind != ChatModelSelection.Kind.LocalModel }
                    val local = options.filter { it.kind == ChatModelSelection.Kind.LocalModel }

                    if (cloud.isNotEmpty()) {
                        ModelSectionLabel(
                            text = stringResource(R.string.chat_models_cloud),
                            icon = Icons.Outlined.Cloud,
                        )
                        cloud.forEach { option ->
                            CloudModelOptionRow(
                                option = option,
                                selected = option.id == selectedId,
                                onClick = { onSelect(option) },
                            )
                        }
                    }

                    if (local.isNotEmpty()) {
                        if (cloud.isNotEmpty()) {
                            HorizontalDivider(
                                color = chrome.sheetBorder,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        ModelSectionLabel(
                            text = stringResource(R.string.chat_models_local),
                            icon = Icons.Outlined.Memory,
                        )
                        local.forEach { option ->
                            val fileName = option.localFileName
                            val isActiveDownload =
                                fileName != null &&
                                    modelDownload.fileName == fileName &&
                                    (
                                        modelDownload.status == LocalModelDownloadState.Status.Downloading ||
                                            modelDownload.status == LocalModelDownloadState.Status.Paused
                                        )
                            val isPaused =
                                isActiveDownload &&
                                    modelDownload.status == LocalModelDownloadState.Status.Paused
                            val preset = ChatModelSelection.presetForOption(option)
                            LocalModelCard(
                                option = option,
                                selected = option.id == selectedId,
                                isActiveDownload = isActiveDownload,
                                isPaused = isPaused,
                                downloadProgress = if (isActiveDownload) modelDownload.progress else null,
                                bytesDownloaded = if (isActiveDownload) modelDownload.bytesDownloaded else 0L,
                                totalBytes = modelDownload.totalBytes ?: preset?.expectedBytes,
                                onClick = { if (option.available) onSelect(option) },
                                onDownload =
                                    if (option.isDownloadable && !isActiveDownload) {
                                        { onDownload(option) }
                                    } else {
                                        null
                                    },
                                onResume =
                                    if (isPaused) {
                                        { onResumeDownload(option) }
                                    } else {
                                        null
                                    },
                                onPause = if (isActiveDownload && !isPaused) onPauseDownload else null,
                                onCancelDownload = if (isActiveDownload) onCancelDownload else null,
                                onDelete =
                                    if (option.available && !isActiveDownload && fileName != null) {
                                        { onDelete(option) }
                                    } else {
                                        null
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSectionLabel(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val chrome = LocalEssentialChrome.current
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = chrome.sheetIcon, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = chrome.sheetMuted,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun CloudModelOptionRow(
    option: ChatModelSelection.Option,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ModelOptionShell(selected = selected, onClick = onClick, enabled = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    color = LocalEssentialChrome.current.sheetText,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                option.subtitle?.let { sub ->
                    Text(
                        text = sub,
                        color = LocalEssentialChrome.current.sheetMuted,
                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalModelCard(
    option: ChatModelSelection.Option,
    selected: Boolean,
    isActiveDownload: Boolean,
    isPaused: Boolean,
    downloadProgress: Float?,
    bytesDownloaded: Long,
    totalBytes: Long?,
    onClick: () -> Unit,
    onDownload: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onPause: (() -> Unit)?,
    onCancelDownload: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val chrome = LocalEssentialChrome.current
    val colorScheme = MaterialTheme.colorScheme
    ModelOptionShell(
        selected = selected,
        onClick = onClick,
        enabled = option.available && !isActiveDownload,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.title,
                        color = chrome.sheetText,
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (option.recommended) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.chat_model_recommended),
                            color = colorScheme.primary,
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        tint = chrome.sheetMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = option.sizeLabel ?: option.subtitle.orEmpty(),
                        color = chrome.sheetMuted,
                        style = TextStyle(fontSize = 12.sp),
                    )
                }

                option.description?.let { desc ->
                    Text(
                        text = desc,
                        color = chrome.sheetMuted,
                        style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (!isActiveDownload) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.chat_model_delete),
                                tint = chrome.sheetMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    when {
                        selected -> {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        onDownload != null -> {
                            DownloadActionChip(
                                label = stringResource(R.string.chat_model_download),
                                onClick = onDownload,
                            )
                        }
                        onResume != null -> {
                            DownloadActionChip(
                                label = stringResource(R.string.chat_model_download_resume),
                                onClick = onResume,
                            )
                        }
                    }
                }
            }
        }

        if (isActiveDownload) {
            val progress = downloadProgress?.coerceIn(0f, 1f)
            val downloadedLabel =
                when {
                    isPaused ->
                        stringResource(R.string.chat_model_download_paused)
                    progress != null && totalBytes != null && totalBytes > 0L ->
                        stringResource(
                            R.string.chat_model_download_progress,
                            LocalModelCatalog.formatDownloadSize(bytesDownloaded),
                            LocalModelCatalog.formatDownloadSize(totalBytes),
                        )
                    bytesDownloaded > 0L ->
                        LocalModelCatalog.formatDownloadSize(bytesDownloaded)
                    else -> stringResource(R.string.chat_model_downloading)
                }
            Text(
                text = downloadedLabel,
                color = chrome.sheetMuted,
                style = TextStyle(fontSize = 12.sp),
                modifier = Modifier.padding(top = 12.dp),
            )
            ModelDownloadWavyProgress(
                progress = if (isPaused) progress else downloadProgress,
                indeterminate = !isPaused && progress == null,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onPause != null) {
                    DownloadControlChip(
                        label = stringResource(R.string.chat_model_download_pause),
                        icon = Icons.Outlined.Pause,
                        onClick = onPause,
                    )
                }
                if (onResume != null) {
                    DownloadControlChip(
                        label = stringResource(R.string.chat_model_download_resume),
                        icon = Icons.Outlined.PlayArrow,
                        onClick = onResume,
                    )
                }
                if (onCancelDownload != null) {
                    DownloadControlChip(
                        label = stringResource(R.string.chat_model_download_cancel),
                        icon = Icons.Outlined.Close,
                        onClick = onCancelDownload,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadActionChip(
    label: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dlShape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(dlShape)
            .background(colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Download,
            contentDescription = null,
            tint = colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = colorScheme.onPrimary,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun DownloadControlChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val chrome = LocalEssentialChrome.current
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, chrome.sheetBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = chrome.sheetText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = chrome.sheetText,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun ModelOptionShell(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val chrome = LocalEssentialChrome.current
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val tap = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    chrome.sheetContainer
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) colorScheme.primary else chrome.sheetBorder,
                shape = shape,
            )
            .clickable(
                interactionSource = tap,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        content()
    }
}

@Composable
private fun ModelDownloadWavyProgress(
    progress: Float?,
    indeterminate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val wavyShape = RoundedCornerShape(percent = 50)
    val wavyModifier =
        modifier
            .fillMaxWidth()
            .height(WavyProgressIndicatorDefaults.LinearContainerHeight)
            .clip(wavyShape)

    if (!indeterminate && progress != null && progress >= 0f) {
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = wavyModifier,
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            stopSize = 0.dp,
            amplitude = { 1f },
        )
    } else {
        LinearWavyProgressIndicator(
            modifier = wavyModifier,
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            amplitude = 1f,
        )
    }
}


