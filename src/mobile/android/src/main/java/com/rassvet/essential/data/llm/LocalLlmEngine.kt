package com.rassvet.essential.data.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class LocalLlmRequest(
    val userMessage: String,
    val vaultContext: String,
    val webContext: String = "",
    val modelId: String,
    val systemOverlay: String = "",
    val chatSessionKey: Long = 0L,
    val history: List<Pair<Boolean, String>> = emptyList(),
)


interface LocalLlmEngine {
    suspend fun complete(request: LocalLlmRequest): String

    fun streamComplete(request: LocalLlmRequest): Flow<String>

    suspend fun releaseModel() {}
}


class StubLocalLlmEngine : LocalLlmEngine {
    override suspend fun complete(request: LocalLlmRequest): String {
        val sb = StringBuilder()
        streamComplete(request).collect { sb.append(it) }
        return sb.toString()
    }

    override fun streamComplete(request: LocalLlmRequest): Flow<String> =
        flow {
            val response = buildLocalSearchAnswer(request.userMessage, request.vaultContext)
            emit(response)
        }

    private fun buildLocalSearchAnswer(
        userMessage: String,
        vaultContext: String,
    ): String {
        val q = userMessage.trim()
        if (vaultContext.isBlank()) {
            return "Локальная модель недоступна (LiteRT-LM не загружен), " +
                "и в хранилище нет заметок, чтобы ответить на запрос «$q». " +
                "Создайте заметки или выберите облачную модель сверху справа."
        }
        val cards = vaultContext.split("\n\n---\n\n").map { it.trim() }.filter { it.isNotBlank() }
        if (cards.isEmpty()) {
            return "В хранилище ничего не нашлось по запросу «$q»."
        }
        val keywords = q
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
            .toSet()
        val scored = cards.map { card ->
            val lower = card.lowercase()
            val hits = if (keywords.isEmpty()) 0 else keywords.count { lower.contains(it) }
            card to hits
        }.sortedByDescending { it.second }

        val top = scored.take(3).filter { it.second > 0 || keywords.isEmpty() }
        if (top.isEmpty()) {
            return "По запросу «$q» в хранилище совпадений не нашлось.\n\n" +
                "Подскажите больше деталей или попробуйте облачную модель (иконка хранилища сверху справа)."
        }
        return buildString {
            appendLine("На основе ваших заметок:")
            appendLine()
            top.forEachIndexed { i, (card, _) ->
                val firstLine = card.lineSequence().firstOrNull()?.trim().orEmpty()
                val title = firstLine.removePrefix("#").trim().ifBlank { "Заметка ${i + 1}" }
                val body = card.lineSequence().drop(1).joinToString("\n").trim()
                appendLine("**${title}**")
                appendLine(body.take(600))
                if (body.length > 600) appendLine("…")
                appendLine()
            }
            appendLine("_(Локальный поиск без LLM. Для развёрнутого ответа подключите облачную модель сверху справа.)_")
        }.trim()
    }
}


