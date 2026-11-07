package com.rassvet.essential.data.recording

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.nio.ByteOrder
import kotlin.math.min

/** Models bundled in assets/models; the manifest lives in speech-decoder/models/manifest.json. */
object SpeechModelPaths {
    const val ASR_DIR = "models/asr-gigaam-v3-ru"
    const val ASR_MODEL = "$ASR_DIR/model.int8.onnx"
    const val ASR_TOKENS = "$ASR_DIR/tokens.txt"
    const val DTLN_1 = "models/denoise/dtln_1.onnx"
    const val DTLN_2 = "models/denoise/dtln_2.onnx"
    const val SEPARATOR = "models/separation/mossformer2_ss_16k_int8.onnx"
    const val VAD = "models/vad/silero_vad.onnx"
    const val SEGMENTATION = "models/diar-segmentation/model.int8.onnx"
    const val EMBEDDING = "models/diar-embedding/model.onnx"
    const val ASR_ID = "gigaam-v3-ctc-ru"
    const val DIARIZATION_ID = "pyannote-seg-3.0+campplus"
    const val SAMPLE_RATE = 16_000

    fun assetExists(context: Context, path: String): Boolean =
        runCatching { context.assets.open(path).close(); true }.getOrDefault(false)
}

/** MediaCodec based AudioDecoder + AudioPreprocessor: any Android-decodable file → mono 16 kHz PCM16. */
class MediaCodecAudioDecoder {
    suspend fun decode16kMono(path: String, onProgress: (Float) -> Unit = {}): ShortArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { extractor.release(); error("No audio track in file") }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val resampler = StreamingResampler()
        var out = ShortArray(SpeechModelPaths.SAMPLE_RATE * 60)
        var outLen = 0
        fun push(sample: Short) {
            if (outLen == out.size) out = out.copyOf(out.size * 2)
            out[outLen++] = sample
        }
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                            if (durationUs > 0) onProgress((extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f))
                            extractor.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    oi >= 0 -> {
                        val buf = codec.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder())
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        resampler.configure(srcRate)
                        if (floatPcm) {
                            val fb = buf.asFloatBuffer()
                            while (fb.remaining() >= channels) {
                                var s = 0f
                                repeat(channels) { s += fb.get() }
                                resampler.feed(s / channels) { push((it * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()) }
                            }
                        } else {
                            val sb = buf.asShortBuffer()
                            while (sb.remaining() >= channels) {
                                var s = 0
                                repeat(channels) { s += sb.get() }
                                resampler.feed(s.toFloat() / channels / 32768f) { push((it * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()) }
                            }
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); extractor.release()
        }
        require(outLen > 0) { "Decoded audio is empty" }
        return out.copyOf(outLen)
    }
}

/** Streaming resampler to 16 kHz: box-average when downsampling, linear interpolation when upsampling. */
private class StreamingResampler {
    private var ratio = 1.0          // source rate / 16000
    private var configuredRate = -1
    private var acc = 0.0
    private var accCount = 0
    private var pos = 0.0
    private var prev = 0f
    private var nextOut = 0.0        // upsampling: position of next output sample in source index units
    private var index = 0L
    fun configure(srcRate: Int) {
        if (srcRate == configuredRate) return
        configuredRate = srcRate
        ratio = srcRate.toDouble() / SpeechModelPaths.SAMPLE_RATE
        pos = 0.0; acc = 0.0; accCount = 0; prev = 0f; nextOut = 0.0; index = 0
    }
    inline fun feed(x: Float, emit: (Float) -> Unit) {
        if (ratio == 1.0) { emit(x); return }
        if (ratio > 1.0) {
            acc += x; accCount++; pos += 1.0
            if (pos >= ratio) { emit((acc / accCount).toFloat()); pos -= ratio; acc = 0.0; accCount = 0 }
        } else {
            // sample x sits at source index `index`; emit outputs whose position lies in (index-1, index]
            while (nextOut <= index) {
                val frac = (nextOut - (index - 1)).toFloat().coerceIn(0f, 1f)
                emit(if (index == 0L) x else prev + (x - prev) * frac)
                nextOut += ratio
            }
            prev = x; index++
        }
    }
}

