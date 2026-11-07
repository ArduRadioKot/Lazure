package com.rassvet.essential.data.recording

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Stage 10: MossFormer2_SS_16K (ClearerVoice, Apache-2.0), exported to a fixed 2 s ONNX window (int8 MatMul).
 * Long regions are processed with 75 % overlapping windows; the source order of each window is aligned to the
 * previous one by correlation before triangular overlap-add.
 */
class OverlapSeparator(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        // Load from a file (copied once) instead of readBytes(): avoids holding a second 98 MB copy on the heap.
        val file = java.io.File(context.filesDir, "separator.onnx")
        val expected = context.assets.openFd(SpeechModelPaths.SEPARATOR).use { it.length }
        if (!file.isFile || file.length() != expected) {
            context.assets.open(SpeechModelPaths.SEPARATOR).use { i -> file.outputStream().use { i.copyTo(it, 1 shl 20) } }
        }
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(maxOf(1, minOf(4, Runtime.getRuntime().availableProcessors() - 1)))
            setCPUArenaAllocator(false); setMemoryPatternOptimization(false)
        }
        session = env.createSession(file.absolutePath, opts)
    }

    /** Returns two source signals, each the same length as [mix]. */
    fun separate(mix: FloatArray): Array<FloatArray> {
        val n = mix.size
        val total = maxOf(n, WINDOW)
        val padded = mix.copyOf(total)
        val starts = ArrayList<Int>()
        var s = 0
        while (s + WINDOW < total) { starts += s; s += STRIDE }
        starts += total - WINDOW
        val acc = Array(2) { FloatArray(total) }
        val wsum = FloatArray(total)
        var prev: Array<FloatArray>? = null
        var prevStart = 0
        for (st in starts.distinct()) {
            var o = runWindow(padded.copyOfRange(st, st + WINDOW))
            prev?.let { p ->
                val ov = prevStart + WINDOW - st
                if (ov > 0) {
                    val off = st - prevStart
                    var same = 0.0; var swapped = 0.0
                    for (i in 0 until ov) {
                        same += p[0][off + i] * o[0][i] + p[1][off + i] * o[1][i]
                        swapped += p[0][off + i] * o[1][i] + p[1][off + i] * o[0][i]
                    }
                    if (swapped > same) o = arrayOf(o[1], o[0])
                }
            }
            for (i in 0 until WINDOW) {
                val w = 1f - kotlin.math.abs(2f * (i + 0.5f) / WINDOW - 1f) + 0.05f
                acc[0][st + i] += o[0][i] * w; acc[1][st + i] += o[1][i] * w; wsum[st + i] += w
            }
            prev = o; prevStart = st
        }
        val inRms = rms(mix)
        return Array(2) { k ->
            val y = FloatArray(n) { acc[k][it] / wsum[it] }
            val r = rms(y)
            if (r > 1e-6f) { val g = inRms / r; for (i in y.indices) y[i] *= g }
            y
        }
    }

    private fun runWindow(x: FloatArray): Array<FloatArray> {
        // A silent window (typical when a separated source is re-split) carries nothing to separate: skip the model run.
        if (rms(x) < 0.002f) return arrayOf(FloatArray(WINDOW), FloatArray(WINDOW))
        OnnxTensor.createTensor(env, FloatBuffer.wrap(x), longArrayOf(1, WINDOW.toLong())).use { t ->
            session.run(mapOf(session.inputNames.first() to t)).use { r ->
                val b = (r[0] as OnnxTensor).floatBuffer
                val all = FloatArray(b.remaining()).also { b.get(it) }
                return arrayOf(all.copyOfRange(0, WINDOW), all.copyOfRange(WINDOW, 2 * WINDOW))
            }
        }
    }

    private fun rms(x: FloatArray): Float { var e = 0.0; for (v in x) e += v.toDouble() * v; return sqrt(e / maxOf(1, x.size)).toFloat() }

    override fun close() = session.close()

    companion object { const val WINDOW = 32_000; const val STRIDE = 24_000 }
}
