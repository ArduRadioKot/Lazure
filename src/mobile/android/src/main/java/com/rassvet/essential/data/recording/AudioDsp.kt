package com.rassvet.essential.data.recording

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stateful, in-place DSP blocks. They run over PCM16 audio in 60 s float chunks ([DspChain]) so memory stays at
 * ~2 bytes per sample (plus one chunk) however long the recording is. Chunk size is a multiple of the 20 ms frame
 * so results equal whole-array processing.
 */

/** Stage 4: 2nd-order Butterworth high-pass (Q = 0.707) at 80 Hz, RBJ biquad, direct form I. */
class HighPassFilter(sampleRate: Int = 16_000, cutoffHz: Double = 80.0) {
    private val b0: Double; private val b1: Double; private val b2: Double; private val a1: Double; private val a2: Double
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    init {
        val w0 = 2 * PI * cutoffHz / sampleRate
        val alpha = sin(w0) / (2 * 0.7071)
        val c = cos(w0)
        val a0 = 1 + alpha
        b0 = ((1 + c) / 2) / a0; b1 = (-(1 + c)) / a0; b2 = b0
        a1 = (-2 * c) / a0; a2 = (1 - alpha) / a0
    }

    fun process(x: FloatArray) {
        for (i in x.indices) {
            val xi = x[i].toDouble()
            val y = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = xi; y2 = y1; y1 = y
            x[i] = y.toFloat()
        }
    }
}

/**
 * Stage 6: slow RMS normalisation toward a target level. Frames below a noise gate are not boosted,
 * so the gain follows speech instead of lifting silence; gain is smoothed and capped.
 */
class RmsNormalizer(private val sampleRate: Int = 16_000, targetDbfs: Double = -23.0, maxGainDb: Double = 18.0) {
    private val frame = sampleRate / 50 // 20 ms
    private val target = 10.0.pow(targetDbfs / 20)
    private val maxGain = 10.0.pow(maxGainDb / 20)
    private val gateRms = 10.0.pow(-50.0 / 20)
    private val tc = exp(-1.0 / (0.4 * 50)) // ~400 ms smoothing at 50 frames/s
    private var gain = 1.0

    fun process(x: FloatArray) {
        var i = 0
        while (i < x.size) {
            val end = minOf(i + frame, x.size)
            var e = 0.0
            for (k in i until end) e += x[k].toDouble() * x[k]
            val rms = sqrt(e / (end - i))
            if (rms > gateRms) {
                val want = (target / rms).coerceIn(1.0 / 4, maxGain)
                gain = tc * gain + (1 - tc) * want
            }
            for (k in i until end) x[k] = (x[k] * gain).toFloat()
            i = end
        }
    }
}

/** Stage 7: feed-forward compressor (threshold -14 dBFS, 3:1) followed by a hard ceiling at -0.5 dBFS. */
class CompressorLimiter(sampleRate: Int = 16_000, private val thresholdDb: Double = -14.0, private val ratio: Double = 3.0) {
    private val attack = exp(-1.0 / (0.005 * sampleRate))
    private val release = exp(-1.0 / (0.120 * sampleRate))
    private val ceiling = 10.0.pow(-0.5 / 20).toFloat()
    private var env = 0.0

    fun process(x: FloatArray) {
        for (i in x.indices) {
            val a = abs(x[i].toDouble())
            env = if (a > env) attack * env + (1 - attack) * a else release * env + (1 - release) * a
            val db = 20 * ln(max(env, 1e-6)) / ln(10.0)
            val over = db - thresholdDb
            val gainDb = if (over > 0) -over * (1 - 1 / ratio) else 0.0
            x[i] = (x[i] * 10.0.pow(gainDb / 20)).toFloat().coerceIn(-ceiling, ceiling)
        }
    }
}

object DspChain {
    const val CHUNK = 320 * 3000 // 60 s at 16 kHz, a whole number of 20 ms frames

    /** Runs [blocks] in order over [pcm] chunk by chunk and returns a new PCM16 array. */
    fun run(pcm: ShortArray, vararg blocks: (FloatArray) -> Unit): ShortArray {
        val out = ShortArray(pcm.size)
        val buf = FloatArray(minOf(CHUNK, pcm.size))
        var pos = 0
        while (pos < pcm.size) {
            val n = minOf(CHUNK, pcm.size - pos)
            val chunk = if (n == buf.size) buf else FloatArray(n)
            for (i in 0 until n) chunk[i] = pcm[pos + i] / 32768f
            for (b in blocks) b(chunk)
            for (i in 0 until n) out[pos + i] = (chunk[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            pos += n
        }
        return out
    }

    fun highPass(pcm: ShortArray): ShortArray = HighPassFilter().let { hp -> run(pcm, hp::process) }

    fun normalizeCompress(pcm: ShortArray): ShortArray {
        val n = RmsNormalizer(); val c = CompressorLimiter()
        return run(pcm, n::process, c::process)
    }

    /** Float copy of [from, to) for models that need FloatArray input. */
    fun floats(pcm: ShortArray, from: Int = 0, to: Int = pcm.size): FloatArray {
        val a = from.coerceIn(0, pcm.size); val b = to.coerceIn(a, pcm.size)
        return FloatArray(b - a) { pcm[a + it] / 32768f }
    }
}
