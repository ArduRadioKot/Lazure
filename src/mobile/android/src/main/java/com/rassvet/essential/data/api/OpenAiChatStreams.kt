package com.rassvet.essential.data.api

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val JsonMedia = "application/json; charset=utf-8".toMediaType()

fun openAiChatCompletionsStream(
    baseUrl: String,
    apiKey: String,
    model: String,
    messages: List<ChatMessageJson>,
    temperature: Float,
    attachments: List<AttachmentPayload> = emptyList(),
): Flow<String> =
    channelFlow {
        val url = resolveChatCompletionsUrl(baseUrl)
        val arr = buildMessagesArray(messages, attachments)
        val bodyJson = JSONObject()
        bodyJson.put("model", model)
        bodyJson.put("messages", arr)
        bodyJson.put("temperature", temperature.toDouble())
        bodyJson.put("stream", true)
        val bodyString = bodyJson.toString()

        val client =
            OkHttpClient.Builder()
                .callTimeout(120, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

        fun shutdownClient() {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }

        val req =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "text/event-stream")
                .post(bodyString.toRequestBody(JsonMedia))
                .build()

        val call = client.newCall(req)
        invokeOnClose { call.cancel() }
        try {
            val resp = call.execute()
            resp.use { r ->
                if (!r.isSuccessful) {
                    val err = r.body?.string().orEmpty()
                    throw EssentialHttpException(r.code, err)
                }
                val buf = r.body ?: throw IOException("пустое тело ответа")
                buf.source().use { source ->
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        if (!trimmed.startsWith("data:", ignoreCase = true)) continue
                        val payload =
                            trimmed
                                .removePrefix("data:")
                                .removePrefix("Data:")
                                .trim()
                        if (payload.isEmpty()) continue
                        if (payload == "[DONE]") break

                        try {
                            val json = JSONObject(payload)
                            val choices = json.optJSONArray("choices") ?: continue
                            if (choices.length() <= 0) continue
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta") ?: continue
                            var fragment = extractOpenAiTextContent(delta.opt("content"))
                            if (fragment.isEmpty()) {
                                val message = choice.optJSONObject("message")
                                if (message != null) {
                                    fragment = extractOpenAiTextContent(message.opt("content"))
                                }
                            }
                            if (fragment.isNotEmpty()) {
                                send(fragment)
                            }
                        } catch (_: Exception) {

                        }
                    }
                }
            }
        } finally {
            shutdownClient()
        }
    }.flowOn(Dispatchers.IO)


