package com.rassvet.essential.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import java.io.RandomAccessFile
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rassvet.essential.MainActivity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CaptureState { Idle, Recording, Paused, Stopped, Failed }

data class CaptureSnapshot(
    val state: CaptureState = CaptureState.Idle,
    val elapsedMs: Long = 0,
    val level: Float = 0f,
    val file: File? = null,
    val error: String? = null,
)

/** Process-wide view of the active capture; the UI observes it and the service mutates it. */
object CaptureSession {
    private val _state = MutableStateFlow(CaptureSnapshot())
    val state: StateFlow<CaptureSnapshot> = _state.asStateFlow()
    internal fun update(f: (CaptureSnapshot) -> CaptureSnapshot) = _state.update(f)
    fun reset() { _state.value = CaptureSnapshot() }
}

/**
 * Foreground microphone service. Capture follows the pipeline's stages 0–1: probe the capture rate, record mono PCM
 * from the VOICE_RECOGNITION source with the platform AcousticEchoCanceler attached when the device provides one
 * (the effect runs in the audio HAL at the stream rate), and write a WAV file. Later stages resample/denoise.
 */
class RecordingService : Service() {
    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var file: File? = null
    private var wav: RandomAccessFile? = null
    private var writer: Thread? = null
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var samplesWritten = 0L
    private var sampleRate = 16_000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Lazure").setContentText(text).setOngoing(true).setContentIntent(open).build()
    }

    /** Stage 0: first rate the device can capture from, preferring the 16 kHz the models use. */
    @SuppressLint("MissingPermission")
    private fun openRecord(): AudioRecord {
        for (rate in intArrayOf(16_000, 48_000, 44_100)) {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) continue
            val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, min * 4)
            if (r.state == AudioRecord.STATE_INITIALIZED) { sampleRate = rate; return r }
            r.release()
        }
        error("No supported microphone configuration")
    }

    private fun start() {
        if (record != null) return
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, notification("Recording…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIF_ID, notification("Recording…"))
            val dir = File(filesDir, "recordings").apply { mkdirs() }
            val f = File(dir, "${UUID.randomUUID()}.wav")
            val r = openRecord()
            // Stage 1: AEC. Only effective on devices that implement the effect and when audio is playing nearby.
            aec = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(r.audioSessionId)?.also { it.enabled = true } }.getOrNull() else null
            android.util.Log.i("RecordingService", "capture ${sampleRate} Hz, AEC=${aec?.enabled ?: false}")
            val out = RandomAccessFile(f, "rw").also { it.setLength(0); writeWavHeader(it, sampleRate, 0) }
            record = r; file = f; wav = out; samplesWritten = 0; paused = false; running = true
            r.startRecording()
            writer = Thread {
                var headerAt = 0L
                val buf = ShortArray(sampleRate / 10)
                val bytes = ByteArray(buf.size * 2)
                while (running) {
                    val n = r.read(buf, 0, buf.size)
                    if (n <= 0) { if (n < 0) break else continue }
                    var peak = 0
                    for (i in 0 until n) { val v = buf[i].toInt(); bytes[2 * i] = v.toByte(); bytes[2 * i + 1] = (v shr 8).toByte(); peak = maxOf(peak, kotlin.math.abs(v)) }
                    if (!paused) {
                        out.write(bytes, 0, n * 2); samplesWritten += n
                        // Keep the header valid every ~2 s so a killed process leaves a playable, recoverable file.
                        if (samplesWritten - headerAt >= sampleRate * 2L) { headerAt = samplesWritten; patchWavHeader(out, sampleRate, samplesWritten) }
                    }
                    CaptureSession.update { it.copy(level = if (paused) 0f else (peak / 32767f).coerceIn(0f, 1f)) }
                }
            }.also { it.start() }
            CaptureSession.update { CaptureSnapshot(CaptureState.Recording, 0, 0f, f, null) }
            ticker = scope.launch {
                while (isActive) {
                    CaptureSession.update { it.copy(elapsedMs = samplesWritten * 1000 / sampleRate) }
                    delay(200)
                }
            }
        } catch (t: Throwable) {
            cleanup(); file?.delete()
            CaptureSession.update { CaptureSnapshot(CaptureState.Failed, error = t.message ?: "Could not start recording") }
            stopForegroundCompat(); stopSelf()
        }
    }

    private fun pause() { if (record == null) return; paused = true; CaptureSession.update { it.copy(state = CaptureState.Paused, level = 0f) } }
    private fun resume() { if (record == null) return; paused = false; CaptureSession.update { it.copy(state = CaptureState.Recording) } }

    private fun cleanup() {
        running = false
        runCatching { writer?.join(1000) }; writer = null
        runCatching { record?.stop() }; runCatching { record?.release() }; record = null
        runCatching { aec?.release() }; aec = null
    }

    private fun finalizeWav(@Suppress("UNUSED_PARAMETER") f: File, out: RandomAccessFile, samples: Long) {
        patchWavHeader(out, sampleRate, samples); out.close()
    }

    private fun stop() {
        val f = file; val out = wav
        ticker?.cancel()
        cleanup()
        val samples = samplesWritten
        file = null; wav = null
        if (f == null || out == null) { stopForegroundCompat(); stopSelf(); return }
        runCatching { finalizeWav(f, out, samples) }
        if (samples > sampleRate / 4) CaptureSession.update { CaptureSnapshot(CaptureState.Stopped, samples * 1000 / sampleRate, 0f, f) }
        else { f.delete(); CaptureSession.update { CaptureSnapshot(CaptureState.Failed, error = "Recording is too short or failed") } }
        stopForegroundCompat(); stopSelf()
    }

    private fun stopForegroundCompat() { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true) }

    override fun onDestroy() {
        ticker?.cancel()
        if (record != null) { val f = file; val o = wav; val n = samplesWritten; cleanup(); if (f != null && o != null) runCatching { finalizeWav(f, o, n) } }
        super.onDestroy()
    }

    companion object {
        /** Writes a 16-bit mono PCM WAV header for [samples] samples at the current file position (0). */
        fun writeWavHeader(out: RandomAccessFile, rate: Int, samples: Long) {
            val dataLen = samples * 2
            val h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt((36 + dataLen).toInt()).put("WAVE".toByteArray()).put("fmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(dataLen.toInt())
            out.seek(0); out.write(h.array())
        }

        /** Rewrites the header and restores the write position to the end of the audio data. */
        fun patchWavHeader(out: RandomAccessFile, rate: Int, samples: Long) {
            writeWavHeader(out, rate, samples); out.seek(44 + samples * 2)
        }

        private const val CHANNEL = "recording"; private const val NOTIF_ID = 4201
        const val ACTION_START = "start"; const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"; const val ACTION_STOP = "stop"
        fun send(context: Context, action: String) {
            val i = Intent(context, RecordingService::class.java).setAction(action)
            if (action == ACTION_START && Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }
}
