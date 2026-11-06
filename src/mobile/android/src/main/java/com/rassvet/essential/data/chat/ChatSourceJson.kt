package com.rassvet.essential.data.chat

import org.json.JSONArray
import org.json.JSONObject

fun sourcesToJson(sources: List<ChatSourceNote>): String {
    val arr = JSONArray()
    for (source in sources) {
        arr.put(
            JSONObject().apply {
                put("uri", source.uri)
                put("title", source.title)
                put("snippet", source.snippet)
                put("kind", source.kind.name)
            },
        )
    }
    return arr.toString()
}

fun sourcesFromJson(json: String): List<ChatSourceNote> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val el = arr.optJSONObject(i) ?: continue
                val uri = el.optString("uri", "")
                if (uri.isBlank()) continue
                val kind =
                    runCatching { ChatSourceNote.Kind.valueOf(el.optString("kind", "VAULT")) }
                        .getOrDefault(ChatSourceNote.Kind.VAULT)
                add(
                    ChatSourceNote(
                        uri = uri,
                        title = el.optString("title", ""),
                        snippet = el.optString("snippet", ""),
                        kind = kind,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}


