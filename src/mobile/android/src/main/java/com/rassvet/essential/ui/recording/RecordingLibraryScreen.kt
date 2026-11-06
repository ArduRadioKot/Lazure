package com.rassvet.essential.ui.recording

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rassvet.essential.R
import com.rassvet.essential.data.local.RecordingEntity
import com.rassvet.essential.data.local.SpeakerEntity
import com.rassvet.essential.data.local.TranscriptSegmentEntity
import com.rassvet.essential.data.recording.RecordingRepository
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private enum class RecordingFilter { All, Completed, Processing, Failed }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingLibraryScreen(repository: RecordingRepository, onBack: () -> Unit, showBack: Boolean = true,
    initialRecordingId: String? = null, startImportOnOpen: Boolean = false) {
    val rows by repository.recordings.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<RecordingEntity?>(null) }
    var titleQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RecordingFilter.All) }
    var sortByTitle by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { runCatching { repository.importAudio(uri) }.onFailure { error = it.message } }
    }
    LaunchedEffect(initialRecordingId, rows) { if (initialRecordingId != null) selected = rows.firstOrNull { it.id == initialRecordingId } }
    LaunchedEffect(startImportOnOpen) { if (startImportOnOpen) importer.launch(arrayOf("audio/*")) }
    Scaffold(topBar = { TopAppBar(title = { Text(selected?.title ?: stringResource(R.string.recordings_title)) },
        navigationIcon = { if (showBack || selected != null) IconButton(onClick = { if (selected != null) selected = null else onBack() }) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.recordings_back))
        } }, actions = { if (selected == null) IconButton(onClick = { importer.launch(arrayOf("audio/*")) }) {
            Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.recordings_import))
        } }) }) { insets ->
        if (selected == null) {
            if (rows.isEmpty()) Column(Modifier.fillMaxSize().padding(insets).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.AudioFile, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.recordings_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.recordings_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { importer.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.recordings_import)) }
            } else LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(titleQuery, { titleQuery = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.recordings_search_hint)) }) }
                item { TextButton(onClick = { sortByTitle = !sortByTitle }) {
                    Text(stringResource(if (sortByTitle) R.string.recordings_sort_title else R.string.recordings_sort_date))
                } }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(RecordingFilter.All to R.string.recordings_filter_all,
                            RecordingFilter.Completed to R.string.recordings_filter_completed,
                            RecordingFilter.Processing to R.string.recordings_filter_processing,
                            RecordingFilter.Failed to R.string.recordings_filter_failed).forEach { (value, label) ->
                            FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                val visible = rows.filter { row ->
                    val queryMatch = row.title.contains(titleQuery.trim(), true)
                    val filterMatch = when (filter) {
                        RecordingFilter.All -> true
                        RecordingFilter.Completed -> row.processingStatus == "completed"
                        RecordingFilter.Processing -> row.processingStatus == "processing" || row.processingStatus == "queued"
                        RecordingFilter.Failed -> row.processingStatus == "failed"
                    }
                    queryMatch && filterMatch
                }.let { filtered -> if (sortByTitle) filtered.sortedBy { it.title.lowercase() } else filtered.sortedByDescending { it.createdAt } }
                if (visible.isEmpty()) item { Text(stringResource(R.string.recordings_empty_filter), Modifier.padding(16.dp)) }
                items(visible, key = { it.id }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth().clickable { selected = row }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AudioFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.title, style = MaterialTheme.typography.titleSmall)
                                    Text(DateFormat.getDateTimeInstance().format(Date(row.createdAt)) + " · " + duration(row.durationMs),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(statusText(row.processingStatus), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { renameText = row.title; renameTarget = row }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.recordings_rename))
                                }
                                IconButton(onClick = { deleteTarget = row }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.recordings_delete))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val row = selected!!
            val segments by repository.observeSegments(row.id).collectAsState(initial = emptyList())
            val speakers by repository.speakers(row.id).collectAsState(initial = emptyList())
            var player by remember(row.id) { mutableStateOf<MediaPlayer?>(null) }
            var json by remember { mutableStateOf<String?>(null) }
            var editTarget by remember { mutableStateOf<TranscriptSegmentEntity?>(null) }
            var editText by remember { mutableStateOf("") }
            var speakerTarget by remember { mutableStateOf<SpeakerEntity?>(null) }
            var speakerName by remember { mutableStateOf("") }
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
                if (uri != null && json != null) scope.launch {
                    runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) } }
                        .onFailure { error = it.message }
                }
            }
            DisposableEffect(row.id) { onDispose { player?.release() } }
            LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(statusText(row.processingStatus), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                item { OutlinedButton(onClick = { runCatching {
                    val active = player ?: MediaPlayer().also { it.setDataSource(File(row.audioPath).absolutePath); it.prepare(); player = it }
                    active.start()
                }.onFailure { error = it.message } }) {
                    Icon(Icons.Outlined.PlayArrow, null); Text("  " + stringResource(R.string.recordings_playback))
                } }
                item { OutlinedButton(onClick = { scope.launch { runCatching { repository.exportJson(row.id) }
                    .onSuccess { json = it; export.launch(row.title + ".json") }.onFailure { error = it.message } } }) {
                    Text(stringResource(R.string.recordings_json))
                } }
                if (segments.isEmpty()) item { Text(stringResource(R.string.recordings_transcript_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else {
                    item { Text(stringResource(R.string.recordings_transcript), style = MaterialTheme.typography.titleLarge) }
                    items(speakers, key = { it.rawSpeakerId }) { speaker ->
                        TextButton(onClick = { speakerName = speaker.displayName; speakerTarget = speaker }) {
                            Text(speaker.displayName + " · " + stringResource(R.string.speaker_rename))
                        }
                    }
                    items(segments, key = { it.id }) { segment ->
                        Card(Modifier.fillMaxWidth().clickable { runCatching {
                            val active = player ?: MediaPlayer().also { it.setDataSource(File(row.audioPath).absolutePath); it.prepare(); player = it }
                            active.seekTo(segment.startMs.toInt()); if (!active.isPlaying) active.start()
                        }.onFailure { error = it.message } }) {
                            Column(Modifier.padding(12.dp)) {
                                Text((speakers.firstOrNull { it.rawSpeakerId == segment.speakerId }?.displayName ?: segment.speakerId) +
                                    " · " + duration(segment.startMs) + "–" + duration(segment.endMs),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(segment.text, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { editTarget = segment; editText = segment.text }) {
                                    Icon(Icons.Outlined.Edit, null); Text(stringResource(R.string.transcript_edit))
                                }
                            }
                        }
                    }
                }
                if (error != null) item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
            editTarget?.let { segment -> AlertDialog(onDismissRequest = { editTarget = null },
                title = { Text(stringResource(R.string.transcript_edit)) },
                text = { OutlinedTextField(editText, { editText = it }, minLines = 3) },
                confirmButton = { TextButton(onClick = { scope.launch { runCatching {
                    repository.updateSegmentText(row.id, segment.id, editText)
                }.onFailure { error = it.message }; editTarget = null } }) { Text(stringResource(R.string.ok)) } },
                dismissButton = { TextButton(onClick = { editTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
            speakerTarget?.let { speaker -> AlertDialog(onDismissRequest = { speakerTarget = null },
                title = { Text(stringResource(R.string.speaker_rename)) },
                text = { OutlinedTextField(speakerName, { speakerName = it }, singleLine = true) },
                confirmButton = { TextButton(onClick = { scope.launch { runCatching {
                    repository.renameSpeaker(row.id, speaker.rawSpeakerId, speakerName)
                }.onFailure { error = it.message }; speakerTarget = null } }) { Text(stringResource(R.string.ok)) } },
                dismissButton = { TextButton(onClick = { speakerTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
        }
        renameTarget?.let { target -> AlertDialog(onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.recordings_rename_title)) },
            text = { OutlinedTextField(renameText, { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { scope.launch { runCatching { repository.rename(target.id, renameText) }
                .onFailure { error = it.message }; renameTarget = null } }) { Text(stringResource(R.string.recordings_rename)) } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
        deleteTarget?.let { target -> AlertDialog(onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.recordings_delete)) }, text = { Text(stringResource(R.string.recordings_delete_confirm)) },
            confirmButton = { TextButton(onClick = { scope.launch { repository.delete(target.id); deleteTarget = null } }) {
                Text(stringResource(R.string.recordings_delete)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }) }
    }
}

@Composable
private fun statusText(value: String) = when (value) {
    "completed" -> stringResource(R.string.recordings_processing_ready)
    "processing", "queued" -> stringResource(R.string.recordings_processing)
    "failed" -> stringResource(R.string.recordings_processing_failed)
    else -> stringResource(R.string.recordings_waiting_model)
}
private fun duration(ms: Long) = "%d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
