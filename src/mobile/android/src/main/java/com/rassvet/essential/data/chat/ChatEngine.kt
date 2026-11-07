package com.rassvet.essential.data.chat

import android.content.Context
import com.rassvet.essential.R
import com.rassvet.essential.BuildConfig
import com.rassvet.essential.data.api.AttachmentPayload
import com.rassvet.essential.data.api.ChatMessageJson
import com.rassvet.essential.data.api.CloudLlmClient
import com.rassvet.essential.data.api.EssentialApi
import com.rassvet.essential.data.api.EssentialApiErrors
import com.rassvet.essential.data.api.GeminiDefaults
import com.rassvet.essential.data.api.TimewebCloudAiPreset
import com.rassvet.essential.data.api.openAiChatCompletions
import com.rassvet.essential.data.api.openAiChatCompletionsStream
import com.rassvet.essential.data.chat.ChatDeviceClock
import com.rassvet.essential.data.index.IndexRepository
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.data.llm.ChatModelSelection
import com.rassvet.essential.data.llm.HybridLocalLlmEngine
import com.rassvet.essential.data.llm.LocalLlmRequest
import com.rassvet.essential.data.llm.LocalWebResearch
import com.rassvet.essential.data.llm.LlamaRuntimeTuning
import com.rassvet.essential.data.llm.LocalLlmGenerationMetrics
import com.rassvet.essential.data.network.NetworkMonitor
import com.rassvet.essential.data.network.OnlineChecker
import com.rassvet.essential.ui.PendingNoteContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext


