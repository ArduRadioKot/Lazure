package com.rassvet.essential.ui.recording

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rassvet.essential.data.local.SpeakerEntity
import com.rassvet.essential.data.local.TranscriptSegmentEntity
import com.rassvet.essential.data.recording.MediaCodecAudioDecoder
import com.rassvet.essential.ui.theme.LazureIcons
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Owns one MediaPlayer for the detail screen and exposes position/duration/playing as Compose state. */
class RecordingPlayerState(private val path: String, val durationHintMs: Long) {
    private var mp: MediaPlayer? = null
    var playing by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var error by mutableStateOf<String?>(null)
    val durationMs: Long get() = mp?.duration?.toLong()?.takeIf { it > 0 } ?: durationHintMs

    private fun ensure(): MediaPlayer = mp ?: MediaPlayer().also {
        it.setDataSource(File(path).absolutePath); it.prepare()
        it.setOnCompletionListener { playing = false; positionMs = 0 }
        mp = it
    }

    fun toggle() = runCatching {
        val p = ensure()
        if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
    }.onFailure { error = it.message }

    fun seekTo(ms: Long, play: Boolean = false) = runCatching {
        val p = ensure()
        p.seekTo(ms.coerceIn(0, durationMs).toInt()); positionMs = ms.coerceIn(0, durationMs)
        if (play && !p.isPlaying) { p.start(); playing = true }
    }.onFailure { error = it.message }

    fun refresh() { mp?.let { positionMs = it.currentPosition.toLong(); playing = it.isPlaying } }

    fun release() { runCatching { mp?.release() }; mp = null; playing = false }
}

/** Waveform bins (0..1), cached next to the audio file; decoded lazily the first time a recording is opened. */
object WaveformPeaks {
    private const val BINS = 200

    suspend fun load(audioPath: String): FloatArray = withContext(Dispatchers.IO) {
        val cache = File("$audioPath.peaks")
        if (cache.isFile && cache.length() == BINS.toLong()) return@withContext cache.readBytes().let { b -> FloatArray(BINS) { (b[it].toInt() and 0xFF) / 255f } }
        val dir = File(File(audioPath).parentFile, ".wave").also { it.mkdirs() }
        val peaks = FloatArray(BINS)
        MediaCodecAudioDecoder().decode16kMono(audioPath, dir, "wave.raw").use { pcm ->
            val per = maxOf(1, pcm.size / BINS)
            var base = 0
            while (base < pcm.size) {
                val block = pcm.floats(base, base + 160_000)
                for (i in block.indices) { val bin = minOf(BINS - 1, (base + i) / per); val v = abs(block[i]); if (v > peaks[bin]) peaks[bin] = v }
                base += block.size
            }
        }
        dir.deleteRecursively()
        val max = (peaks.maxOrNull() ?: 1f).coerceAtLeast(0.05f)
        for (i in peaks.indices) peaks[i] = (peaks[i] / max).coerceIn(0f, 1f)
        runCatching { cache.writeBytes(ByteArray(BINS) { (peaks[it] * 255).toInt().toByte() }) }
        peaks
    }
}

/** Colour for a speaker, stable per colorIndex and drawn from the Material scheme. */
@Composable
fun speakerColor(index: Int): Color {
    val s = MaterialTheme.colorScheme
    val palette = listOf(s.primary, s.tertiary, s.secondary, s.error, s.inversePrimary, s.outline)
    return palette[index.mod(palette.size)]
}

@Composable
fun PlayerBar(player: RecordingPlayerState, audioPath: String, segments: List<TranscriptSegmentEntity>, speakers: List<SpeakerEntity>) {
    var peaks by remember(audioPath) { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(audioPath) { peaks = runCatching { WaveformPeaks.load(audioPath) }.getOrNull() }

    val progress = if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.outlineVariant
    val colors = speakers.associate { it.rawSpeakerId to speakerColor(it.colorIndex) }
    val fallback = MaterialTheme.colorScheme.outline

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(onClick = { player.toggle() }, modifier = Modifier.size(48.dp)) {
                    Icon(if (player.playing) LazureIcons.Pause else LazureIcons.PlayArrow, contentDescription = null)
                }
                Column(Modifier.weight(1f)) {
                    Canvas(
                        Modifier.fillMaxWidth().height(56.dp)
                            .pointerInput(player.durationMs) { detectTapGestures { o -> player.seekTo((o.x / size.width * player.durationMs).toLong()) } }
                            .pointerInput(player.durationMs) { detectHorizontalDragGestures { change, _ -> player.seekTo((change.position.x.coerceIn(0f, size.width.toFloat()) / size.width * player.durationMs).toLong()) } },
                    ) {
                        val bars = peaks ?: FloatArray(60) { 0.12f }
                        val n = bars.size
                        val gap = 2.dp.toPx(); val w = (size.width - gap * (n - 1)) / n
                        bars.forEachIndexed { i, v ->
                            val h = (4.dp.toPx() + (size.height - 4.dp.toPx()) * v).coerceAtMost(size.height)
                            drawRoundRect(if ((i + 0.5f) / n <= progress) played else rest,
                                Offset(i * (w + gap), (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
                        }
                        // playhead
                        drawRect(played, Offset((size.width * progress - 1.dp.toPx()).coerceAtLeast(0f), 0f), Size(2.dp.toPx(), size.height))
                    }
                    // speaker strip: who talks when (overlaps stack as overlapping strips of the same row)
                    val overlapColor = MaterialTheme.colorScheme.error
                    Canvas(Modifier.fillMaxWidth().height(10.dp).padding(top = 2.dp)) {
                        val d = player.durationMs.coerceAtLeast(1)
                        segments.forEach { s ->
                            drawRect((colors[s.speakerId] ?: fallback).copy(alpha = 0.85f),
                                Offset(size.width * s.startMs / d, 0f), Size((size.width * (s.endMs - s.startMs) / d).coerceAtLeast(1f), size.height * 0.6f))
                        }
                        // simultaneous speech: red band under the strip wherever two speakers' segments overlap
                        segments.forEach { a ->
                            segments.forEach { b ->
                                if (a.id < b.id && a.speakerId != b.speakerId) {
                                    val st = maxOf(a.startMs, b.startMs); val en = minOf(a.endMs, b.endMs)
                                    if (en - st > 250) drawRect(overlapColor, Offset(size.width * st / d, size.height * 0.65f),
                                        Size((size.width * (en - st) / d).coerceAtLeast(2f), size.height * 0.35f))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(player.positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(clock(player.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
