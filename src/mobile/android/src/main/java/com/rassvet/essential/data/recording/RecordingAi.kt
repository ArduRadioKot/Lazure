package com.rassvet.essential.data.recording

import com.rassvet.essential.data.chat.ChatEngine
import com.rassvet.essential.data.local.SpeakerEntity
import com.rassvet.essential.data.local.TranscriptSegmentEntity
import kotlin.math.ln

/** Local summarisation and transcript RAG on top of the existing LiteRT-LM ChatEngine. */
object RecordingAi {
    fun timecode(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    fun parseTimecodeMs(tc: String): Long {
        val p = tc.split(':').map { it.toLongOrNull() ?: return -1 }
        return when (p.size) { 2 -> (p[0] * 60 + p[1]) * 1000; 3 -> (p[0] * 3600 + p[1] * 60 + p[2]) * 1000; else -> -1 }
    }

    private val tcRegex = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?)\b""")
    fun citedTimecodes(answer: String): List<Long> =
        tcRegex.findAll(answer).map { parseTimecodeMs(it.value) }.filter { it >= 0 }.distinct().toList()

    fun line(seg: TranscriptSegmentEntity, names: Map<String, String>) =
        "[${timecode(seg.startMs)}] ${names[seg.speakerId] ?: seg.speakerId}: ${seg.text}"

    fun nameMap(speakers: List<SpeakerEntity>) = speakers.associate { it.rawSpeakerId to it.displayName }

    private const val NO_INVENT = "Используй только факты из расшифровки. Не выдумывай. Если сведений нет — так и напиши."

    /** Map-reduce summary: chunk → notes → (repeat while too long) → structured summary. */
    suspend fun summarize(
        chat: ChatEngine, modelId: String, segments: List<TranscriptSegmentEntity>, speakers: List<SpeakerEntity>,
        onProgress: (String) -> Unit,
    ): String {
        require(segments.isNotEmpty()) { "Transcript is empty" }
        val names = nameMap(speakers)
        val lines = segments.map { line(it, names) }
        // Small-context builds (…_ekv1280, ~1300 tokens in total) need short chunks; Russian is ~2.3 chars per token.
        val small = modelId.contains("ekv1280", ignoreCase = true)
        var chunks = pack(lines, if (small) 1500 else 3000)
        var level = 0
        while (chunks.size > 1) {
            val notes = ArrayList<String>()
            chunks.forEachIndexed { i, c ->
                onProgress("${i + 1}/${chunks.size}")
                notes += chat.completeLocalIsolated(
                    system = "Ты помощник, который конспектирует фрагмент разговора. $NO_INVENT Сохраняй тайм-коды [м:сс], имена, решения, задачи и вопросы. Пиши кратким списком.",
                    user = "Фрагмент разговора:\n$c\n\nСделай конспект.", modelId = modelId,
                ).trim()
            }
            chunks = pack(notes, if (small) 1500 else 3500)
            if (++level > 4) break
        }
        onProgress("final")
        return chat.completeLocalIsolated(
            system = "Ты помощник, который готовит итоги разговора. $NO_INVENT Отвечай на языке расшифровки в Markdown.",
            user = "Материал:\n${chunks.joinToString("\n\n")}\n\nСоставь короткий итог ровно с четырьмя разделами: " +
                "## Краткое содержание, ## Решения, ## Задачи, ## Открытые вопросы. " +
                "В кратком содержании 2–3 предложения. В задачах указывай ответственного, только если он назван. " +
                "Если в разделе нечего написать, пиши «—».",
            modelId = modelId,
        ).trim()
    }

    private fun pack(lines: List<String>, limit: Int): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder()
        for (l in lines) {
            if (sb.isNotEmpty() && sb.length + l.length > limit) { out += sb.toString(); sb.clear() }
            sb.appendLine(l)
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    // ---- retrieval ----
    private fun tokens(t: String) = Regex("""[\p{L}\p{N}]{2,}""").findAll(t.lowercase()).map { stem(it.value) }.toList()
    private fun stem(w: String) = if (w.length > 5 && w.any { it in 'а'..'я' }) w.take(w.length - 2).take(7) else w.take(7)

    /** TF-IDF over segments, plus a boost for segments near a minute mentioned in the query ("на десятой минуте" is not parsed; "10 минуте" is). */
    fun retrieve(query: String, segments: List<TranscriptSegmentEntity>, k: Int = 6): List<TranscriptSegmentEntity> {
        if (segments.isEmpty()) return emptyList()
        val docs = segments.map { tokens(it.text) }
        val df = HashMap<String, Int>()
        docs.forEach { d -> d.toSet().forEach { df.merge(it, 1, Int::plus) } }
        val q = tokens(query).toSet()
        val n = segments.size.toDouble()
        val minute = Regex("""(\d{1,3})\s*(?:-?\s*)?(?:мин|min)""").find(query.lowercase())?.groupValues?.get(1)?.toLongOrNull()
        val scored = segments.indices.map { i ->
            val tf = docs[i].groupingBy { it }.eachCount()
            var score = q.sumOf { t -> (tf[t] ?: 0).let { if (it == 0) 0.0 else (1 + ln(it.toDouble())) * ln(1 + n / (df[t] ?: 1)) } }
            score /= Math.sqrt(docs[i].size.toDouble() + 3)
            if (minute != null) {
                val d = kotlin.math.abs(segments[i].startMs / 60_000 - (minute - 1))
                if (d <= 1) score += 2.0 - d
            }
            i to score
        }
        val top = scored.filter { it.second > 0 }.sortedByDescending { it.second }.take(k).map { it.first }
        val picked = if (top.isEmpty()) segments.indices.take(k) else top
        return picked.sorted().map { segments[it] }
    }

    suspend fun answer(
        chat: ChatEngine, modelId: String, question: String, segments: List<TranscriptSegmentEntity>,
        speakers: List<SpeakerEntity>, history: List<ChatEngine.HistoryLine>, onChunk: (String) -> Unit,
    ): Pair<String, List<TranscriptSegmentEntity>> {
        val hits = retrieve(question, segments)
        val names = nameMap(speakers)
        val context = hits.joinToString("\n") { line(it, names) }
        val reply = chat.completeLocalIsolated(
            system = "Ты отвечаешь на вопросы по расшифровке разговора. $NO_INVENT " +
                "Ссылайся на реплики тайм-кодами в формате [м:сс], как в источнике. Отвечай на языке вопроса.\n\nРеплики:\n$context",
            user = question, history = history.takeLast(6), modelId = modelId, onChunk = onChunk,
        )
        return reply.trim() to hits
    }
}
