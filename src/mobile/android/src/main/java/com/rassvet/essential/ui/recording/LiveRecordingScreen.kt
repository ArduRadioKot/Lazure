package com.rassvet.essential.ui.recording

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rassvet.essential.R
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.recording.RecordingRepository
import com.rassvet.essential.service.CaptureSession
import com.rassvet.essential.service.CaptureState
import com.rassvet.essential.service.RecordingService
import com.rassvet.essential.ui.theme.LazureIcons
import kotlinx.coroutines.delay

private enum class LiveState { Idle, Recording, Paused, Saving, Saved }

private const val BARS = 44

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRecordingScreen(
    db: AppDatabase,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context, db) { RecordingRepository(context.applicationContext, db) }
    val snap by CaptureSession.state.collectAsState()
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val state = when {
        saved -> LiveState.Saved
        saving -> LiveState.Saving
        snap.state == CaptureState.Recording -> LiveState.Recording
        snap.state == CaptureState.Paused -> LiveState.Paused
        else -> LiveState.Idle
    }
    var lastElapsed by remember { mutableLongStateOf(0L) }
    if (snap.elapsedMs > 0) lastElapsed = snap.elapsedMs

    // Rolling level history for the waveform: a new bar every 80 ms while recording, flat while paused.
    val levels = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }
    LaunchedEffect(state) {
        while (state == LiveState.Recording || state == LiveState.Paused) {
            val v = if (state == LiveState.Recording) CaptureSession.state.value.level else 0f
            levels.removeAt(0); levels.add(v)
            delay(80)
        }
        if (state == LiveState.Idle) for (i in levels.indices) levels[i] = 0f
    }

    LaunchedEffect(snap.state) {
        when (snap.state) {
            CaptureState.Stopped -> {
                saving = true
                runCatching { repository.registerRecordedAudio(checkNotNull(snap.file)) }
                    .onSuccess { saved = true }
                    .onFailure { error = it.message ?: "Could not save recording" }
                saving = false
                CaptureSession.reset()
            }
            CaptureState.Failed -> { error = snap.error; CaptureSession.reset() }
            else -> Unit
        }
    }

    fun startRecording() { error = null; RecordingService.send(context, RecordingService.ACTION_START) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else error = context.getString(R.string.live_permission_body)
    }
    fun requestOrStart() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun stopAndSave() = RecordingService.send(context, RecordingService.ACTION_STOP)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(LazureIcons.ArrowBack, contentDescription = null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            StatusPill(state)
            Spacer(Modifier.weight(1f))

            AnimatedContent(targetState = state == LiveState.Saved, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "body") { done ->
                if (done) SavedCard(onOpen = onSaved, onAgain = { saved = false; lastElapsed = 0L; CaptureSession.reset() }, durationMs = lastElapsed)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatElapsed(lastElapsed),
                        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                        color = if (state == LiveState.Paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(28.dp))
                    Waveform(levels, active = state == LiveState.Recording, modifier = Modifier.fillMaxWidth().height(96.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(
                        when (state) {
                            LiveState.Idle -> stringResource(R.string.live_tap_start)
                            LiveState.Recording -> stringResource(R.string.live_recording)
                            LiveState.Paused -> stringResource(R.string.live_paused)
                            else -> stringResource(R.string.live_saving)
                        },
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.weight(1f))

            if (error != null) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(error.orEmpty(), Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))
            }

            Controls(
                state = state,
                onStart = ::requestOrStart,
                onPause = { RecordingService.send(context, RecordingService.ACTION_PAUSE) },
                onResume = { RecordingService.send(context, RecordingService.ACTION_RESUME) },
                onStop = ::stopAndSave,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                if (state == LiveState.Saved) stringResource(R.string.live_processing_note) else stringResource(R.string.live_local_badge),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusPill(state: LiveState) {
    val live = state == LiveState.Recording
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dotAlpha",
    )
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).alpha(if (live) pulse else 1f).background(
                    when (state) { LiveState.Recording -> MaterialTheme.colorScheme.error; LiveState.Paused -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.outline },
                    CircleShape,
                ),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                when (state) {
                    LiveState.Recording -> "REC"; LiveState.Paused -> "PAUSE"; LiveState.Saving -> "…"; LiveState.Saved -> "OK"; else -> "READY"
                },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Waveform(levels: List<Float>, active: Boolean, modifier: Modifier) {
    val on = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val dim = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val n = levels.size
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val minH = 4.dp.toPx()
        levels.forEachIndexed { i, raw ->
            // sqrt lifts quiet speech so the bars stay readable; newest bars (right) are brightest
            val h = (minH + (size.height - minH) * kotlin.math.sqrt(raw.coerceIn(0f, 1f))).coerceAtMost(size.height)
            val age = i.toFloat() / (n - 1)
            drawRoundRect(
                color = (if (raw > 0.01f) on else dim).copy(alpha = 0.35f + 0.65f * age),
                topLeft = Offset(i * (w + gap), (size.height - h) / 2),
                size = Size(w, h), cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

@Composable
private fun Controls(state: LiveState, onStart: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(112.dp)) {
        when (state) {
            LiveState.Idle -> MainButton(onClick = onStart, recording = false)
            LiveState.Recording, LiveState.Paused -> {
                FilledTonalIconButton(onClick = if (state == LiveState.Recording) onPause else onResume, modifier = Modifier.size(64.dp)) {
                    Icon(if (state == LiveState.Recording) LazureIcons.Pause else LazureIcons.PlayArrow,
                        contentDescription = stringResource(if (state == LiveState.Recording) R.string.live_pause else R.string.live_resume), modifier = Modifier.size(28.dp))
                }
                MainButton(onClick = onStop, recording = true, pulsing = state == LiveState.Recording)
                Spacer(Modifier.size(64.dp)) // keeps the stop button centred
            }
            LiveState.Saving -> CircularProgressIndicator()
            LiveState.Saved -> Unit
        }
    }
}

@Composable
private fun MainButton(onClick: () -> Unit, recording: Boolean, pulsing: Boolean = false) {
    val ring by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 1f, targetValue = 1.28f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ringScale",
    )
    val ringAlpha by rememberInfiniteTransition(label = "ringA").animateFloat(
        initialValue = 0.35f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ringAlpha",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
        if (pulsing) Box(Modifier.size(88.dp).scale(ring).alpha(ringAlpha).background(MaterialTheme.colorScheme.error, CircleShape))
        FilledIconButton(
            onClick = onClick, modifier = Modifier.size(88.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (recording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(if (recording) LazureIcons.Stop else LazureIcons.Mic,
                contentDescription = stringResource(if (recording) R.string.live_stop else R.string.live_start), modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun SavedCard(onOpen: () -> Unit, onAgain: () -> Unit, durationMs: Long) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(LazureIcons.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.live_saved_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(formatElapsed(durationMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.live_open_vault)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.live_record_another)) }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}
