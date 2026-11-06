package com.rassvet.essential.data.api

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val JsonMedia = "application/json; charset=utf-8".toMediaType()

internal fun resolveChatCompletionsUrl(baseUrl: String): String {
    var root = baseUrl.trim()
    while (root.endsWith("/")) {
        root = root.dropLast(1)
    }
    return if (root.lowercase().endsWith("/v1")) {
        "$root/chat/completions"
    } else {
        "$root/v1/chat/completions"
    }
}

internal fun buildMessagesArray(
    messages: List<ChatMessageJson>,
    attachments: List<AttachmentPayload>,
): JSONArray {
    val arr = JSONArray()
    val lastUserIdx = messages.indexOfLast { !it.role.equals("assistant", true) }
    for ((i, m) in messages.withIndex()) {
        val mo = JSONObject()
        mo.put("role", m.role)
        if (i == lastUserIdx && attachments.isNotEmpty()) {
            mo.put("content", buildMultimodalContent(m.content, attachments))
        } else {
            mo.put("content", m.content)
        }
        arr.put(mo)
    }
    return arr
}

internal fun buildMultimodalContent(
    text: String,
    attachments: List<AttachmentPayload>,
): JSONArray {
    val parts = JSONArray()
    val textPart = JSONObject()
    textPart.put("type", "text")
    textPart.put("text", text)
    parts.put(textPart)
    for (att in attachments) {
        if (att.mimeType.startsWith("image/")) {
            val dataUri = "data:${att.mimeType};base64,${att.base64}"
            val urlObj = JSONObject()
            urlObj.put("url", dataUri)
            val imgPart = JSONObject()
            imgPart.put("type", "image_url")
            imgPart.put("image_url", urlObj)
            parts.put(imgPart)
        } else {
            val inline = JSONObject()
            inline.put("data", att.base64)
            inline.put("mime_type", att.mimeType)
            val docPart = JSONObject()
            docPart.put("type", "input_pdf")
            docPart.put("input_pdf", inline)
            parts.put(docPart)
        }
    }
    return parts
}

internal fun extractOpenAiTextContent(raw: Any?): String {
    return when (raw) {
        null -> ""
        is String -> raw.trim()
        is JSONArray -> {
            buildString {
                for (i in 0 until raw.length()) {
                    val part = raw.optJSONObject(i) ?: continue
                    when (part.optString("type")) {
                        "text" -> append(part.optString("text", ""))
                        else -> {
                            val text = part.optString("text", "")
                            if (text.isNotEmpty()) append(text)
                        }
                    }
                }
            }.trim()
        }
        else -> raw.toString().trim()
    }
}

fun openAiChatCompletions(
    baseUrl: String,
    apiKey: String,
    model: String,
    messages: List<ChatMessageJson>,
    temperature: Float,
    attachments: List<AttachmentPayload> = emptyList(),
): String {
    val url = resolveChatCompletionsUrl(baseUrl)
    val bodyJson = JSONObject()
    bodyJson.put("model", model)
    bodyJson.put("messages", buildMessagesArray(messages, attachments))
    bodyJson.put("temperature", temperature.toDouble())
    bodyJson.put("stream", false)

    val client =
        OkHttpClient.Builder()
            .callTimeout(120, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    try {
        val req =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(bodyJson.toString().toRequestBody(JsonMedia))
                .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw EssentialHttpException(resp.code, body)
            }
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
                ?: throw IOException("empty choices")
            if (choices.length() <= 0) {
                throw IOException("empty choices")
            }
            val choice = choices.getJSONObject(0)
            val message = choice.optJSONObject("message")
                ?: throw IOException("empty message")
            val text = extractOpenAiTextContent(message.opt("content"))
            if (text.isEmpty()) {
                val finishReason = choice.optString("finish_reason", "")
                if (finishReason.equals("content_filter", true) ||
                    finishReason.equals("safety", true)
                ) {
                    throw IOException("ответ заблокирован фильтром безопасности")
                }
                throw IOException("пустой ответ от провайдера")
            }
            return text
        }
    } finally {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