/** Toggles for the enhancement chain (HPF is always on); used for A/B evaluation and the low-power setting. */
data class PipelineOptions(
    val denoise: Boolean = true,
    val normalize: Boolean = true,
    val separate: Boolean = true,
    /** Wall-clock cap for separation (it is ~4x slower than real time on the emulator); longest regions go first. */
    val maxSeparationWallMs: Long = 90_000L,
    /** Agglomerative-clustering distance threshold: lower splits voices more eagerly. */
    val diarThreshold: Float = 0.5f,
    /** Known number of speakers (-1 = detect automatically). Forces the clusterer to split/merge to exactly N. */
    val numSpeakers: Int = -1,
    /** Re-label each sentence from its own speaker embedding (fixes late/merged turns from the diarizer). */
    val refineSpeakers: Boolean = true,
    /** Cosine similarity above which two sentences are the same speaker. */
    val sameSpeakerSimilarity: Float = 0.7f,
)

/** Matches separated sources to diarization speakers with speaker embeddings (CAM++), falling back to energy. */
internal class SpeakerMatcher(context: Context) : AutoCloseable {
    private val extractor = com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor(context.assets,
        SpeakerEmbeddingExtractorConfig(model = SpeechModelPaths.EMBEDDING, numThreads = 2))

    fun embed(x: FloatArray): FloatArray? {
        if (x.size < SpeechModelPaths.SAMPLE_RATE) return null          // < 1 s is not reliable
        val st = extractor.createStream()
        return try {
            st.acceptWaveform(x, SpeechModelPaths.SAMPLE_RATE); st.inputFinished()
            if (extractor.isReady(st)) extractor.compute(st) else null
        } finally { st.release() }
    }

    override fun close() = extractor.release()

    companion object {
        fun cosine(a: FloatArray, b: FloatArray): Double {
            var d = 0.0; var na = 0.0; var nb = 0.0
            for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            return d / (Math.sqrt(na * nb) + 1e-9)
        }
    }
}

data class PipelineSegment(val startMs: Long, val endMs: Long, val speaker: Int, val text: String, val overlap: Boolean = false)

/** VAD → ASR → diarization → timestamp alignment. Everything runs on-device through sherpa-onnx. */
class SpeechPipeline(private val context: Context) {
    private val decoder = MediaCodecAudioDecoder()

    fun modelsAvailable(): Boolean = listOf(
        SpeechModelPaths.ASR_MODEL, SpeechModelPaths.ASR_TOKENS, SpeechModelPaths.VAD,
        SpeechModelPaths.SEGMENTATION, SpeechModelPaths.EMBEDDING,
    ).all { SpeechModelPaths.assetExists(context, it) }

