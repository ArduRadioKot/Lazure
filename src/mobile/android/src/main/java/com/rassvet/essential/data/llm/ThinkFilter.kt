package com.rassvet.essential.data.llm

/**
 * Streaming filter for `<think>…</think>` reasoning blocks (Qwen3, DeepSeek R1). Tags may be split across chunks.
 * hide = true drops the reasoning; hide = false renders it as a Markdown quote followed by the answer.
 */
class ThinkFilter(private val hide: Boolean) {
    private var inThink = false
    private var pending = StringBuilder()
    private var startedQuote = false
    private var swallowLeading = false

    fun feed(chunk: String): String {
        pending.append(chunk)
        val out = StringBuilder()
        while (true) {
            val tag = if (inThink) CLOSE else OPEN
            val idx = pending.indexOf(tag)
            if (idx >= 0) {
                emit(out, pending.substring(0, idx))
                pending.delete(0, idx + tag.length)
                if (inThink) { inThink = false; swallowLeading = true; if (!hide && startedQuote) out.append("*\n\n") }
                else { inThink = true; startedQuote = false }
                continue
            }
            // keep a possible partial tag at the end
            val keep = (minOf(tag.length - 1, pending.length) downTo 1).firstOrNull { tag.startsWith(pending.takeLast(it)) } ?: 0
            emit(out, pending.substring(0, pending.length - keep))
            pending = StringBuilder(pending.takeLast(keep))
            break
        }
        return out.toString()
    }

    /** Call once when the stream ends to flush any held-back text. */
    fun finish(): String {
        val out = StringBuilder(); emit(out, pending.toString()); pending = StringBuilder(); return out.toString()
    }

    private fun emit(out: StringBuilder, text: String) {
        if (text.isEmpty()) return
        if (inThink) {
            if (hide) return
            val t = if (!startedQuote) text.trimStart() else text
            if (t.isEmpty()) return
            if (!startedQuote) { out.append("> *"); startedQuote = true }
            out.append(t.replace("\n", "*\n> *"))
        } else {
            val t = if (swallowLeading) text.trimStart() else text
            if (t.isNotEmpty()) swallowLeading = false
            out.append(t)
        }
    }

    private companion object { const val OPEN = "<think>"; const val CLOSE = "</think>" }
}
