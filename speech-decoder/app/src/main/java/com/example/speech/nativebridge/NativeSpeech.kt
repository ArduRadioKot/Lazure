package com.example.speech.nativebridge

fun interface SegmentListener {
    fun onSegment(startMs: Long, endMs: Long, speaker: Int, text: String)
}

/** Тонкая обёртка над JNI. Весь DSP/ML живёт в нативных потоках; сюда приходит только готовый текст. */
class NativeSpeech {
    companion object {
        init {
            System.loadLibrary("c++_shared")
            System.loadLibrary("speechcore")
        }
    }

    private var handle = 0L

    /** false — не хватает обязательных моделей или ошибка инициализации ORT. */
    fun create(modelDir: String, listener: SegmentListener): Boolean {
        handle = nativeCreate(modelDir, listener)
        return handle != 0L
    }
    fun start(): Boolean = nativeStart(handle)
    fun stop() = nativeStop(handle)
    fun destroy() { nativeDestroy(handle); handle = 0L }
    fun audioSessionId(): Int = nativeSessionId(handle)

    private external fun nativeCreate(modelDir: String, listener: SegmentListener): Long
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeDestroy(handle: Long)
    private external fun nativeSessionId(handle: Long): Int
}
