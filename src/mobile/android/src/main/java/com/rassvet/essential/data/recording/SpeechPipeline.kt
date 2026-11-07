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
    /** Share of the DTLN output in the signal that drives speech detection (the rest is the unprocessed signal). 1 = full denoise. */
    val denoiseStrength: Float = 0.7f,
    /** Silero speech threshold; lower catches quieter words and word onsets. */
    val vadThreshold: Float = 0.45f,
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
    /** Cosine similarity above which two speaker centroids are merged afterwards (over-splitting guard). */
    val mergeSimilarity: Float = 0.75f,
    /** A tiny cluster is absorbed into its nearest speaker when at least this similar. */
    val absorbSimilarity: Float = 0.35f,
    /** A weak cluster (< 4 s) is folded into another one at least this similar even when it is not tiny. */
    val weakClusterSimilarity: Float = 0.5f,
    /** A reply shorter than a second takes the closest speaker only if at least this similar; else the diarizer decides. */
    val shortUnitSimilarity: Float = 0.45f,
    /** Weight of the diarizer-id purity prior when labelling short replies. */
    val diarizerPriorWeight: Float = 0.3f,
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

    /** Same as [embed] but accepts replies from 0.5 s (less reliable). */
    fun embedShort(x: FloatArray): FloatArray? {
        if (x.size < SpeechModelPaths.SAMPLE_RATE / 2) return null
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
    private companion object { const val MAX_SIMULTANEOUS = 4 }

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
        val utterances = detectSpeech(vadSignal, samples, options.vadThreshold)
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
            val diarRegions = overlapRegions(turns)
            if (samples.size <= 120 * 16000) android.util.Log.i("SpeechPipeline", "turns: " + turns.sortedBy { it.start }.joinToString(" ") { "${it.speaker}:${"%.1f".format(it.start)}-${"%.1f".format(it.end)}" })
            val overlapS = overlapSeconds(turns)
            android.util.Log.i("SpeechPipeline", "speakers=${turns.map { it.speaker }.distinct().size} overlapped=${"%.1f".format(overlapS)}s regions=${diarRegions.size} of ${samples.size / 16000}s")

            // Speakers first. The diarizer tends to over-split (4 clusters for 2 people on the English test clip), so every
            // sentence is re-labelled from its own CAM++ embedding and the clusters are merged/absorbed (see refineSpeakers).
            // Words inside overlap regions are blends of voices and are left out of the embeddings.
            onProgress("speakers", 0f)
            val refined = if (options.refineSpeakers) {
                runCatching { refineSpeakers(samples, tokens.filter { t -> diarRegions.none { t.ms in it.startMs..it.endMs } }, options, turns.map { it.speaker }.distinct().size, turns, utterances.map { it.start * 1000 / SpeechModelPaths.SAMPLE_RATE }) }
                    .onFailure { android.util.Log.e("SpeechPipeline", "speaker refinement failed", it) }.getOrNull()
            } else null
            val units = refined?.units
            // One id space from here on: diarizer ids are translated to final ids. An "overlap" whose speakers collapse into
            // one person is the diarizer's over-split, not simultaneous speech, so it is dropped.
            val d2f: Map<Int, Int> = if (units != null) diarizerToFinal(turns, units) else turns.map { it.speaker }.distinct().associateWith { it }
            val regions = if (units == null) diarRegions else diarRegions.mapNotNull { r ->
                val f = r.spk.mapNotNull { d2f[it] }.distinct()
                if (f.size >= 2) r.copy(spk = f) else null
            }
            if (regions.size != diarRegions.size) android.util.Log.i("SpeechPipeline", "dropped ${diarRegions.size - regions.size} overlap region(s) that were one speaker")
            val activeAt: (Int, Float) -> Boolean = if (units != null) { spk, t -> units.any { it.speaker == spk && t >= it.startMs / 1000f && t <= it.endMs / 1000f } }
                else { spk, t -> turns.any { it.speaker == spk && t >= it.start && t <= it.end } }

            // Stage 10: separate overlapped regions and recognise each source separately.
            val replaced = ArrayList<LongRange>()
            val separated = ArrayList<Tok>()
            if (options.separate && regions.isNotEmpty() && SpeechModelPaths.assetExists(context, SpeechModelPaths.SEPARATOR)) {
                // Peak-memory control: the ASR model is released while the (large) separator runs, then reloaded.
                recognizer!!.release(); recognizer = null
                val sources = ArrayList<SeparatedRegion>()
                runCatching {
                    val t0 = android.os.SystemClock.elapsedRealtime()
                    val refs = refined?.centroids
                        ?: runCatching { SpeakerMatcher(context) }.getOrNull()?.use { m -> referenceEmbeddings(m, samples, turns, regions) } ?: emptyMap()
                    val matcher = runCatching { SpeakerMatcher(context) }.getOrNull()
                    try {
                        OverlapSeparator(context).use { sep ->
                            var skipped = 0
                            // Longest overlaps first: they hold the most lost words, and the wall-clock cap drops the rest.
                            regions.sortedByDescending { it.endMs - it.startMs }.forEachIndexed { idx, r ->
                                if (isCancelled()) error("cancelled")
                                if (android.os.SystemClock.elapsedRealtime() - t0 > options.maxSeparationWallMs) { skipped++; return@forEachIndexed }
                                separateRegion(sep, samples, activeAt, r, matcher, refs)?.let { sources += it }
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
            val all = ArrayList<Tok>()
            for (t in tokens) {
                if (replaced.any { t.ms in it }) continue            // words inside a separated region come from the separated streams
                all += t.copy(speaker = units?.let { u -> speakerFromUnits(u, t.ms) } ?: speakerAt(turns, t.ms))
            }
            all += separated
            val out = groupTokens(all)
            noTimestamps.forEach { out += it.copy(speaker = units?.let { u -> speakerFromUnits(u, (it.startMs + it.endMs) / 2) } ?: dominantSpeaker(turns, it.startMs, it.endMs)) }
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

    /** Sentence units labelled with FINAL speaker ids (0..k-1 in order of first appearance) plus one centroid per speaker. */
    private class Refined(val units: List<SpeakerUnit>, val centroids: Map<Int, FloatArray>)

    /**
     * Splits the transcript into sentence units (sentence punctuation or >0.35 s pause), embeds each unit (>= 1 s) with
     * CAM++ and clusters the embeddings: online assignment to centroids, two re-assignment passes, then agglomerative
     * merging of centroids closer than [PipelineOptions.mergeSimilarity] and absorption of tiny clusters (a few noisy
     * sentences that would otherwise show up as extra "speakers"). The ids it returns are the final ids used by the
     * separation stage and the output, so there is a single id space (the diarizer's ids are only used to find overlaps).
     */
    private fun refineSpeakers(
        samples: ShortArray, tokens: List<Tok>, options: PipelineOptions, diarizerCount: Int,
        turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>, segmentStartsMs: List<Long>,
    ): Refined? {
        if (tokens.size < 2) return null
        val sorted = tokens.sortedBy { it.ms }
        // Candidate turn changes inside a sentence: the diarizer's change points (accurate in time even for 0.5 s replies,
        // though its ids over-split) and GigaAM's dialogue dash. Each one is verified below before it is used.
        val st = turns.sortedBy { it.start }
        val diarCuts = ArrayList<Long>()
        for (i in 0 until st.size - 1) if (st[i].speaker != st[i + 1].speaker && st[i + 1].start - st[i].end <= 0.6f)
            diarCuts += (((st[i].end + st[i + 1].start) / 2f) * 1000).toLong()
        val dashTimes = ArrayList<Long>()
        // 1) sentence units: cut at sentence ends and pauses > 0.35 s
        data class Raw(var start: Long, var end: Long)
        val raws = ArrayList<Raw>()
        var cur: Raw? = null
        for ((i, t) in sorted.withIndex()) {
            val body = t.text.replace("▁", "").trim()
            if (body == "—" || body == "-" || body == "–") { dashTimes += t.ms; continue }
            val c = cur
            if (c == null) cur = Raw(t.ms, t.ms + 160) else { c.end = t.ms + 160 }
            val endsSentence = body.isNotEmpty() && body.last() in ".?!"
            val gap = if (i + 1 < sorted.size) sorted[i + 1].ms - (t.ms + 160) else 0L
            if (endsSentence || gap > 350) { raws += cur!!; cur = null }
        }
        cur?.let { raws += it }
        val matcher = SpeakerMatcher(context)
        try {
            fun norm(v: FloatArray): FloatArray { val n = Math.sqrt(v.sumOf { (it * it).toDouble() }).toFloat() + 1e-9f; return FloatArray(v.size) { v[it] / n } }
            fun dot(a: FloatArray, b: FloatArray): Float { var d = 0f; for (i in a.indices) d += a[i] * b[i]; return d }
            fun embedSpan(a: Long, b: Long): FloatArray? =
                matcher.embedShort(DspChain.floats(samples, (a * 16).toInt(), minOf((b * 16).toInt(), (a * 16).toInt() + 15 * 16000)))?.let { norm(it) }
            // 2) verified cuts inside a unit. A piece of at least a second on both sides must sound like two different people
            // (similarity < 0.55 for a diarizer cut, < 0.65 for a dash); next to a reply shorter than a second the embedding
            // is useless and the cut is taken on the evidence of the cue alone.
            val split = ArrayList<Raw>()
            for (r in raws) {
                val cands = (diarCuts.map { it to 0.55f } + dashTimes.map { it to 0.65f } + segmentStartsMs.map { it to 0.6f })
                    .filter { it.first > r.start + 300 && it.first < r.end - 300 }.sortedBy { it.first }
                var from = r.start
                for ((idx, cand) in cands.withIndex()) {
                    val (c, thr) = cand
                    if (c - from < 250 || r.end - c < 250) continue
                    val nextCut = cands.drop(idx + 1).firstOrNull { it.first - c >= 250 && r.end - it.first >= 250 }?.first ?: r.end
                    val left = c - from; val right = nextCut - c
                    val accept = if (left >= 1000 && right >= 1000) {
                        val e1 = embedSpan(from, c); val e2 = embedSpan(c, nextCut)
                        e1 == null || e2 == null || dot(e1, e2) < thr
                    } else true
                    if (accept) { split += Raw(from, c); from = c }
                }
                split += Raw(from, r.end)
            }
            // fragments under 0.25 s are recognition artefacts: glue them to the previous piece; nothing else is merged,
            // because a 0.6 s "Угу" belongs to somebody else more often than to the sentence before it
            val units = ArrayList<Raw>()
            for (r in split) {
                val p = units.lastOrNull()
                if (p != null && r.end - r.start < 250 && r.start - p.end < 300) p.end = r.end else units += Raw(r.start, r.end)
            }
            if (units.size < 2) return null
            val len = units.map { it.end - it.start }
            // CAM++ needs about a second; shorter units still get an embedding (>= 0.5 s) but are labelled afterwards
            val emb = units.mapIndexed { i, u ->
                if (len[i] < 500) null else {
                    val from = (u.start * 16).toInt(); val to = minOf((u.end * 16).toInt(), from + 15 * 16000)
                    matcher.embedShort(DspChain.floats(samples, from, to))?.let { norm(it) }
                }
            }
            val confident = units.indices.filter { len[it] >= 1000 && emb[it] != null }
            if (confident.size < 2 && units.size >= 2 && confident.isEmpty()) return null
            // 3) online clustering of the confident units, then two re-assignment passes
            val maxSpk = if (options.numSpeakers > 0) options.numSpeakers else 8
            val cents = ArrayList<FloatArray>(); val counts = ArrayList<Int>(); val label = IntArray(units.size) { -1 }
            for (i in confident) {
                val e = emb[i]!!
                var best = -1; var bs = -2f
                for (k in cents.indices) { val sc = dot(cents[k], e); if (sc > bs) { bs = sc; best = k } }
                if (best >= 0 && (bs >= options.sameSpeakerSimilarity || cents.size >= maxSpk)) {
                    label[i] = best; counts[best]++
                    cents[best] = norm(FloatArray(e.size) { cents[best][it] * (counts[best] - 1) + e[it] })
                } else { cents += e.copyOf(); counts += 1; label[i] = cents.size - 1 }
            }
            repeat(2) {
                val sums = cents.map { FloatArray(it.size) }; val n = IntArray(cents.size)
                for (i in confident) {
                    val e = emb[i]!!
                    var best = 0; var bs = -2f
                    for (k in cents.indices) { val sc = dot(cents[k], e); if (sc > bs) { bs = sc; best = k } }
                    label[i] = best; for (d in e.indices) sums[best][d] += e[d]; n[best]++
                }
                for (k in cents.indices) if (n[k] > 0) cents[k] = norm(sums[k])
            }
            fun live() = cents.indices.filter { k -> confident.any { label[it] == k } }
            run {
                val l = live()
                android.util.Log.i("SpeechPipeline", "clusters before merge: " + l.joinToString { k -> "$k(${confident.filter { label[it] == k }.sumOf { len[it] } / 1000}s)" } +
                    " sims: " + l.flatMap { x -> l.filter { it > x }.map { y -> "$x-$y=${"%.2f".format(dot(cents[x], cents[y]))}" } }.joinToString(" "))
            }
            fun merge(from: Int, into: Int) {
                val sum = FloatArray(cents[into].size)
                for (i in confident) if (label[i] == from || label[i] == into) emb[i]?.let { e -> for (d in e.indices) sum[d] += e[d] }
                for (i in confident) if (label[i] == from) label[i] = into
                cents[into] = norm(sum)
            }
            // 4) agglomerative merge of near-identical centroids (an online pass over-splits one voice that varies in tone)
            while (true) {
                val l = live(); var bi = -1; var bj = -1; var bs = -2f
                for (x in l) for (y in l) if (x < y) { val sc = dot(cents[x], cents[y]); if (sc > bs) { bs = sc; bi = x; bj = y } }
                if (bi < 0 || bs < options.mergeSimilarity) break
                android.util.Log.i("SpeechPipeline", "merge speakers $bj -> $bi (similarity ${"%.2f".format(bs)})")
                merge(bj, bi)
            }
            // 5) weak clusters are noise, not people: under 4 s of speech (or a single sentence) AND either a sliver of the
            // recording (< 8 %) or clearly the same voice as another cluster (cosine >= 0.5). Cutting a voice into
            // fragments of 2-3 s is how one person turns into three "speakers"; real people talk for longer than that.
            val total = confident.sumOf { len[it] }.coerceAtLeast(1)
            while (true) {
                val l = live(); if (l.size <= 1) break
                val dur = l.associateWith { k -> confident.filter { label[it] == k }.sumOf { len[it] } }
                val sentences = l.associateWith { k -> confident.count { label[it] == k } }
                fun nearest(k: Int) = l.filter { it != k }.maxByOrNull { dot(cents[k], cents[it]) }!!
                val tiny = l.filter { k ->
                    (dur[k]!! < 4000 || sentences[k]!! <= 1) &&
                        (dur[k]!! < 0.08 * total || dot(cents[k], cents[nearest(k)]) >= options.weakClusterSimilarity)
                }.minByOrNull { dur[it]!! } ?: break
                val target = nearest(tiny)
                val sc = dot(cents[tiny], cents[target])
                if (sc < options.absorbSimilarity) break
                android.util.Log.i("SpeechPipeline", "absorb weak speaker $tiny (${dur[tiny]} ms) -> $target (similarity ${"%.2f".format(sc)})")
                merge(tiny, target)
            }
            // The diarizer is tuned to over-split (threshold 0.5), so finding MORE people than it did is a red flag:
            // fold the most similar pairs together until the counts agree, but never merge clearly different voices.
            if (options.numSpeakers <= 0 && diarizerCount >= 1) while (live().size > diarizerCount) {
                val l = live(); var bi = -1; var bj = -1; var bs = -2f
                for (x in l) for (y in l) if (x < y) { val sc = dot(cents[x], cents[y]); if (sc > bs) { bs = sc; bi = x; bj = y } }
                if (bi < 0 || bs < 0.5f) break
                android.util.Log.i("SpeechPipeline", "more speakers than the diarizer found ($diarizerCount): merge $bj -> $bi (similarity ${"%.2f".format(bs)})")
                merge(bj, bi)
            }
            // fixed speaker count: merge the closest pair until k remain
            if (options.numSpeakers > 0) while (live().size > options.numSpeakers) {
                val l = live(); var bi = -1; var bj = -1; var bs = -2f
                for (x in l) for (y in l) if (x < y) { val sc = dot(cents[x], cents[y]); if (sc > bs) { bs = sc; bi = x; bj = y } }
                merge(bj, bi)
            }
            // 6) short units: closest centroid by embedding when there is a trustworthy match, otherwise the diarizer's
            // speaker (translated through the confident units), otherwise the previous unit's speaker
            // How pure is each diarizer id? P(final speaker | diarizer id) from the confident units. An id that is almost
            // always one person (pyannote often separates the lowest voice cleanly) is trusted; an id that mixes two people
            // says nothing and the embedding decides.
            val joint = HashMap<Int, HashMap<Int, Double>>()
            for (i in confident) for (t in turns) {
                val o = minOf(units[i].end / 1000f, t.end) - maxOf(units[i].start / 1000f, t.start)
                if (o > 0) joint.getOrPut(t.speaker) { HashMap() }.merge(label[i], o.toDouble(), Double::plus)
            }
            fun prior(d: Int?, k: Int): Double {
                val m = joint[d ?: return 0.0] ?: return 0.0
                val tot = m.values.sum(); return if (tot > 0) (m[k] ?: 0.0) / tot else 0.0
            }
            fun diarizerIdAt(a: Long, b: Long): Int? {
                val votes = HashMap<Int, Double>()
                for (t in turns) { val o = minOf(b / 1000f, t.end) - maxOf(a / 1000f, t.start); if (o > 0) votes.merge(t.speaker, o.toDouble(), Double::plus) }
                return votes.maxByOrNull { it.value }?.key
            }
            for (i in units.indices) if (label[i] < 0) {
                val e = emb[i]; val d = diarizerIdAt(units[i].start, units[i].end)
                var pick = -1; var best = Double.NEGATIVE_INFINITY; var bestSim = -2f
                for (k in live()) {
                    val sim = if (e != null) dot(cents[k], e) else 0f
                    val sc = sim + options.diarizerPriorWeight * prior(d, k)
                    if (sc > best) { best = sc; pick = k; bestSim = sim }
                }
                val trusted = pick >= 0 && ((e != null && bestSim >= options.shortUnitSimilarity) || prior(d, pick) >= 0.7)
                if (!trusted) pick = label.getOrNull(i - 1)?.takeIf { it >= 0 } ?: label.drop(i + 1).firstOrNull { it >= 0 } ?: pick.coerceAtLeast(0)
                label[i] = pick
            }
            // 7) final ids in order of first appearance, so "Speaker 1" is whoever talks first
            val order = label.distinct()
            val finalId = order.withIndex().associate { (n, c) -> c to n }
            val summary = order.joinToString { c -> "${finalId[c]}:${"%.1f".format(units.indices.filter { label[it] == c }.sumOf { len[it] } / 1000.0)}s" }
            android.util.Log.i("SpeechPipeline", "refined speakers: ${order.size} from ${units.size} units (${confident.size} long) [$summary] (join>=${options.sameSpeakerSimilarity}, merge>=${options.mergeSimilarity})")
            val result = units.indices.map { SpeakerUnit(units[it].start, units[it].end, finalId[label[it]] ?: 0) }
            val centroids = order.filter { c -> c in cents.indices }.associate { c -> finalId[c]!! to cents[c] }
            return Refined(result, centroids)
        } finally { matcher.close() }
    }

    /** Maps each diarizer speaker id to the final speaker that talks most while it is active (null if it never does). */
    private fun diarizerToFinal(turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>, units: List<SpeakerUnit>): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for (d in turns.map { it.speaker }.distinct()) {
            val votes = HashMap<Int, Double>()
            for (t in turns) if (t.speaker == d) for (u in units) {
                val o = minOf(u.endMs / 1000f, t.end) - maxOf(u.startMs / 1000f, t.start)
                if (o > 0) votes.merge(u.speaker, o.toDouble(), Double::plus)
            }
            votes.maxByOrNull { it.value }?.let { out[d] = it.key }
        }
        return out
    }

    /** A stretch where several speakers talk at once; [spk] lists every speaker the diarizer saw active in it. */
    private data class Region(val startMs: Long, val endMs: Long, val spk: List<Int>) {
        val a: Int get() = spk[0]
        val b: Int get() = spk[1]
    }

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

    /**
     * Spans where two or more speakers are active. Chains of overlaps (A+B, then B+C) merge into one region whose
     * speaker set is the union, so a free-for-all of 3–4 people becomes one region with 3–4 speakers.
     */
    private fun overlapRegions(turns: List<com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment>): List<Region> {
        data class Ev(val t: Float, val spk: Int, val d: Int)
        val ev = turns.flatMap { listOf(Ev(it.start, it.speaker, 1), Ev(it.end, it.speaker, -1)) }.sortedWith(compareBy({ it.t }, { it.d }))
        val active = HashMap<Int, Int>()
        val raw = ArrayList<Region>()
        var last = 0f
        for (e in ev) {
            val on = active.filterValues { it > 0 }.keys
            if (on.size >= 2 && e.t > last) raw += Region((last * 1000).toLong(), (e.t * 1000).toLong(), on.sorted())
            active.merge(e.spk, e.d, Int::plus); last = e.t
        }
        val merged = ArrayList<Region>()
        for (r in raw) {
            val p = merged.lastOrNull()
            if (p != null && r.startMs - p.endMs < 300) merged[merged.lastIndex] = p.copy(endMs = r.endMs, spk = (p.spk + r.spk).distinct().sorted().take(MAX_SIMULTANEOUS))
            else merged += r.copy(spk = r.spk.take(MAX_SIMULTANEOUS))
        }
        return merged.filter { it.endMs - it.startMs >= 600 }
    }

    private fun rms(x: FloatArray): Double { var e = 0.0; for (v in x) e += v.toDouble() * v; return Math.sqrt(e / x.size.coerceAtLeast(1)) }

    /** Cosine similarities of one source against every reference speaker, best first; null if it is too short to embed. */
    private fun rankAgainst(matcher: SpeakerMatcher?, x: FloatArray, refs: Map<Int, FloatArray>): List<Pair<Int, Double>>? {
        val e = matcher?.embed(x) ?: return null
        return refs.map { (spk, r) -> spk to SpeakerMatcher.cosine(e, r) }.sortedByDescending { it.second }
    }

    /** How much a source still sounds like several people: small gap between its two best matches (or two good ones). */
    private fun mixedness(rank: List<Pair<Int, Double>>?): Double =
        if (rank == null || rank.size < 2) Double.NEGATIVE_INFINITY else (rank[1].second - rank[0].second) + (if (rank[1].second > 0.4) 0.5 else 0.0)

    /**
     * Splits [mix] into [want] sources with the two-speaker separator, re-splitting whichever source still sounds mixed.
     * The diarizer cannot report more than two simultaneous speakers, so when references exist for more people than
     * we have sources, one extra pass hunts for a hidden third voice. Sources far quieter than the rest are dropped.
     */
    private fun splitSources(sep: OverlapSeparator, mix: FloatArray, want: Int, matcher: SpeakerMatcher?, refs: Map<Int, FloatArray>): List<FloatArray> {
        var sources = sep.separate(mix).toList()
        var tries = 0
        while (tries < 3 && sources.size < MAX_SIMULTANEOUS) {
            val ranks = sources.map { rankAgainst(matcher, it, refs) }
            android.util.Log.i("SpeechPipeline", "split pass $tries: ${sources.size} sources, ranks=${ranks.map { r -> r?.joinToString { "${it.first}:${"%.2f".format(it.second)}" } }} rms=${sources.map { "%.3f".format(rms(it)) }}")
            val needMore = sources.size < want
            // Candidates: sources that still sound like a blend of speakers (the diarizer cannot see a 3rd simultaneous voice).
            val candidates = sources.indices.filter { i ->
                val r = ranks[i]
                needMore || (mix.size <= 12 * 16000 && refs.size > sources.size && r != null && r.size >= 3 && r[1].second > 0.35 && r[0].second - r[1].second < 0.15)
            }.sortedByDescending { if (ranks.all { it == null }) rms(sources[it]) else mixedness(ranks[it]) }
            var progressed = false
            for (idx in candidates) {
                val parts = sep.separate(sources[idx]).toList()
                val pr = parts.map { rankAgainst(matcher, it, refs) }
                // A forced split (diarizer says more speakers) is always taken. A hunted one only if both parts are cleaner
                // and belong to different speakers; splitting a single clean voice would only duplicate its words.
                val accept = needMore || (pr.all { it != null } && ranks[idx] != null &&
                    pr[0]!![0].first != pr[1]!![0].first &&
                    (pr[0]!![0].second + pr[1]!![0].second) / 2 >= ranks[idx]!![0].second - 0.02)
                android.util.Log.i("SpeechPipeline", "  try split source $idx -> parts ${pr.map { r -> r?.take(2)?.joinToString { "${it.first}:${"%.2f".format(it.second)}" } }} accept=$accept")
                if (accept) { sources = sources.toMutableList().also { it.removeAt(idx); it.addAll(idx, parts) }; progressed = true; break }
            }
            tries++
            if (!progressed) break
        }
        val loud = sources.maxOf { rms(it) }
        return sources.filter { rms(it) >= 0.15 * loud }
    }

    /** Every injective assignment of [n] sources to distinct speakers from [pool], as lists of speaker ids. */
    private fun assignments(n: Int, pool: List<Int>): Sequence<IntArray> = sequence {
        fun rec(cur: IntArray, depth: Int, used: BooleanArray): Sequence<IntArray> = sequence {
            if (depth == n) { yield(cur.copyOf()); return@sequence }
            for (i in pool.indices) if (!used[i]) { used[i] = true; cur[depth] = pool[i]; yieldAll(rec(cur, depth + 1, used)); used[i] = false }
        }
        yieldAll(rec(IntArray(n), 0, BooleanArray(pool.size)))
    }

    /** Separates one overlapped region into one source per speaker and labels each source with a speaker, or null. */
    private fun separateRegion(
        sep: OverlapSeparator, samples: ShortArray,
        activeAt: (Int, Float) -> Boolean, r: Region,
        matcher: SpeakerMatcher?, refs: Map<Int, FloatArray>,
    ): SeparatedRegion? {
        val margin = 250L
        val s0 = ((r.startMs - margin).coerceAtLeast(0) * 16).toInt()
        val s1 = (((r.endMs + margin) * 16).toInt()).coerceAtMost(samples.size)
        if (s1 - s0 < 1600) return null
        val mix = DspChain.floats(samples, s0, s1)
        val src = splitSources(sep, mix, r.spk.size, matcher, refs).toTypedArray()
        val n = src.size
        // Which source is which speaker? 1) embeddings vs references from clean speech (best injective assignment over
        // all known speakers, so a mislabelled overlap is corrected), 2) for two sources: energy in exclusive speech,
        // 3) the diarizer's order.
        var owner = IntArray(n) { r.spk.getOrElse(it) { r.spk.last() } }
        var method = "none"; var matchMargin = 0.0
        val emb = src.map { matcher?.embed(it) }
        if (matcher != null && emb.all { it != null } && refs.isNotEmpty()) {
            fun score(a: IntArray) = a.indices.sumOf { i -> refs[a[i]]?.let { SpeakerMatcher.cosine(emb[i]!!, it) } ?: 0.0 }
            val pool = (r.spk + refs.keys).distinct()
            val labelledPool = r.spk.distinct()
            val labelled = if (labelledPool.size >= n) assignments(n, labelledPool).maxByOrNull { score(it) } else null
            val global = if (pool.size >= n) assignments(n, pool).maxByOrNull { score(it) } else null
            if (global != null && (labelled == null || score(global) > score(labelled) + 0.05)) {
                owner = global; method = "embedding-global"; matchMargin = score(global) - (labelled?.let { score(it) } ?: 0.0)
            } else if (labelled != null) {
                owner = labelled; method = "embedding"
                matchMargin = score(labelled) - (assignments(n, labelledPool).filter { !it.contentEquals(labelled) }.maxOfOrNull { score(it) } ?: 0.0)
            }
        }
        if (method == "none" && n == 2) {
            fun energy(x: FloatArray, only: Int, other: Int): Double {
                var e = 0.0; var c = 0
                for (i in x.indices) {
                    val t = (s0 + i) / 16000f
                    val onlyOn = activeAt(only, t)
                    val otherOn = activeAt(other, t)
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
        android.util.Log.i("SpeechPipeline", "separated ${r.startMs}-${r.endMs}ms spk ${r.spk} -> ${n} sources owners=${owner.toList()} match=$method matchMargin=${"%.2f".format(matchMargin)}")
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
        for (k in sr.src.indices) {
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
        // Full DTLN suppresses quiet words and soft onsets together with the noise, and the speech detector then never sees
        // them. A dry/wet mix keeps most of the noise reduction and leaves those words audible to it.
        val mixed = if (o.denoiseStrength >= 0.999f) den else ShortArray(hp.size) { i ->
            val w = if (i < den.size) den[i].toInt() else hp[i].toInt()
            (hp[i] + (w - hp[i]) * o.denoiseStrength).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Triple(asr, if (o.normalize) DspChain.normalizeCompress(mixed) else mixed, hp)
    }

    private fun audioDurationMs(samples: ShortArray) = samples.size * 1000L / SpeechModelPaths.SAMPLE_RATE

    private fun threads() = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)

    /** VAD runs on [vadSignal]; returned segments carry audio cut from [asrSignal] at the same sample positions. */
    private fun detectSpeech(vadSignal: ShortArray, asrSignal: ShortArray, threshold: Float): List<com.k2fsa.sherpa.onnx.SpeechSegment> {
        val vad = Vad(context.assets, VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = SpeechModelPaths.VAD, threshold = threshold, minSilenceDuration = 0.4f,
                minSpeechDuration = 0.25f, windowSize = 512, maxSpeechDuration = 25f,
            ),
            sampleRate = SpeechModelPaths.SAMPLE_RATE, numThreads = 1,
        ))
        val raw = ArrayList<Pair<Int, Int>>()          // (start, length) in samples, as the VAD reports them
        try {
            val window = 512
            fun drain() {
                while (!vad.empty()) {
                    val f = vad.front()
                    raw += f.start.toInt().coerceIn(0, asrSignal.size) to f.samples.size
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
        // Silero reports a speech start a few hundred ms late, which clipped the first word of a reply ("Нужно позвонить" came
        // out as "Позвонить"). Give the recogniser 0.25 s before and 0.15 s after each segment, never reaching into a neighbour.
        val result = ArrayList<com.k2fsa.sherpa.onnx.SpeechSegment>()
        for ((k, r) in raw.withIndex()) {
            val prevEnd = if (k > 0) raw[k - 1].first + raw[k - 1].second else 0
            val nextStart = if (k + 1 < raw.size) raw[k + 1].first else asrSignal.size
            val gapBefore = r.first - prevEnd; val gapAfter = nextStart - (r.first + r.second)
            val st = r.first - minOf(4000, maxOf(0, gapBefore / 2))
            val en = r.first + r.second + minOf(2400, maxOf(0, gapAfter / 2))
            result += com.k2fsa.sherpa.onnx.SpeechSegment(st.toLong(), DspChain.floats(asrSignal, st.coerceIn(0, asrSignal.size), en.coerceIn(0, asrSignal.size)))
        }
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
