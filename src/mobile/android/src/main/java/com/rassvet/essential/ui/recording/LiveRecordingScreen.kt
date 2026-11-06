package com.rassvet.essential.ui.recording

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rassvet.essential.R
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.recording.RecordingRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LiveState { Idle, Recording, Paused, Saving, Saved }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRecordingScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context, db) { RecordingRepository(context.applicationContext, db) }
    var state by remember { mutableStateOf(LiveState.Idle) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var shownElapsedMs by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }

    fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()

    fun startRecording() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "recordings").apply { mkdirs() }
                    val file = File(dir, "${UUID.randomUUID()}.m4a")
                    val active = createRecorder()
                    try {
                        active.setAudioSource(MediaRecorder.AudioSource.MIC)
                        active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        active.setAudioEncodingBitRate(96_000)
                        active.setAudioSamplingRate(44_100)
                        active.setOutputFile(file.absolutePath)
                        active.prepare()
                        active.start()
                        file to active
                    } catch (t: Throwable) {
                        runCatching { active.release() }
                        file.delete()
                        throw t
                    }
                }
            }.onSuccess { (file, active) ->
                audioFile = file
                recorder = active
                startedAt = System.currentTimeMillis()
                state = LiveState.Recording
            }.onFailure { error = it.message ?: "Could not start recording" }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else error = context.getString(R.string.live_permission_body)
    }

    fun requestOrStart() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) runCatching {
            recorder?.pause()
            elapsedMs += System.currentTimeMillis() - startedAt
            state = LiveState.Paused
        }.onFailure { error = it.message ?: "Could not pause recording" }
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) runCatching {
            recorder?.resume()
            startedAt = System.currentTimeMillis()
            state = LiveState.Recording
        }.onFailure { error = it.message ?: "Could not resume recording" }
    }

    fun stopAndSave() {
        val active = recorder ?: return
        val wasRecording = state == LiveState.Recording
        if (wasRecording) elapsedMs += System.currentTimeMillis() - startedAt
        state = LiveState.Saving
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    active.stop()
                    active.release()
                    repository.registerRecordedAudio(checkNotNull(audioFile) { "Recording file is missing" })
                }
            }.onSuccess {
                recorder = null
                state = LiveState.Saved
            }.onFailure {
                runCatching { active.reset(); active.release() }
                audioFile?.delete()
                recorder = null
                state = LiveState.Idle
                error = it.message ?: "Could not save recording"
            }
        }
    }

    LaunchedEffect(state) {
        while (state == LiveState.Recording) {
            shownElapsedMs = elapsedMs + (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            delay(500)
        }
        shownElapsedMs = elapsedMs
    }
    DisposableEffect(Unit) {
        onDispose {
            if (recorder != null && state != LiveState.Saved) {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                audioFile?.delete()
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.live_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(64.dp),
                tint = if (state == LiveState.Recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text(formatElapsed(shownElapsedMs), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(when (state) {
                LiveState.Idle -> stringResource(R.string.live_permission_title)
                LiveState.Recording -> stringResource(R.string.live_recording)
                LiveState.Paused -> stringResource(R.string.live_paused)
                LiveState.Saving -> stringResource(R.string.live_saving)
                    LiveState.Saved -> stringResource(R.string.live_saved)
            }, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.live_model_pending), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(30.dp))
            when (state) {
                LiveState.Idle -> Button(onClick = ::requestOrStart) {
                    Icon(Icons.Outlined.Mic, null); Text("  ${stringResource(R.string.live_start)}")
                }
                LiveState.Recording -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = ::pauseRecording) {
                        Icon(Icons.Outlined.Pause, null); Text("  ${stringResource(R.string.live_pause)}")
                    }
                    Button(onClick = ::stopAndSave) {
                        Icon(Icons.Outlined.Stop, null); Text("  ${stringResource(R.string.live_stop)}")
                    }
                }
                LiveState.Paused -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = ::resumeRecording) {
                        Icon(Icons.Outlined.PlayArrow, null); Text("  ${stringResource(R.string.live_resume)}")
                    }
                    Button(onClick = ::stopAndSave) {
                        Icon(Icons.Outlined.Stop, null); Text("  ${stringResource(R.string.live_stop)}")
                    }
                }
                LiveState.Saving -> CircularProgressIndicator()
                LiveState.Saved -> Button(onClick = onSaved) { Text(stringResource(R.string.live_open_vault)) }
            }
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatElapsed(ms: Long): String = "%02d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
