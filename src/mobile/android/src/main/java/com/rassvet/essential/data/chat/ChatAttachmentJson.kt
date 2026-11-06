package com.rassvet.essential.data.chat

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject


data class PersistedAttachment(
    val displayName: String,
    val mimeType: String,
    val base64: String?,
    val sizeBytes: Long,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isPdf: Boolean get() = mimeType.equals("application/pdf", ignoreCase = true)
}

fun ChatAttachment.toPersisted(): PersistedAttachment =
    PersistedAttachment(
        displayName = displayName,
        mimeType = mimeType,
        base64 = base64,
        sizeBytes = sizeBytes,
    )

fun PersistedAttachment.toChatAttachment(): ChatAttachment =
    ChatAttachment(
        uri = Uri.EMPTY,
        displayName = displayName,
        mimeType = mimeType,
        base64 = base64.orEmpty(),
        sizeBytes = sizeBytes,
    )

fun attachmentsToJson(attachments: List<PersistedAttachment>): String {
    val arr = JSONArray()
    for (a in attachments) {
        arr.put(
            JSONObject().apply {
                put("name", a.displayName)
                put("mimeType", a.mimeType)
                if (!a.base64.isNullOrBlank()) put("base64", a.base64)
                put("sizeBytes", a.sizeBytes)
            },
        )
    }
    return arr.toString()
}

fun attachmentsFromJson(json: String): List<PersistedAttachment> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                when (val el = arr.get(i)) {
                    is String -> if (el.isNotBlank()) add(PersistedAttachment(el, "application/octet-stream", null, 0))
                    is JSONObject -> {
                        val name = el.optString("name", el.optString("displayName", ""))
                        if (name.isBlank()) continue
                        add(
                            PersistedAttachment(
                                displayName = name,
                                mimeType = el.optString("mimeType", "application/octet-stream"),
                                base64 = el.optString("base64", null)?.takeIf { it.isNotBlank() },
                                sizeBytes = el.optLong("sizeBytes", 0),
                            ),
                        )
                    }
                }
            }
        }
    }.getOrDefault(emptyList())
}


