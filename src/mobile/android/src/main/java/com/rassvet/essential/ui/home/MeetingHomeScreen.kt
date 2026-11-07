package com.rassvet.essential.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import com.rassvet.essential.ui.theme.LazureIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rassvet.essential.R
import com.rassvet.essential.data.local.RecordingEntity
import java.text.DateFormat
import java.util.Date

data class HomeAction(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
    val primary: Boolean = false,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MeetingHomeScreen(
    recent: List<RecordingEntity>,
    onLive: () -> Unit,
    onImport: () -> Unit,
    onModels: () -> Unit,
    onVault: () -> Unit,
    onSearch: () -> Unit,
    onOpenRecording: (String) -> Unit,
) {
    val actions = listOf(
        HomeAction(stringResource(R.string.home_action_live), stringResource(R.string.home_action_live_hint), LazureIcons.GraphicEq, onLive, primary = true),
        HomeAction(stringResource(R.string.home_action_import), stringResource(R.string.home_action_import_hint), LazureIcons.AudioFile, onClick = onImport, primary = true),
        HomeAction(stringResource(R.string.home_action_models), stringResource(R.string.home_action_models_hint), LazureIcons.ModelTraining, onClick = onModels),
        HomeAction(stringResource(R.string.home_action_vault), stringResource(R.string.home_action_vault_hint), LazureIcons.FolderOpen, onClick = onVault),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Lazure", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(LazureIcons.Search, contentDescription = stringResource(R.string.home_search))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("home_feed"),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth < 280.dp * LocalDensity.current.fontScale) 1 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.chunked(columns).forEach { pair ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { action ->
                                    ActionTile(action, Modifier.weight(1f).fillMaxHeight(), horizontal = columns == 1)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (recent.isNotEmpty()) TextButton(onClick = onVault) { Text(stringResource(R.string.home_see_all)) }
                }
            }
            if (recent.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(LazureIcons.GraphicEq, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.home_recent_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            }
                            Text(stringResource(R.string.home_recent_empty), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onLive, modifier = Modifier.heightIn(min = 48.dp).testTag("home_empty_record")) {
                                Text(stringResource(R.string.live_start))
                            }
                        }
                    }
                }
            } else {
                items(recent.take(5), key = { it.id }) { item ->
                    RecentRecordingRow(item, onClick = { onOpenRecording(item.id) })
                }
            }

        }
    }
}

@Composable
private fun ActionTile(action: HomeAction, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val container = if (action.primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (action.primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Card(
        onClick = action.onClick,
        modifier = modifier.heightIn(min = if (horizontal) 140.dp else 116.dp).testTag("home_action_" + action.title),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = MaterialTheme.shapes.large,
    ) {
        if (horizontal) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(action.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                ActionText(action, content, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(action.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                ActionText(action, content)
            }
        }
    }
}

@Composable
private fun ActionText(action: HomeAction, content: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(action.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(action.subtitle, style = MaterialTheme.typography.bodySmall,
            color = if (action.primary) content.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentRecordingRow(recording: RecordingEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(LazureIcons.AudioFile, null, Modifier.padding(11.dp).size(21.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(recording.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatDuration(recording.durationMs)} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(recording.createdAt))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(statusLabel(recording.processingStatus), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    "completed" -> stringResource(R.string.recordings_processing_ready)
    "processing", "queued" -> stringResource(R.string.recordings_processing)
    "failed" -> stringResource(R.string.recordings_processing_failed)
    else -> stringResource(R.string.recordings_waiting_model)
}

private fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
