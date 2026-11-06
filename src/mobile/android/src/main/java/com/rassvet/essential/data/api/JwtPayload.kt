package com.rassvet.essential.data.api

import android.util.Base64
import org.json.JSONObject

object JwtPayload {
    fun sessionId(token: String?): String? {
        val raw = token?.trim()?.removePrefix("Bearer ")?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val parts = raw.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val payload =
                String(
                    Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                    Charsets.UTF_8,
                )
            JSONObject(payload).optString("jti").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}


