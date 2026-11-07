package com.rassvet.essential.data.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Push-to-toggle voice input for the chat box. Uses the bundled GigaAM model; no audio leaves the device. */
class LocalDictation(private val context: Context) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var capturing = false
    private val buffer = ArrayList<ShortArray>()

    val isRecording: Boolean get() = capturing

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (capturing) return true
        val rate = SpeechModelPaths.SAMPLE_RATE
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, min * 4)
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        synchronized(buffer) { buffer.clear() }
        record = rec; capturing = true
        rec.startRecording()
        thread = Thread {
            val chunk = ShortArray(min)
            var total = 0
            while (capturing && total < rate * 120) { // 2 minute cap
                val n = rec.read(chunk, 0, chunk.size)
                if (n > 0) { synchronized(buffer) { buffer += chunk.copyOf(n) }; total += n } else if (n < 0) break
            }
        }.also { it.start() }
        return true
    }

    /** Stops capture and returns the recognized text ("" for silence). */
    suspend fun stopAndTranscribe(): String = withContext(Dispatchers.Default) {
        capturing = false
        thread?.join(); thread = null
        record?.let { runCatching { it.stop() }; it.release() }; record = null
        val pcm = synchronized(buffer) { buffer.toList().also { buffer.clear() } }
        val total = pcm.sumOf { it.size }
        if (total < SpeechModelPaths.SAMPLE_RATE / 4) return@withContext ""
        val samples = FloatArray(total)
        var o = 0
        pcm.forEach { c -> c.forEach { samples[o++] = it / 32768f } }
        val rec = recognizer(context)
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(samples, SpeechModelPaths.SAMPLE_RATE)
            synchronized(rec) { rec.decode(stream) }
            rec.getResult(stream).text.trim()
        } finally { stream.release() }
    }

    fun cancel() {
        capturing = false
        thread?.join(); thread = null
        record?.let { runCatching { it.stop() }; it.release() }; record = null
    }

    companion object {
        @Volatile private var shared: OfflineRecognizer? = null
        private fun recognizer(context: Context): OfflineRecognizer = shared ?: synchronized(this) {
            shared ?: OfflineRecognizer(context.applicationContext.assets, OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = SpeechModelPaths.ASR_MODEL),
                    tokens = SpeechModelPaths.ASR_TOKENS, numThreads = 2, modelType = "nemo_ctc",
                ),
            )).also { shared = it }
        }
    }
}
