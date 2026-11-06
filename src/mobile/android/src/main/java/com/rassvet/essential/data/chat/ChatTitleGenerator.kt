package com.rassvet.essential.data.chat

import com.rassvet.essential.data.api.ChatMessageJson
import com.rassvet.essential.data.api.CloudLlmClient
import com.rassvet.essential.data.api.GeminiDefaults
import com.rassvet.essential.data.api.TimewebCloudAiPreset
import com.rassvet.essential.data.local.VaultPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


suspend fun generateChatTitle(
    prefs: VaultPreferencesRepository,
    userText: String,
    assistantText: String,
    geminiKey: String?,
    geminiModel: String,
    timewebKey: String?,
    preferTimeweb: Boolean,
): String? {
    val systemPrompt =
        "Придумай очень короткий заголовок для этого чата (2-5 слов, без кавычек, без точки в конце, " +
            "на языке пользователя). Только заголовок, без префиксов и пояснений."
    val convo =
        "Пользователь: ${userText.take(800)}\n\nАссистент: ${assistantText.take(1500)}"
    val msgs = listOf(
        ChatMessageJson("system", systemPrompt),
        ChatMessageJson("user", convo),
    )
    val temp = withContext(Dispatchers.IO) { prefs.getLlmRuntimeParams().temperature }
    val raw = runCatching {
        when {
            preferTimeweb && timewebKey != null ->
                openAiTitle(timewebKey, msgs, temp)
            geminiKey != null ->
                geminiTitle(geminiKey, geminiModel, msgs, temp)
            timewebKey != null ->
                openAiTitle(timewebKey, msgs, temp)
            else -> null
        }
    }.getOrNull() ?: return null
    val cleaned = raw.trim()
        .removePrefix("\"").removeSuffix("\"")
        .removeSuffix(".").removeSuffix("…")
        .lineSequence().firstOrNull()?.trim()
        .orEmpty()
    return cleaned.take(60).takeIf { it.isNotBlank() }
}

private suspend fun openAiTitle(
    key: String,
    msgs: List<ChatMessageJson>,
    temp: Float,
): String? =
    withContext(Dispatchers.IO) {
        val client = CloudLlmClient()
        try {
            client.openAiCompatibleChat(
                TimewebCloudAiPreset.OPENAI_COMPAT_BASE,
                key,
                TimewebCloudAiPreset.OPENAI_COMPAT_MODEL,
                msgs,
                temp,
            )
        } finally {
            client.close()
        }
    }

private suspend fun geminiTitle(
    key: String,
    model: String,
    msgs: List<ChatMessageJson>,
    temp: Float,
): String? =
    withContext(Dispatchers.IO) {
        val client = CloudLlmClient()
        try {
            client.geminiGenerate(key, model.ifBlank { GeminiDefaults.MODEL_ID }, msgs, temp)
        } finally {
            client.close()
        }
    }