    suspend fun run(
        audioPath: String,
        onProgress: (stage: String, fraction: Float) -> Unit,
        isCancelled: () -> Boolean = { false },
        options: PipelineOptions = PipelineOptions(),
    ): List<PipelineSegment> {
        check(modelsAvailable()) { "Speech models are missing from the APK" }
        onProgress("decode", 0f)
        var pcm: ShortArray? = decoder.decode16kMono(audioPath) { onProgress("decode", it) }
        android.util.Log.i("SpeechPipeline", "decoded ${pcm!!.size} samples (${pcm!!.size * 2 / 1_000_000} MB PCM16)")

        onProgress("enhance", 0f)
        val (samples, vadSignal, diarSignal) = enhance(pcm!!, options) { onProgress("enhance", it) }
        pcm = null   // release the decoded copy; only the three processed signals stay (PCM16)

        onProgress("vad", 0f)
        val utterances = detectSpeech(vadSignal, samples)
        android.util.Log.i("SpeechPipeline", "vad segments=${utterances.size}")
        if (utterances.isEmpty()) return emptyList()

        var recognizer: OfflineRecognizer? = newRecognizer()
        try {
            // Stage 11 (main pass): GigaAM on every VAD segment. Tokens carry absolute timestamps.
            val tokens = ArrayList<Tok>()   // speaker = -1 until diarization is known
            val noTimestamps = ArrayList<PipelineSegment>()
            utterances.forEachIndexed { i, seg ->
                if (isCancelled()) error("cancelled")
                val r = recognizeSamples(recognizer!!, seg.samples)
                val startMs = seg.start * 1000 / SpeechModelPaths.SAMPLE_RATE
                val durMs = seg.samples.size * 1000L / SpeechModelPaths.SAMPLE_RATE
                android.util.Log.i("SpeechPipeline", "asr[$i] ${seg.samples.size} -> '${r.text.trim()}'")
                if (r.text.isNotBlank()) {
                    if (r.tokens.isNotEmpty() && r.timestamps.size == r.tokens.size)
                        r.tokens.forEachIndexed { k, t -> tokens += Tok(startMs + (r.timestamps[k] * 1000).toLong(), t, -1) }
                    else noTimestamps += PipelineSegment(startMs, startMs + maxOf(durMs, 100), -1, r.text.trim())
                }
                onProgress("asr", (i + 1f) / utterances.size)
            }
            if (tokens.isEmpty() && noTimestamps.isEmpty()) return emptyList()

            onProgress("diarization", 0f)
            val turns = diarize(DspChain.floats(diarSignal), options.diarThreshold, options.numSpeakers) { onProgress("diarization", it) }
            val regions = overlapRegions(turns)
            val overlapS = overlapSeconds(turns)
            android.util.Log.i("SpeechPipeline", "speakers=${turns.map { it.speaker }.distinct().size} overlapped=${"%.1f".format(overlapS)}s regions=${regions.size} of ${samples.size / 16000}s")

            // Stage 10: separate overlapped regions and recognise each source separately.
            val replaced = ArrayList<LongRange>()
            val separated = ArrayList<Tok>()
            if (options.separate && regions.isNotEmpty() && SpeechModelPaths.assetExists(context, SpeechModelPaths.SEPARATOR)) {
                // Peak-memory control: the ASR model is released while the (large) separator runs, then reloaded.
                recognizer!!.release(); recognizer = null
                val sources = ArrayList<SeparatedRegion>()
                runCatching {
                    val t0 = android.os.SystemClock.elapsedRealtime()
                    val refs = runCatching { SpeakerMatcher(context) }.getOrNull()?.use { m -> referenceEmbeddings(m, samples, turns, regions) } ?: emptyMap()
                    val matcher = runCatching { SpeakerMatcher(context) }.getOrNull()
                    try {
                        OverlapSeparator(context).use { sep ->
                            var skipped = 0
                            // Longest overlaps first: they hold the most lost words, and the wall-clock cap drops the rest.
                            regions.sortedByDescending { it.endMs - it.startMs }.forEachIndexed { idx, r ->
                                if (isCancelled()) error("cancelled")
                                if (android.os.SystemClock.elapsedRealtime() - t0 > options.maxSeparationWallMs) { skipped++; return@forEachIndexed }
                                separateRegion(sep, samples, turns, r, matcher, refs)?.let { sources += it }
                                onProgress("separation", (idx + 1f) / regions.size * 0.8f)
                            }
                            if (skipped > 0) android.util.Log.w("SpeechPipeline", "separation time cap reached, $skipped region(s) left on the mixed transcript")
                        }
                    } finally { matcher?.close() }
                }.onFailure { android.util.Log.e("SpeechPipeline", "separation failed, using mixed transcript", it) }
                recognizer = newRecognizer()
                for (sr in sources) {
                    separated += recognizeSeparated(recognizer!!, sr)
                    replaced += sr.region.startMs..sr.region.endMs
                }
                onProgress("separation", 1f)
            }

            onProgress("align", 0f)
            // Sentence-level speaker refinement: pyannote turn boundaries lag at speaker changes and its clusterer
            // can merge similar voices, so every sentence is re-labelled from its own CAM++ embedding.
            val units = if (options.refineSpeakers) {
                runCatching { refineSpeakers(samples, tokens.filter { t -> replaced.none { t.ms in it } }, turns, options) }
                    .onFailure { android.util.Log.e("SpeechPipeline", "speaker refinement failed", it) }.getOrNull()
            } else null
            val all = ArrayList<Tok>()
            for (t in tokens) {
                if (replaced.any { t.ms in it }) continue            // words inside a separated region come from the separated streams
                all += t.copy(speaker = units?.let { u -> speakerFromUnits(u, t.ms) } ?: speakerAt(turns, t.ms))
            }
            all += separated
            val out = groupTokens(all)
            noTimestamps.forEach { out += it.copy(speaker = dominantSpeaker(turns, it.startMs, it.endMs)) }
            val merged = mergeAdjacent(out.filter { it.text.isNotBlank() }.sortedBy { it.startMs }, durationLimitMs = audioDurationMs(samples))
            return markOverlaps(merged, regions)
        } finally { recognizer?.release() }
    }

    private fun newRecognizer() = OfflineRecognizer(context.assets, OfflineRecognizerConfig(
        modelConfig = OfflineModelConfig(
            nemo = OfflineNemoEncDecCtcModelConfig(model = SpeechModelPaths.ASR_MODEL),
            tokens = SpeechModelPaths.ASR_TOKENS, numThreads = threads(), modelType = "nemo_ctc",
        ),
    ))

    private data class SeparatedRegion(val region: Region, val windowStartMs: Long, val owner: IntArray, val src: Array<FloatArray>)

