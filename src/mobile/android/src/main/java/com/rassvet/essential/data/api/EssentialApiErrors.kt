package com.rassvet.essential.data.api

import android.content.Context
import com.rassvet.essential.R
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONObject

object EssentialApiErrors {
    fun chatMessage(context: Context, error: Throwable): String {
        if (error is EssentialHttpException) {
            val code = parseErrorCode(error.responseBody)
            when {
                error.statusCode == 402 || code == "quota_exhausted" ->
                    return context.getString(R.string.chat_error_quota_exhausted)
                error.statusCode == 503 || code == "cloud_ai_disabled" ->
                    return context.getString(R.string.chat_error_cloud_ai_disabled)
                error.statusCode == 502 || code == "ai_provider_error" ->
                    return context.getString(R.string.chat_error_ai_provider)
                error.statusCode in 500..599 || code == "internal_error" ->
                    return context.getString(R.string.chat_error_server)
            }
        }
        val cause = error.cause
        if (cause != null && cause !== error) {
            return chatMessage(context, cause)
        }
        if (error is UnknownHostException || error is SocketTimeoutException || error is InterruptedIOException) {
            return context.getString(R.string.settings_quota_error_network)
        }
        return error.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.chat_error_generic)
    }

    private fun parseErrorCode(responseBody: String?): String? =
        runCatching {
            val body = responseBody?.trim().orEmpty()
            if (body.isEmpty() || body[0] != '{') return@runCatching null
            JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        }.getOrNull()
}


