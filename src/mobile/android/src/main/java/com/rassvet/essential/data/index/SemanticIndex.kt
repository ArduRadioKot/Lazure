package com.rassvet.essential.data.index

import android.content.Context
import com.rassvet.essential.data.local.NoteIndexEntity
import com.rassvet.essential.data.local.NoteFtsResult
import com.rassvet.essential.data.vault.VaultDocuments
import kotlin.math.ln
import kotlin.math.sqrt


class SemanticIndex {

    private data class Doc(
        val uri: String,
        val title: String,
        val body: String,
        val vector: Map<String, Float>,
        val norm: Float,
        val chunkIndex: Int = 0,
    )

    @Volatile
    private var docs: List<Doc> = emptyList()

    @Volatile
    private var idf: Map<String, Float> = emptyMap()

    fun isReady(): Boolean = docs.isNotEmpty()

    fun clear() {
        docs = emptyList()
        idf = emptyMap()
    }

    fun rebuild(context: Context, vaultStored: String?, recent: List<NoteIndexEntity>) {
        if (vaultStored.isNullOrBlank()) {
            docs = emptyList()
            idf = emptyMap()
            return
        }
        val root = VaultDocuments.resolveRoot(context, vaultStored) ?: run {
            docs = emptyList()
            idf = emptyMap()
            return
        }
        val files = VaultDocuments.listMarkdown(root)
        val rawDocs = files.mapNotNull { file ->
            val uri = file.uri.toString()
            val text = runCatching { VaultDocuments.readText(context, file, vaultStored) }
                .getOrDefault("")
            if (text.isBlank()) return@mapNotNull null
            val title = text.lineSequence()
                .firstOrNull { it.trim().isNotEmpty() }
                ?.trim()
                ?.removePrefix("#")
                ?.trim()
                ?.ifBlank { VaultDocuments.displayName(file) }
                ?: VaultDocuments.displayName(file)
            Triple(uri, title, text)
        }
        indexFromRawDocs(rawDocs)
    }

    internal fun loadForTest(entries: List<Triple<String, String, String>>) {
        indexFromRawDocs(entries)
    }

    private fun indexFromRawDocs(rawDocs: List<Triple<String, String, String>>) {
        if (rawDocs.isEmpty()) {
            docs = emptyList()
            idf = emptyMap()
            return
        }

        val chunked = ArrayList<Quintuple<String, String, String, Int, Int>>()
        for ((uri, title, body) in rawDocs) {
            val chunks = chunkText(body, CHUNK_SIZE, CHUNK_OVERLAP)
            chunks.forEachIndexed { idx, chunk ->
                chunked.add(Quintuple(uri, title, chunk, idx, chunks.size))
            }
        }

        val n = chunked.size
        val df = HashMap<String, Int>()
        val tokenized = chunked.map { (uri, title, body, chunkIdx, totalChunks) ->
            val tokens = tokenize(title + " " + body)
            val tf = HashMap<String, Int>()
            for (t in tokens) tf.merge(t, 1, Int::plus)
            for (t in tf.keys) df.merge(t, 1, Int::plus)
            Sextuple(uri, title, body, chunkIdx, totalChunks, tf)
        }
        val newIdf = HashMap<String, Float>(df.size)
        for ((term, count) in df) {
            newIdf[term] = ln((1.0 + n) / (1.0 + count)).toFloat() + 1f
        }
        idf = newIdf
        docs = tokenized.map { (uri, title, body, chunkIdx, _, tf) ->
            val vector = buildVector(tf, newIdf)
            val norm = vectorNorm(vector)
            Doc(uri, title, body, vector, norm, chunkIdx)
        }
    }

    fun findSimilar(query: String, limit: Int): List<NoteFtsResult> {
        if (docs.isEmpty()) return emptyList()
        val qTokens = tokenize(query)
        if (qTokens.isEmpty()) return emptyList()
        val qTf = HashMap<String, Int>()
        for (t in qTokens) qTf.merge(t, 1, Int::plus)
        val qVec = buildVector(qTf, idf)
        val qNorm = vectorNorm(qVec)
        if (qNorm == 0f) return emptyList()


        val bestPerUri = LinkedHashMap<String, Pair<Doc, Float>>()
        for (doc in docs) {
            val score = cosine(qVec, qNorm, doc.vector, doc.norm)
            if (score <= 0.01f) continue
            val prev = bestPerUri[doc.uri]
            if (prev == null || score > prev.second) {
                bestPerUri[doc.uri] = doc to score
            }
        }
        return bestPerUri.values
            .sortedByDescending { it.second }
            .take(limit)
            .map { (doc, _) ->
                val chunkLabel = if (doc.chunkIndex > 0) " · фрагмент ${doc.chunkIndex + 1}" else ""
                NoteFtsResult(doc.uri, doc.title + chunkLabel, doc.body)
            }
    }

    private fun buildVector(tf: Map<String, Int>, idfMap: Map<String, Float>): Map<String, Float> {
        if (tf.isEmpty()) return emptyMap()
        val total = tf.values.sum().toFloat()
        val out = HashMap<String, Float>(tf.size)
        for ((term, count) in tf) {
            val w = (count / total) * (idfMap[term] ?: 0f)
            if (w != 0f) out[term] = w
        }
        return out
    }

    private fun vectorNorm(v: Map<String, Float>): Float {
        var s = 0.0
        for (w in v.values) s += w * w
        return sqrt(s).toFloat()
    }

    private fun cosine(a: Map<String, Float>, aNorm: Float, b: Map<String, Float>, bNorm: Float): Float {
        if (aNorm == 0f || bNorm == 0f) return 0f
        val (small, big) = if (a.size <= b.size) a to b else b to a
        var dot = 0f
        for ((term, weight) in small) {
            val other = big[term] ?: continue
            dot += weight * other
        }
        return dot / (aNorm * bNorm)
    }

    private fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text
            .lowercase()
            .split(SPLIT_RE)
            .asSequence()
            .filter { it.length >= 3 }
            .filter { it !in STOPWORDS }
            .toList()
    }

    private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
    private data class Sextuple<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)

    companion object {
        private const val CHUNK_SIZE = 900
        private const val CHUNK_OVERLAP = 120

        private val SPLIT_RE = Regex("[^\\p{L}\\p{N}]+")
        private val STOPWORDS = setOf(
            "это", "что", "как", "так", "для", "при", "его", "ему", "она", "они", "был",
            "была", "были", "если", "или", "тоже", "когда", "после", "перед", "будет",
            "есть", "нет", "над", "под", "из-за", "там", "тут", "уже", "ещё", "вот",
            "the", "and", "for", "with", "from", "this", "that", "have", "has", "are",
            "was", "were", "but", "not", "you", "your", "they", "their", "out", "into",
        )


        fun chunkText(text: String, chunkSize: Int, overlap: Int): List<String> {
            val normalized = text.trim()
            if (normalized.length <= chunkSize) return listOf(normalized)
            val out = ArrayList<String>()
            var start = 0
            while (start < normalized.length) {
                val end = (start + chunkSize).coerceAtMost(normalized.length)
                out.add(normalized.substring(start, end))
                if (end >= normalized.length) break
                start = (end - overlap).coerceAtLeast(start + 1)
            }
            return out
        }
    }
}


