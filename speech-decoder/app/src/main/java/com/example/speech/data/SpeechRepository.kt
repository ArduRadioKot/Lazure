package com.example.speech.data

import android.content.Context
import android.media.audiofx.AcousticEchoCanceler
import com.example.speech.domain.Segment
import com.example.speech.nativebridge.NativeSpeech
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class SpeechRepository(private val ctx: Context, private val native: NativeSpeech = NativeSpeech()) {
    private val _segments = MutableSharedFlow<Segment>(
        extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val segments: SharedFlow<Segment> = _segments

    private var aec: AcousticEchoCanceler? = null
    @Volatile private var running = false

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        val dir = ModelInstaller.ensureModels(ctx)
        if (!native.create(dir.absolutePath) { s, e, spk, t -> _segments.tryEmit(Segment(s, e, spk, t)) }) return false
        if (!native.start()) { native.destroy(); return false }

        // Этап 1: системный AEC поверх аудио-сессии Oboe (если эффект есть на устройстве)
        val sid = native.audioSessionId()
        if (sid > 0 && AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(sid)?.apply { enabled = true }
        }
        running = true
        return true
    }

    @Synchronized
    fun stop() {
        if (!running) return
        aec?.release(); aec = null
        native.stop()
        native.destroy()
        running = false
    }
}
