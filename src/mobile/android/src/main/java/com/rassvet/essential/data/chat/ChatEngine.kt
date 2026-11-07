package com.rassvet.essential.data.chat

import android.content.Context
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.llm.HybridLocalLlmEngine
import com.rassvet.essential.data.llm.LocalLlmGenerationMetrics
import com.rassvet.essential.data.llm.LocalLlmRequest
import com.rassvet.essential.ui.PendingNoteContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local-only chat over LiteRT-LM. There is no cloud or web path: every prompt, transcript excerpt and answer stays on
 * the device. Attachments are not supported by the local models and are answered with a fixed message.
 */
class ChatEngine(
    private val context: Context,
    private val index: IndexRepository,
    private val localEngine: HybridLocalLlmEngine,
) {
    data class HistoryLine(val isUser: Boolean, val text: String)

    data class Request(
        val userVisibleText: String,
        val queryForSearch: String,
        val noteContext: PendingNoteContext? = null,
        val attachments: List<ChatAttachment> = emptyList(),
        val history: List<HistoryLine> = emptyList(),
        val activeModel: String?,
        val vaultStored: String?,
        val chatSessionKey: Long = 0L,
        val streamReplies: Boolean = true,
        val semanticSearchEnabled: Boolean = true,
    )

    suspend fun streamReply(
        request: Request,
        onChunk: (String) -> Unit,
    ): ChatStreamResult {
        if (request.attachments.isNotEmpty()) {
            val message = "Анализ вложений недоступен в автономном режиме."
            onChunk(message)
            return ChatStreamResult(text = message, sources = emptyList())
        }
        LocalLlmGenerationMetrics.onStreamStart()
        try {
            val vaultBundle =
                withContext(Dispatchers.IO) {
                    buildVaultContextForChat(
                        context = context,
                        index = index,
                        vaultUri = request.vaultStored,
                        userQuery = request.queryForSearch,
                        semanticSearchEnabled = request.semanticSearchEnabled,
                    )
                }
            val sources = mergeChatSources(vaultBundle.sources, request.noteContext)
            val text = generate(request, vaultBundle.text, onChunk)
            return ChatStreamResult(text = text, sources = sources)
        } finally {
            LocalLlmGenerationMetrics.onStreamEnd()
        }
    }

    /**
     * Local completion for recording summaries and transcript Q&A. Skips the note storage retrieval so transcript text
     * is never used as a search query.
     */
    suspend fun completeLocalIsolated(
        system: String,
        user: String,
        history: List<HistoryLine> = emptyList(),
        modelId: String,
        onChunk: (String) -> Unit = {},
    ): String {
        LocalLlmGenerationMetrics.onStreamStart()
        try {
            val req = LocalLlmRequest(
                userMessage = user, vaultContext = "", webContext = "", modelId = modelId,
                systemOverlay = system, history = history.map { it.isUser to it.text }, hideThinking = true,
            )
            val sb = StringBuilder()
            localEngine.streamComplete(req).collect { chunk ->
                sb.append(chunk); LocalLlmGenerationMetrics.onChunk(chunk); onChunk(chunk)
            }
            return sb.toString()
        } finally {
            LocalLlmGenerationMetrics.onStreamEnd()
        }
    }

    /**
     * Kept deliberately short. A 0.6B model recites whatever the system prompt contains, so the date line is added only
     * when the question is about the date or time.
     */
    private fun buildLocalSystemOverlay(noteContext: PendingNoteContext?, userText: String): String =
        buildString {
            appendLine("Ты — Lazure, помощник для разговоров. Отвечай коротко, по делу и на языке пользователя.")
            if (Regex("""(?iu)сегодня|дата|число|время|час\b|день недели|какой день|today|date|time|what day""").containsMatchIn(userText)) {
                appendLine(ChatDeviceClock.systemDateTimeLine())
            }
            if (noteContext != null) {
                appendLine()
                appendLine("Контекст «${noteContext.title.ifBlank { "Без названия" }}»:")
                appendLine(noteContext.body.take(1200))
            }
        }

    private suspend fun generate(
        request: Request,
        vaultContext: String,
        onChunk: (String) -> Unit,
    ): String {
        val localRequest =
            LocalLlmRequest(
                userMessage = request.queryForSearch.ifBlank { request.userVisibleText },
                vaultContext = vaultContext,
                webContext = "",
                modelId = request.activeModel ?: "",
                systemOverlay = buildLocalSystemOverlay(request.noteContext, request.userVisibleText),
                chatSessionKey = request.chatSessionKey,
                history = request.history.map { it.isUser to it.text },
            )
        val sb = StringBuilder()
        if (request.streamReplies) {
            localEngine.streamComplete(localRequest).collect { chunk ->
                sb.append(chunk)
                LocalLlmGenerationMetrics.onChunk(chunk)
                onChunk(chunk)
            }
        } else {
            val full = localEngine.complete(localRequest)
            sb.append(full)
            LocalLlmGenerationMetrics.onChunk(full)
            onChunk(full)
        }
        return sb.toString()
    }
}
