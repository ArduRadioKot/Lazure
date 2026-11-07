package com.rassvet.essential.data.recording

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stage 5: DTLN (Westhausen & Meyer, MIT) two-stage stateful noise suppression, 16 kHz.
 * Ported from the reference real_time_processing_onnx.py: 512-sample blocks, 128-sample shift.
 */
class DtlnDenoiser(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val s1: OrtSession
    private val s2: OrtSession
    private val state1Shape: LongArray
    private val state2Shape: LongArray

    init {
        fun load(path: String): OrtSession {
            val bytes = context.assets.open(path).use { it.readBytes() }
            val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(1) }
            return env.createSession(bytes, opts)
        }
        s1 = load(SpeechModelPaths.DTLN_1)
        s2 = load(SpeechModelPaths.DTLN_2)
        fun stateShape(s: OrtSession): LongArray =
            (s.inputInfo[s.inputNames.toList()[1]]!!.info as ai.onnxruntime.TensorInfo).shape.map { if (it <= 0) 1L else it }.toLongArray()
        state1Shape = stateShape(s1); state2Shape = stateShape(s2)
    }

    /**
     * Denoises PCM16 in place-of-copy: blocks are read from [pcm] and written into the returned array, so no float
     * copy of the whole recording is made. DTLN has a 384-sample algorithmic delay that is compensated here.
     */
    fun process(pcm: ShortArray, onProgress: (Float) -> Unit = {}): ShortArray {
        val n = pcm.size
        val delay = BLOCK - SHIFT
        val res = ShortArray(n)
        val inBuf = FloatArray(BLOCK); val outBuf = FloatArray(BLOCK)
        var st1 = FloatArray(state1Shape.fold(1L) { a, b -> a * b }.toInt())
        var st2 = FloatArray(state2Shape.fold(1L) { a, b -> a * b }.toInt())
        val in1 = s1.inputNames.toList(); val in2 = s2.inputNames.toList()
        val re = DoubleArray(BLOCK); val im = DoubleArray(BLOCK)
        val bins = BLOCK / 2 + 1
        val numBlocks = (n + delay + SHIFT - 1) / SHIFT
        for (idx in 0 until numBlocks) {
            System.arraycopy(inBuf, SHIFT, inBuf, 0, BLOCK - SHIFT)
            for (k in 0 until SHIFT) { val p = idx * SHIFT + k; inBuf[BLOCK - SHIFT + k] = if (p < n) pcm[p] / 32768f else 0f }
            for (k in 0 until BLOCK) { re[k] = inBuf[k].toDouble(); im[k] = 0.0 }
            Fft.transform(re, im, false)
            val mag = FloatArray(bins) { sqrt(re[it] * re[it] + im[it] * im[it]).toFloat() }
            val phase = DoubleArray(bins) { atan2(im[it], re[it]) }
            val mask: FloatArray
            OnnxTensor.createTensor(env, FloatBuffer.wrap(mag), longArrayOf(1, 1, bins.toLong())).use { t ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(st1), state1Shape).use { st ->
                    s1.run(mapOf(in1[0] to t, in1[1] to st)).use { r ->
                        mask = (r[0] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                        st1 = (r[1] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                    }
                }
            }
            for (k in 0 until bins) {
                val m = mag[k] * mask[k]
                re[k] = m * cos(phase[k]); im[k] = m * sin(phase[k])
            }
            for (k in 1 until BLOCK / 2) { re[BLOCK - k] = re[k]; im[BLOCK - k] = -im[k] }
            Fft.transform(re, im, true)
            val est = FloatArray(BLOCK) { re[it].toFloat() }
            val block: FloatArray
            OnnxTensor.createTensor(env, FloatBuffer.wrap(est), longArrayOf(1, 1, BLOCK.toLong())).use { t ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(st2), state2Shape).use { st ->
                    s2.run(mapOf(in2[0] to t, in2[1] to st)).use { r ->
                        block = (r[0] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                        st2 = (r[1] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                    }
                }
            }
            System.arraycopy(outBuf, SHIFT, outBuf, 0, BLOCK - SHIFT)
            java.util.Arrays.fill(outBuf, BLOCK - SHIFT, BLOCK, 0f)
            for (k in 0 until BLOCK) outBuf[k] += block[k]
            // outBuf[0 until SHIFT] is final output for stream position idx*SHIFT + k, i.e. input sample (that - delay)
            for (k in 0 until SHIFT) {
                val i = idx * SHIFT + k - delay
                if (i in 0 until n) res[i] = (outBuf[k] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }
            if (idx % 2000 == 0) onProgress(idx.toFloat() / numBlocks)
        }
        return res
    }

    override fun close() { s1.close(); s2.close() }

    companion object { const val BLOCK = 512; const val SHIFT = 128 }
}

/** Minimal iterative radix-2 FFT (power-of-two sizes). Inverse includes 1/N scaling. */
object Fft {
    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len * (if (inverse) 1 else -1)
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = i + k + len / 2
                    val xr = re[b] * cr - im[b] * ci; val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi; re[a] += xr; im[a] += xi
                    val t = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = t
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }
}
