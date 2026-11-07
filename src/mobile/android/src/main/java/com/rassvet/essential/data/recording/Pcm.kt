package com.rassvet.essential.data.recording

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mono PCM16 audio kept in a temporary file instead of on the heap. A recording of any length therefore costs the
 * same memory: only the spans the pipeline is working on (a minute of speech, a window for the VAD) are read back.
 */
class Pcm private constructor(private val file: File, val size: Int) : AutoCloseable {
    private val raf = RandomAccessFile(file, "r")

    /** Float copy of [from, to) (clamped), scaled to -1..1. */
    fun floats(from: Int = 0, to: Int = size): FloatArray {
        val a = from.coerceIn(0, size); val b = to.coerceIn(a, size)
        val out = FloatArray(b - a)
        var done = 0
        val buf = ByteBuffer.allocate(BLOCK * 2).order(ByteOrder.LITTLE_ENDIAN)
        while (done < out.size) {
            val n = minOf(BLOCK, out.size - done)
            buf.clear(); buf.limit(n * 2)
            synchronized(raf) { raf.seek((a + done) * 2L); raf.channel.read(buf, (a + done) * 2L) }
            buf.flip()
            for (i in 0 until buf.remaining() / 2) out[done + i] = buf.getShort(i * 2) / 32768f
            done += n
        }
        return out
    }

    fun shorts(from: Int, to: Int): ShortArray {
        val a = from.coerceIn(0, size); val b = to.coerceIn(a, size)
        val out = ShortArray(b - a)
        val buf = ByteBuffer.allocate(out.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        synchronized(raf) { raf.channel.read(buf, a * 2L) }
        buf.flip(); buf.asShortBuffer().get(out)
        return out
    }

    override fun close() { runCatching { raf.close() }; file.delete() }

    class Writer(private val file: File) : AutoCloseable {
        private val out = java.io.BufferedOutputStream(java.io.FileOutputStream(file), 1 shl 16)
        private val bytes = ByteArray(BLOCK * 2)
        var count = 0
            private set

        fun write(x: ShortArray, from: Int = 0, to: Int = x.size) {
            var i = from
            while (i < to) {
                val n = minOf(BLOCK, to - i)
                for (k in 0 until n) { val s = x[i + k].toInt(); bytes[2 * k] = s.toByte(); bytes[2 * k + 1] = (s shr 8).toByte() }
                out.write(bytes, 0, n * 2)
                i += n
            }
            count += to - from
        }

        fun write(s: Short) { out.write(s.toInt() and 0xff); out.write((s.toInt() shr 8) and 0xff); count++ }

        fun finish(): Pcm { out.flush(); out.close(); return Pcm(file, count) }
        override fun close() { runCatching { out.close() } }
    }

    companion object {
        private const val BLOCK = 1 shl 15
        fun writer(dir: File, name: String): Writer { dir.mkdirs(); return Writer(File(dir, name)) }
    }
}