class ChatEngine(
    private val context: Context,
    private val prefs: VaultPreferencesRepository,
    private val index: IndexRepository,
    private val localEngine: HybridLocalLlmEngine,
    private val onlineChecker: OnlineChecker,
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

        val cloudMode: Boolean = false,
        val cloudProvider: String = "essential",
        val apiBase: String? = null,
        val authToken: String? = null,
        val openAiBase: String? = null,
        val openAiKey: String? = null,
        val openAiModel: String? = null,
        val geminiKey: String? = null,
        val geminiModel: String? = null,
    )

    suspend fun streamReply(
        request: Request,
        onChunk: (String) -> Unit,
    ): ChatStreamResult {
        val useLocal = request.attachments.isEmpty() && !request.cloudMode
        if (useLocal) {
            LocalLlmGenerationMetrics.onStreamStart()
        }
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
            val vaultContext = vaultBundle.text
            val webContext =
                if (useLocal && request.queryForSearch.isNotBlank() && onlineChecker.isOnline()) {
                    withContext(Dispatchers.IO) {
                        if (prefs.resolveLocalWebResearchEnabled()) {
                            LocalWebResearch.fetchContext(
                                query = request.queryForSearch,
                                customEndpoint = prefs.resolveLocalWebResearchEndpoint(),
                            )
                        } else {
                            ""
                        }
                    }
                } else {
                    ""
                }
            val systemPrefix = buildSystemPrefix(request.noteContext, vaultContext)
            val hasAttachments = request.attachments.isNotEmpty()

            val text =
                when {
                    hasAttachments -> handleAttachments(request, systemPrefix, onChunk)
                    request.cloudMode -> handleCloudTimeweb(request, systemPrefix, onChunk)
                    else -> handleLocal(request, systemPrefix, vaultContext, webContext, onChunk)
                }
            return ChatStreamResult(text = text, sources = sources)
        } finally {
            if (useLocal) {
                LocalLlmGenerationMetrics.onStreamEnd()
            }
        }
    }


    /**
     * Fully local completion for recording summaries/Q&A. Bypasses vault and web research so transcript
     * text is never used as a search query and never leaves the device.
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
     * Kept deliberately short. A 0.6B model recites whatever the system prompt contains (it answered "Hello" with the
     * date and "Справка из интернета: [Пожалуйста, что-то есть]"), so the date line is added only when the question is
     * about the date or time, and web-research wording lives in the engine and only when there is web context.
     */
    private fun buildLocalSystemOverlay(noteContext: PendingNoteContext?, userText: String): String =
        buildString {
            appendLine("Ты — Lazure, помощник для разговоров и заметок. Отвечай коротко, по делу и на языке пользователя.")
            if (Regex("""(?iu)сегодня|дата|число|время|час\b|день недели|какой день|today|date|time|what day""").containsMatchIn(userText)) {
                appendLine(ChatDeviceClock.systemDateTimeLine())
            }
            if (noteContext != null) {
                appendLine()
                appendLine("Контекст заметки «${noteContext.title.ifBlank { "Без названия" }}»:")
                appendLine(noteContext.body.take(1200))
            }
        }

    private fun buildSystemPrefix(
        noteContext: PendingNoteContext?,
        vaultContext: String,
    ): String =
        buildString {
            appendLine(
                "Ты — локальный ассистент Lazure, приложения для работы с разговорами. " +
                    "Отвечай по существу, используй обычный Markdown для оформления. " +
                    "Не утверждай, что сохранил файл или изменил данные приложения. " +
                    "Если сведений недостаточно, прямо сообщи об этом.",
            )
            appendLine(ChatDeviceClock.systemDateTimeLine())
            appendLine()
            if (noteContext != null) {
                appendLine("Контекст заметки «${noteContext.title.ifBlank { "Без названия" }}»:")
                appendLine(noteContext.body.take(3500))
                appendLine()
                appendLine(
                    "Пользователь работает с этой заметкой. Если просит расписать, дополнить, " +
                        "изменить или оформить содержимое — дай готовый markdown для заметки " +
                        "в блоке ```note ... ``` (только текст заметки внутри блока). " +
                        "Кратко поясни изменения снаружи блока.",
                )
                appendLine()
            }
            if (vaultContext.isNotBlank()) {
                appendLine("Справка из хранилища:")
                appendLine(vaultContext.take(3500))
                appendLine()
            }
        }

    private fun cloudRequiresNetwork(onChunk: (String) -> Unit): String? {
        if (onlineChecker.isOnline()) return null
        return errorMsg(onChunk, context.getString(R.string.chat_error_offline_cloud))
    }

    private suspend fun handleAttachments(
        request: Request,
        systemPrefix: String,
        onChunk: (String) -> Unit,
    ): String {
        val message = "Анализ вложений недоступен в автономном режиме."
        onChunk(message)
        return message
    }

    private fun estimateTokens(text: String): Int =
        (text.length / 4).coerceAtLeast(0)

    private suspend fun reportClientUsageIfPossible(
        request: Request,
        promptText: String,
        replyText: String,
    ) {
        val base = request.apiBase?.trim()?.takeIf { it.isNotBlank() } ?: return
        val token = request.authToken?.trim()?.takeIf { it.isNotBlank() } ?: return
        val tin = estimateTokens(promptText)
        val tout = estimateTokens(replyText)
        if (tin == 0 && tout == 0) return
        withContext(Dispatchers.IO) {
            runCatching {
                val api = EssentialApi(base)
                try {
                    api.reportAiUsage(token, tin, tout)
                } finally {
                    api.close()
                }
            }
        }
    }

    private suspend fun handleCloudTimeweb(
        request: Request,
        systemPrefix: String,
        onChunk: (String) -> Unit,
    ): String {
        cloudRequiresNetwork(onChunk)?.let { return it }
        when (request.cloudProvider) {
            "essential" -> return handleEssentialApi(request, systemPrefix, onChunk)
        }
        if (!BuildConfig.ALLOW_DIRECT_CLOUD) {
            val base = request.apiBase?.trim()?.takeIf { it.isNotBlank() }
            val token = request.authToken?.trim()?.takeIf { it.isNotBlank() }
            if (base != null && token != null) {
                return handleEssentialApi(request, systemPrefix, onChunk)
            }
            return errorMsg(
                onChunk,
                "Доступна только локальная модель.",
            )
        }
        when (request.cloudProvider) {
            "openai_compat" -> return handleOpenAiCompat(request, systemPrefix, onChunk)
            "gemini" -> return handleGeminiCloud(request, systemPrefix, onChunk)
            "timeweb" -> Unit
        }
        val key = resolveTimewebKey(request)
            ?: return errorMsg(onChunk, "API-ключ не настроен. Пересоберите с ключом в local.properties.")
        val history = request.history.map {
            ChatMessageJson(if (it.isUser) "user" else "assistant", it.text)
        }
        val userContent = (systemPrefix + request.userVisibleText).trim()
        val llmParams = withContext(Dispatchers.IO) { prefs.getLlmRuntimeParams() }
        val promptForUsage = history.joinToString("\n") { it.content } + "\n" + userContent
        val reply =
            collectStream(
                request.streamReplies,
                openAiChatCompletionsStream(
                    TimewebCloudAiPreset.OPENAI_COMPAT_BASE,
                    key,
                    TimewebCloudAiPreset.OPENAI_COMPAT_MODEL,
                    history + ChatMessageJson("user", userContent),
                    llmParams.temperature,
                ),
                onChunk,
            )
        reportClientUsageIfPossible(request, promptForUsage, reply)
        return reply
    }

    private suspend fun handleOpenAiCompat(
        request: Request,
        systemPrefix: String,
        onChunk: (String) -> Unit,
    ): String {
        cloudRequiresNetwork(onChunk)?.let { return it }
        val base = request.openAiBase?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENAI_COMPAT_BASE_URL_LOCAL.trim().takeIf { it.isNotBlank() }
        val key = request.openAiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENAI_COMPAT_API_KEY_LOCAL.trim().takeIf { it.isNotBlank() }
        val model = request.openAiModel?.trim()?.takeIf { it.isNotBlank() } ?: "gpt-4o-mini"
        if (base == null || key == null) {
            return errorMsg(onChunk, "Укажите базовый URL и API-ключ OpenAI-совместимого провайдера в настройках.")
        }
        val history = request.history.map {
            ChatMessageJson(if (it.isUser) "user" else "assistant", it.text)
        }
        val userContent = (systemPrefix + request.userVisibleText).trim()
        val llmParams = withContext(Dispatchers.IO) { prefs.getLlmRuntimeParams() }
        return if (request.streamReplies) {
            collectStream(
                true,
                openAiChatCompletionsStream(base, key, model, history + ChatMessageJson("user", userContent), llmParams.temperature),
                onChunk,
            )
        } else {
            val reply = withContext(Dispatchers.IO) {
                val client = CloudLlmClient()
                try {
                    client.openAiCompatibleChat(base, key, model, history + ChatMessageJson("user", userContent), llmParams.temperature)
                } finally {
                    client.close()
                }
            }
            onChunk(reply)
            reply
        }
    }

    private suspend fun handleGeminiCloud(
        request: Request,
        systemPrefix: String,
        onChunk: (String) -> Unit,
    ): String {
        cloudRequiresNetwork(onChunk)?.let { return it }
        val key = resolveGeminiKey(request)
            ?: return errorMsg(onChunk, "Укажите ключ Gemini (AI Studio) в настройках.")
        val model = request.geminiModel?.trim()?.takeIf { it.isNotBlank() } ?: GeminiDefaults.MODEL_ID
        val history = request.history.map {
            ChatMessageJson(if (it.isUser) "user" else "assistant", it.text)
        }
        val userContent = (systemPrefix + request.userVisibleText).trim()
        val llmParams = withContext(Dispatchers.IO) { prefs.getLlmRuntimeParams() }
        val reply = withContext(Dispatchers.IO) {
            val client = CloudLlmClient()
            try {
                client.geminiGenerate(key, model, history + ChatMessageJson("user", userContent), llmParams.temperature)
            } finally {
                client.close()
            }
        }
        onChunk(reply)
        return reply
    }

    private suspend fun handleEssentialApi(
        request: Request,
        systemPrefix: String,
        onChunk: (String) -> Unit,
    ): String {
        cloudRequiresNetwork(onChunk)?.let { return it }
        val base = request.apiBase?.trim()?.takeIf { it.isNotBlank() }
        val token = request.authToken?.trim()?.takeIf { it.isNotBlank() }
        if (base == null || token == null) {
            return errorMsg(onChunk, "Укажите адрес API и войдите в настройках.")
        }
        val history = request.history.map {
            ChatMessageJson(if (it.isUser) "user" else "assistant", it.text)
        }
        val userContent = (systemPrefix + request.userVisibleText).trim()
        val apiMessages = mergeTrailingUserMessage(history, userContent)
        val payloads =
            request.attachments.map {
                AttachmentPayload(it.mimeType, it.base64, it.displayName)
            }
        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    val api = EssentialApi(base)
                    try {
                        api.aiComplete(token, apiMessages, payloads).text.trim()
                    } finally {
                        api.close()
                    }
                }
            }
        return result.fold(
            onSuccess = { reply ->
                if (reply.isBlank()) {
                    return errorMsg(
                        onChunk,
                        context.getString(R.string.chat_empty_model_response),
                    )
                }
                onChunk(reply)
                reply
            },
            onFailure = { err ->
                errorMsg(onChunk, EssentialApiErrors.chatMessage(context, err))
            },
        )
    }

    private suspend fun handleLocal(
        request: Request,
        systemPrefix: String,
        vaultContext: String,
        webContext: String,
        onChunk: (String) -> Unit,
    ): String {
        val modelId = request.activeModel ?: ""
        val prompt = request.queryForSearch.ifBlank { request.userVisibleText }
        val localRequest =
            LocalLlmRequest(
                userMessage = prompt,
                vaultContext = vaultContext,
                webContext = webContext,
                modelId = modelId,
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

    private fun resolveTimewebKey(request: Request): String? {
        if (!BuildConfig.ALLOW_DIRECT_CLOUD) return null
        return request.openAiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENAI_COMPAT_API_KEY_LOCAL.trim().takeIf { it.isNotBlank() }
    }

    private fun resolveGeminiKey(request: Request): String? {
        if (!BuildConfig.ALLOW_DIRECT_CLOUD) return null
        return request.geminiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GEMINI_API_KEY_LOCAL.trim().takeIf { it.isNotBlank() }
    }


    private fun mergeTrailingUserMessage(
        history: List<ChatMessageJson>,
        userContent: String,
    ): List<ChatMessageJson> {
        val trimmed = userContent.trim()
        if (trimmed.isEmpty()) return history
        val last = history.lastOrNull()
        return if (last != null && last.role == "user") {
            history.dropLast(1) +
                ChatMessageJson(
                    "user",
                    buildString {
                        append(last.content.trim())
                        append("\n\n")
                        append(trimmed)
                    },
                )
        } else {
            history + ChatMessageJson("user", trimmed)
        }
    }

    private fun errorMsg(onChunk: (String) -> Unit, msg: String): String {
        onChunk(msg)
        return msg
    }

    private suspend fun collectStream(
        stream: Boolean,
        flow: Flow<String>,
        onChunk: (String) -> Unit,
    ): String {
        val sb = StringBuilder()
        if (stream) {
            flow.collect { chunk ->
                sb.append(chunk)
                onChunk(chunk)
            }
        } else {
            flow.collect { chunk -> sb.append(chunk) }
            onChunk(sb.toString())
        }
        return sb.toString()
    }

    companion object {
        fun buildUserVisibleText(
            trimmed: String,
            noteCtx: PendingNoteContext?,
            hasAttachments: Boolean,
        ): String =
            buildString {
                if (noteCtx != null) {
                    appendLine("По заметке «${noteCtx.title.ifBlank { "Без названия" }}»:")
                    if (trimmed.isNotBlank()) append(trimmed)
                    else append("Расскажи, что в ней важно.")
                } else if (trimmed.isNotBlank()) {
                    append(trimmed)
                } else if (hasAttachments) {
                    append("Что на этих файлах?")
                }
            }

        suspend fun selectChatModel(
            prefs: VaultPreferencesRepository,
            modelName: String,
        ) {
            prefs.setActiveGguf(modelName)
            prefs.setHomeChatMode(if (modelName == "essentialai") "timeweb_cloud" else "local")
        }

        suspend fun restoreModelFromHomeChatMode(prefs: VaultPreferencesRepository) {
            val mode = prefs.homeChatMode.first()
            var active = prefs.activeGgufName.first()
            if (active == "essential_lite") {
                prefs.setActiveGguf(null)
                active = null
            }
            if (active.isNullOrBlank()) {
                prefs.setActiveGguf(
                    if (mode == "timeweb_cloud" || mode == "cloud") "essentialai" else null,
                )
            }
        }
    }
}