    /** A segment is an interruption when another speaker is active in it: separated streams or a diarizer overlap region. */
    private fun markOverlaps(segs: List<PipelineSegment>, regions: List<Region>): List<PipelineSegment> =
        segs.map { s ->
            val withOther = segs.any { o -> o !== s && o.speaker != s.speaker && minOf(o.endMs, s.endMs) - maxOf(o.startMs, s.startMs) > 250 }
            val inRegion = regions.any { r -> minOf(r.endMs, s.endMs) - maxOf(r.startMs, s.startMs) > 250 }
            if (withOther || inRegion) s.copy(overlap = true) else s
        }

    private data class Tok(val ms: Long, val text: String, val speaker: Int)
    private data class SpeakerUnit(val startMs: Long, val endMs: Long, val speaker: Int)

    private fun speakerFromUnits(units: List<SpeakerUnit>, ms: Long): Int? {
        var lo = 0; var hi = units.size - 1; var best: Int? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1; val u = units[mid]
            if (ms < u.startMs) hi = mid - 1 else if (ms > u.endMs) lo = mid + 1 else { best = u.speaker; break }
        }
        if (best != null) return best
        // between units: nearest unit in time
        val i = lo.coerceIn(0, units.size - 1)
        val a = units.getOrNull(i - 1); val b = units.getOrNull(i)
        return listOfNotNull(a, b).minByOrNull { minOf(kotlin.math.abs(it.startMs - ms), kotlin.math.abs(it.endMs - ms)) }?.speaker
    }

    /**
     * Splits the transcript into sentence units (sentence punctuation or >0.35 s pause), embeds each unit (>= 1 s),
     * clusters the embeddings online around centroids, then maps clusters to diarizer speaker ids by overlap so ids stay
     * consistent with the overlap/separation stage. Returns units sorted by time.
     */
    private fun refineSpeakers(
        samples: ShortArray, tokens: List<Tok>,
        turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>, options: PipelineOptions,
    ): List<SpeakerUnit>? {
        if (tokens.size < 2) return null
        val sorted = tokens.sortedBy { it.ms }
        // 1) sentence units
        data class Raw(var start: Long, var end: Long)
        val raws = ArrayList<Raw>()
        var cur: Raw? = null
        for ((i, t) in sorted.withIndex()) {
            val c = cur
            if (c == null) cur = Raw(t.ms, t.ms + 160) else { c.end = t.ms + 160 }
            val body = t.text.replace("▁", "").trim()
            val endsSentence = body.isNotEmpty() && body.last() in ".?!"
            val gap = if (i + 1 < sorted.size) sorted[i + 1].ms - (t.ms + 160) else 0L
            if (endsSentence || gap > 350) { raws += cur!!; cur = null }
        }
        cur?.let { raws += it }
        // 2) merge units shorter than 1.2 s into the previous one when the pause is small
        val units = ArrayList<Raw>()
        for (r in raws) {
            val p = units.lastOrNull()
            if (p != null && (r.end - r.start < 1000 || p.end - p.start < 1000) && r.start - p.end < 600) p.end = r.end else units += Raw(r.start, r.end)
        }
        if (units.size < 2) return null
        val matcher = SpeakerMatcher(context)
        try {
            val emb = units.map { u ->
                val from = (u.start * 16).toInt(); val to = minOf((u.end * 16).toInt(), from + 15 * 16000)
                matcher.embed(DspChain.floats(samples, from, to))?.also { e -> val n = Math.sqrt(e.sumOf { (it * it).toDouble() }).toFloat() + 1e-9f; for (k in e.indices) e[k] /= n }
            }
            // 3) online clustering with centroids, then two refinement passes
            val maxSpk = if (options.numSpeakers > 0) options.numSpeakers else 8
            val cents = ArrayList<FloatArray>(); val counts = ArrayList<Int>(); val label = IntArray(units.size) { -1 }
            fun dot(a: FloatArray, b: FloatArray): Float { var d = 0f; for (i in a.indices) d += a[i] * b[i]; return d }
            fun norm(v: FloatArray): FloatArray { val n = Math.sqrt(v.sumOf { (it * it).toDouble() }).toFloat() + 1e-9f; return FloatArray(v.size) { v[it] / n } }
            for ((i, e) in emb.withIndex()) {
                if (e == null) continue
                var best = -1; var bs = -2f
                for (k in cents.indices) { val s = dot(cents[k], e); if (s > bs) { bs = s; best = k } }
                if (best >= 0 && (bs >= options.sameSpeakerSimilarity || cents.size >= maxSpk)) {
                    label[i] = best; counts[best]++
                    cents[best] = norm(FloatArray(e.size) { cents[best][it] * (counts[best] - 1) + e[it] })
                } else { cents += e.copyOf(); counts += 1; label[i] = cents.size - 1 }
            }
            repeat(2) {
                val sums = cents.map { FloatArray(it.size) }; val n = IntArray(cents.size)
                for ((i, e) in emb.withIndex()) {
                    if (e == null) continue
                    var best = 0; var bs = -2f
                    for (k in cents.indices) { val s = dot(cents[k], e); if (s > bs) { bs = s; best = k } }
                    label[i] = best; for (d in e.indices) sums[best][d] += e[d]; n[best]++
                }
                for (k in cents.indices) if (n[k] > 0) cents[k] = norm(sums[k])
            }
            // fixed speaker count: merge closest centroids until k remain
            if (options.numSpeakers > 0) while (cents.map { c -> label.count { it == cents.indexOf(c) } }.count { it > 0 } > options.numSpeakers) {
                val live = cents.indices.filter { k -> label.any { it == k } }
                var bi = -1; var bj = -1; var bs = -2f
                for (a in live) for (b in live) if (a < b) { val s = dot(cents[a], cents[b]); if (s > bs) { bs = s; bi = a; bj = b } }
                for (i in label.indices) if (label[i] == bj) label[i] = bi
            }
            // units without an embedding take the label of the nearest labelled neighbour
            for (i in label.indices) if (label[i] < 0) {
                label[i] = (1..label.size).firstNotNullOfOrNull { d -> label.getOrNull(i - d)?.takeIf { it >= 0 } ?: label.getOrNull(i + d)?.takeIf { it >= 0 } } ?: 0
            }
            // 4) map clusters to diarizer ids by overlap (largest clusters first), new ids for the rest
            val clusterIds = label.distinct()
            val size = clusterIds.associateWith { c -> units.indices.filter { label[it] == c }.sumOf { units[it].end - units[it].start } }
            val used = HashSet<Int>(); val mapping = HashMap<Int, Int>()
            var nextId = (turns.maxOfOrNull { it.speaker } ?: -1) + 1
            for (c in clusterIds.sortedByDescending { size[it] }) {
                val votes = HashMap<Int, Double>()
                for (i in units.indices) if (label[i] == c) for (t in turns) {
                    val o = minOf(units[i].end / 1000f, t.end) - maxOf(units[i].start / 1000f, t.start)
                    if (o > 0) votes.merge(t.speaker, o.toDouble(), Double::plus)
                }
                val pick = votes.entries.sortedByDescending { it.value }.firstOrNull { it.key !in used }?.key ?: nextId++
                used += pick; mapping[c] = pick
            }
            android.util.Log.i("SpeechPipeline", "refined speakers: ${clusterIds.size} clusters from ${units.size} sentences, mapping=$mapping")
            return units.indices.map { SpeakerUnit(units[it].start, units[it].end, mapping[label[it]] ?: 0) }
        } finally { matcher.close() }
    }
    private data class Region(val startMs: Long, val endMs: Long, val a: Int, val b: Int)

    private fun recognizeSamples(rec: OfflineRecognizer, x: FloatArray): com.k2fsa.sherpa.onnx.OfflineRecognizerResult {
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(x, SpeechModelPaths.SAMPLE_RATE)
            rec.decode(stream)
            return rec.getResult(stream)
        } finally { stream.release() }
    }

    /** Groups tokens per speaker (so simultaneous speakers do not interleave): break on >1 s silence or 30 s length. */
    private fun groupTokens(all: List<Tok>): ArrayList<PipelineSegment> {
        val out = ArrayList<PipelineSegment>()
        for ((spk, toks) in all.groupBy { it.speaker }) {
            val sorted = toks.sortedBy { it.ms }
            var start = 0L; var end = 0L; val sb = StringBuilder()
            fun flush() {
                val t = sb.toString().replace('▁', ' ').trim()
                if (t.isNotEmpty()) out += PipelineSegment(start, maxOf(end, start + 100), spk, t)
                sb.clear()
            }
            for (t in sorted) {
                if (sb.isEmpty()) start = t.ms
                else if (t.ms - end > 1000 || t.ms - start > 30_000) { flush(); start = t.ms }
                sb.append(t.text); end = t.ms + 80
            }
            flush()
        }
        return out
    }

    /** Spans where two or more different speakers are active, merged across gaps < 300 ms, at least 400 ms long. */
    private fun overlapRegions(turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>): List<Region> {
        data class Ev(val t: Float, val spk: Int, val d: Int)
        val ev = turns.flatMap { listOf(Ev(it.start, it.speaker, 1), Ev(it.end, it.speaker, -1)) }.sortedWith(compareBy({ it.t }, { it.d }))
        val active = HashMap<Int, Int>()
        val raw = ArrayList<Region>()
        var last = 0f
        for (e in ev) {
            val on = active.filterValues { it > 0 }.keys
            if (on.size >= 2 && e.t > last) {
                val dom = on.sortedBy { it }.take(2)
                raw += Region((last * 1000).toLong(), (e.t * 1000).toLong(), dom[0], dom[1])
            }
            active.merge(e.spk, e.d, Int::plus); last = e.t
        }
        val merged = ArrayList<Region>()
        for (r in raw) {
            val p = merged.lastOrNull()
            if (p != null && p.a == r.a && p.b == r.b && r.startMs - p.endMs < 300) merged[merged.lastIndex] = p.copy(endMs = r.endMs) else merged += r
        }
        return merged.filter { it.endMs - it.startMs >= 600 }
    }

    /** Separates one overlapped region and returns word tokens for both speakers (clipped to the region), or null. */
    private fun separateRegion(
        sep: OverlapSeparator, samples: ShortArray,
        turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>, r: Region,
        matcher: SpeakerMatcher?, refs: Map<Int, FloatArray>,
    ): SeparatedRegion? {
        val margin = 250L
        val s0 = ((r.startMs - margin).coerceAtLeast(0) * 16).toInt()
        val s1 = (((r.endMs + margin) * 16).toInt()).coerceAtMost(samples.size)
        if (s1 - s0 < 1600) return null
        val mix = DspChain.floats(samples, s0, s1)
        val src = sep.separate(mix)
        // Which source is speaker A? 1) embeddings vs per-speaker reference from their non-overlapped speech,
        // 2) energy in exclusive speech, 3) arbitrary order.
        var owner = intArrayOf(r.a, r.b)
        var method = "none"; var matchMargin = 0.0
        val ra = refs[r.a]; val rb = refs[r.b]
        val e0 = matcher?.embed(src[0]); val e1 = matcher?.embed(src[1])
        if (e0 != null && e1 != null && refs.isNotEmpty()) {
            // Best distinct pair of speakers over ALL references, so a wrongly labelled overlap pair is corrected.
            var best = Double.NEGATIVE_INFINITY; var bi = -1; var bj = -1
            for ((i, ri) in refs) for ((j, rj) in refs) {
                if (i == j) continue
                val sc = SpeakerMatcher.cosine(e0, ri) + SpeakerMatcher.cosine(e1, rj)
                if (sc > best) { best = sc; bi = i; bj = j }
            }
            var labelled = Double.NEGATIVE_INFINITY
            if (ra != null && rb != null) labelled = maxOf(SpeakerMatcher.cosine(e0, ra) + SpeakerMatcher.cosine(e1, rb), SpeakerMatcher.cosine(e0, rb) + SpeakerMatcher.cosine(e1, ra))
            if (bi >= 0 && (labelled == Double.NEGATIVE_INFINITY || best > labelled + 0.05)) {
                owner = intArrayOf(bi, bj); method = "embedding-global"; matchMargin = best - (if (labelled.isInfinite()) 0.0 else labelled)
            } else if (ra != null && rb != null) {
                val same = SpeakerMatcher.cosine(e0, ra) + SpeakerMatcher.cosine(e1, rb)
                val swap = SpeakerMatcher.cosine(e0, rb) + SpeakerMatcher.cosine(e1, ra)
                matchMargin = Math.abs(same - swap)
                if (matchMargin > 0.02) { owner = if (swap > same) intArrayOf(r.b, r.a) else intArrayOf(r.a, r.b); method = "embedding" }
            } else if (ra != null || rb != null) {
                val (only, who) = if (ra != null) ra to r.a else rb!! to r.b
                val c0 = SpeakerMatcher.cosine(e0, only); val c1 = SpeakerMatcher.cosine(e1, only)
                matchMargin = Math.abs(c0 - c1)
                if (matchMargin > 0.02) { val other = if (who == r.a) r.b else r.a; owner = if (c0 > c1) intArrayOf(who, other) else intArrayOf(other, who); method = "embedding-one-ref" }
            }
        }
        if (method == "none") {
            fun energy(x: FloatArray, only: Int, other: Int): Double {
                var e = 0.0; var c = 0
                for (i in x.indices) {
                    val t = (s0 + i) / 16000f
                    val onlyOn = turns.any { it.speaker == only && t >= it.start && t <= it.end }
                    val otherOn = turns.any { it.speaker == other && t >= it.start && t <= it.end }
                    if (onlyOn && !otherOn) { e += x[i].toDouble() * x[i]; c++ }
                }
                return if (c > 0) e / c else -1.0
            }
            val a0 = energy(src[0], r.a, r.b); val a1 = energy(src[1], r.a, r.b)
            val b0 = energy(src[0], r.b, r.a); val b1 = energy(src[1], r.b, r.a)
            var same = 0.0; var swap = 0.0
            if (a0 >= 0 && a1 >= 0) { same += a0 / (a0 + a1 + 1e-9); swap += a1 / (a0 + a1 + 1e-9) }
            if (b0 >= 0 && b1 >= 0) { same += b1 / (b0 + b1 + 1e-9); swap += b0 / (b0 + b1 + 1e-9) }
            matchMargin = Math.abs(same - swap)
            if (matchMargin > 0.05) { owner = if (swap > same) intArrayOf(r.b, r.a) else intArrayOf(r.a, r.b); method = "energy" }
        }
        android.util.Log.i("SpeechPipeline", "separated ${r.startMs}-${r.endMs}ms spk ${r.a}/${r.b} match=$method matchMargin=${"%.2f".format(matchMargin)}")
        return SeparatedRegion(r, s0 / 16L, owner, src)
    }

    /** Per-speaker reference embedding from up to 12 s of that speaker's speech outside every overlap region. */
    private fun referenceEmbeddings(
        m: SpeakerMatcher, samples: ShortArray,
        turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>, regions: List<Region>,
    ): Map<Int, FloatArray> {
        val needed = turns.map { it.speaker }.toSet()   // all speakers: diarization may label an overlap with the wrong pair
        val out = HashMap<Int, FloatArray>()
        for (spk in needed) {
            val chunks = ArrayList<FloatArray>(); var total = 0
            for (t in turns.filter { it.speaker == spk }.sortedByDescending { it.end - it.start }) {
                val a = (t.start * 1000).toLong(); val b = (t.end * 1000).toLong()
                if (regions.any { a < it.endMs && b > it.startMs }) continue          // clean turns only
                if (turns.any { it.speaker != spk && it.start < t.end && it.end > t.start }) continue
                val seg = DspChain.floats(samples, (t.start * 16000).toInt(), (t.end * 16000).toInt())
                chunks += seg; total += seg.size
                if (total >= 12 * 16000) break
            }
            if (total < 16000) continue
            val joined = FloatArray(total); var o = 0
            chunks.forEach { System.arraycopy(it, 0, joined, o, it.size); o += it.size }
            m.embed(joined)?.let { out[spk] = it }
        }
        android.util.Log.i("SpeechPipeline", "reference embeddings for speakers ${out.keys}")
        return out
    }

    private fun recognizeSeparated(rec: OfflineRecognizer, sr: SeparatedRegion): List<Tok> {
        val out = ArrayList<Tok>()
        for (k in 0..1) {
            val res = recognizeSamples(rec, sr.src[k])
            if (res.tokens.isEmpty() || res.timestamps.size != res.tokens.size) continue
            res.tokens.forEachIndexed { i, t ->
                val ms = sr.windowStartMs + (res.timestamps[i] * 1000).toLong()
                if (ms in sr.region.startMs..sr.region.endMs) out += Tok(ms, t, sr.owner[k])
            }
        }
        return out
    }

    /**
     * Stages 4–7 on PCM16, in 60 s chunks (constant extra memory). Returns (asrSignal, vadSignal, diarSignal):
     * HPF 80 Hz → RMS normalisation → compressor/limiter for ASR, plus the same chain after DTLN for VAD.
     * Measured on emulator clips, DTLN artefacts make GigaAM worse, so the denoised signal only drives speech
     * detection. Diarization gets the HPF-only signal: speaker embeddings over-split (4 clusters instead of 2 on the
     * test clip) after loudness normalisation and compression.
     */
    private fun enhance(pcm: ShortArray, o: PipelineOptions, onProgress: (Float) -> Unit): Triple<ShortArray, ShortArray, ShortArray> {
        val hp = DspChain.highPass(pcm)
        val asr = if (o.normalize) DspChain.normalizeCompress(hp) else hp
        if (!o.denoise || !SpeechModelPaths.assetExists(context, SpeechModelPaths.DTLN_1)) return Triple(asr, asr, hp)
        val den = runCatching { DtlnDenoiser(context).use { it.process(hp, onProgress) } }
            .onFailure { android.util.Log.e("SpeechPipeline", "DTLN failed, continuing without it", it) }
            .getOrNull() ?: return Triple(asr, asr, hp)
        return Triple(asr, if (o.normalize) DspChain.normalizeCompress(den) else den, hp)
    }

    private fun audioDurationMs(samples: ShortArray) = samples.size * 1000L / SpeechModelPaths.SAMPLE_RATE

    private fun threads() = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)

    /** VAD runs on [vadSignal]; returned segments carry audio cut from [asrSignal] at the same sample positions. */
    private fun detectSpeech(vadSignal: ShortArray, asrSignal: ShortArray): List<com.k2fsa.sherpa.onnx.SpeechSegment> {
        val vad = Vad(context.assets, VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = SpeechModelPaths.VAD, threshold = 0.5f, minSilenceDuration = 0.4f,
                minSpeechDuration = 0.25f, windowSize = 512, maxSpeechDuration = 25f,
            ),
            sampleRate = SpeechModelPaths.SAMPLE_RATE, numThreads = 1,
        ))
        val result = ArrayList<com.k2fsa.sherpa.onnx.SpeechSegment>()
        try {
            val window = 512
            fun drain() {
                while (!vad.empty()) {
                    val f = vad.front()
                    val st = f.start.toInt().coerceIn(0, asrSignal.size)
                    result += com.k2fsa.sherpa.onnx.SpeechSegment(f.start, DspChain.floats(asrSignal, st, st + f.samples.size))
                    vad.pop()
                }
            }
            var i = 0
            while (i < vadSignal.size) {
                val end = min(i + window, vadSignal.size)
                vad.acceptWaveform(DspChain.floats(vadSignal, i, end))
                drain()
                i = end
            }
            vad.flush()
            drain()
        } finally { vad.release() }
        return result
    }

    private fun diarize(samples: FloatArray, threshold: Float, numSpeakers: Int, onProgress: (Float) -> Unit): List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment> {
        val diar = OfflineSpeakerDiarization(context.assets, OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = SpeechModelPaths.SEGMENTATION),
                numThreads = threads(),
            ),
            embedding = SpeakerEmbeddingExtractorConfig(model = SpeechModelPaths.EMBEDDING, numThreads = threads()),
            clustering = FastClusteringConfig(numClusters = numSpeakers, threshold = threshold),
            minDurationOn = 0.15f, minDurationOff = 0.3f,
        ))
        android.util.Log.i("SpeechPipeline", "diarize threshold=$threshold numSpeakers=$numSpeakers")
        return try {
            onProgress(0f)
            diar.process(samples).toList().also { onProgress(1f) }
        } finally { diar.release() }
    }

    /** Total time during which two or more diarization turns of different speakers are active (overlapped speech). */
    private fun overlapSeconds(turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>): Float {
        val ev = turns.flatMap { listOf(it.start to 1, it.end to -1) }.sortedWith(compareBy({ it.first }, { it.second }))
        var active = 0; var last = 0f; var total = 0f
        for ((t, d) in ev) { if (active >= 2) total += t - last; active += d; last = t }
        return total
    }

    private fun speakerAt(turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>, tMs: Long): Int {
        val t = tMs / 1000f
        turns.firstOrNull { t >= it.start && t <= it.end }?.let { return it.speaker }
        return turns.minByOrNull { minOf(kotlin.math.abs(it.start - t), kotlin.math.abs(it.end - t)) }?.speaker ?: 0
    }

    private fun dominantSpeaker(turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>, startMs: Long, endMs: Long): Int {
        val s = startMs / 1000f; val e = endMs / 1000f
        val overlap = HashMap<Int, Float>()
        turns.forEach { val o = minOf(e, it.end) - maxOf(s, it.start); if (o > 0) overlap.merge(it.speaker, o, Float::plus) }
        return overlap.maxByOrNull { it.value }?.key ?: speakerAt(turns, (startMs + endMs) / 2)
    }

    /** Merges close same-speaker segments; segments of different speakers may overlap in time, same-speaker ones never do. */
    private fun mergeAdjacent(list: List<PipelineSegment>, durationLimitMs: Long): List<PipelineSegment> {
        val merged = ArrayList<PipelineSegment>()
        val lastIdx = HashMap<Int, Int>()
        for (s in list) {
            val pi = lastIdx[s.speaker]
            val prev = pi?.let { merged[it] }
            if (prev != null && s.startMs - prev.endMs < 600 && s.endMs - prev.startMs < 30_000) {
                merged[pi] = prev.copy(endMs = maxOf(prev.endMs, s.endMs), text = prev.text + " " + s.text)
            } else { merged += s; lastIdx[s.speaker] = merged.lastIndex }
        }
        val cursor = HashMap<Int, Long>()
        return merged.map { s ->
            val st = maxOf(s.startMs, cursor[s.speaker] ?: 0L)
            val en = minOf(maxOf(s.endMs, st + 100), maxOf(durationLimitMs, st + 100))
            cursor[s.speaker] = en; s.copy(startMs = st, endMs = en)
        }.sortedBy { it.startMs }
    }
}
