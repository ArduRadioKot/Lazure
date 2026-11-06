package com.rassvet.essential.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit


object LocalWebResearch {
    const val QUERY_PLACEHOLDER = "{query}"
    const val DEFAULT_ENDPOINT =
        "https://api.duckduckgo.com/?q=$QUERY_PLACEHOLDER&format=json&no_html=1&skip_disambig=1"

    private const val CHAR_BUDGET = 2_500
    private const val TIMEOUT_SEC = 12L
    private const val USER_AGENT = "Lazure-Android/1.0"

    private val weatherPattern =
        Regex(
            pattern = """(?i)(погод|weather|температур|forecast|прогноз|дожд|снег|ветер|осадк)""",
        )
    private val webSearchPattern =
        Regex(
            pattern = """(?i)(найди\s+в\s+интернет|поиск\s+в\s+интернет|search\s+the\s+web|look\s+up\s+online)""",
        )
    private val locationPattern =
        Regex(
            pattern = """(?i)(?:в|in|for|at)\s+([A-Za-zА-Яа-яЁё0-9][A-Za-zА-Яа-яЁё0-9\s\-]{1,40})""",
        )

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()

    suspend fun fetchContext(query: String, customEndpoint: String?): String =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isBlank()) return@withContext ""
            val blocks = mutableListOf<String>()

            if (weatherPattern.containsMatchIn(q)) {
                fetchWeather(q)?.let { blocks += it }
            }

            val custom = customEndpoint?.trim()?.takeIf { it.isNotBlank() }
            if (custom != null) {
                fetchCustomEndpoint(q, custom)?.let { blocks += it }
            }

            fetchDuckDuckGoInstant(q)?.let { blocks += it }

            if (blocks.isEmpty() || webSearchPattern.containsMatchIn(q)) {
                fetchDuckDuckGoLite(q)?.let { blocks += it }
            }

            blocks
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n\n")
                .take(CHAR_BUDGET)
                .trim()
                .also { result ->
                    LocalLlmLog.i(
                        "Web research queryLen=${q.length} blocks=${blocks.size} resultLen=${result.length}",
                    )
                }
        }

    private fun fetchCustomEndpoint(query: String, endpoint: String): String? {
        if (!endpoint.contains(QUERY_PLACEHOLDER)) {
            LocalLlmLog.w("Web research endpoint must contain $QUERY_PLACEHOLDER")
            return null
        }
        val url =
            endpoint.replace(
                QUERY_PLACEHOLDER,
                URLEncoder.encode(query, Charsets.UTF_8.name()),
            )
        return httpGet(url)?.let { body ->
            formatBody(body, endpoint).trim().takeIf { it.isNotBlank() }
        }
    }

    private fun fetchDuckDuckGoInstant(query: String): String? {
        val url =
            DEFAULT_ENDPOINT.replace(
                QUERY_PLACEHOLDER,
                URLEncoder.encode(query, Charsets.UTF_8.name()),
            )
        return httpGet(url)?.let { body ->
            formatDuckDuckGo(body).trim().takeIf { it.isNotBlank() }
        }
    }

    private fun fetchDuckDuckGoLite(query: String): String? {
        val request =
            Request.Builder()
                .url("https://lite.duckduckgo.com/lite/")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .post(
                    FormBody.Builder()
                        .add("q", query)
                        .add("b", "")
                        .build(),
                )
                .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LocalLlmLog.w("DuckDuckGo Lite HTTP ${response.code}")
                    return@runCatching null
                }
                val html = response.body?.string().orEmpty()
                parseDuckDuckGoLite(html)
            }
        }.onFailure { LocalLlmLog.w("DuckDuckGo Lite failed", it) }
            .getOrNull()
    }

    private fun parseDuckDuckGoLite(html: String): String? {
        if (html.isBlank()) return null
        val titles =
            Regex(
                """class=['"]result-link['"][^>]*>([^<]+)</a>""",
                RegexOption.IGNORE_CASE,
            ).findAll(html)
                .map { decodeHtml(it.groupValues[1].trim()) }
                .filter { it.isNotBlank() }
                .take(5)
                .toList()
        val snippets =
            Regex(
                """class=['"]result-snippet['"][^>]*>([\s\S]*?)</td>""",
                RegexOption.IGNORE_CASE,
            ).findAll(html)
                .map { decodeHtml(it.groupValues[1].trim()) }
                .filter { it.isNotBlank() }
                .take(5)
                .toList()
        if (titles.isEmpty() && snippets.isEmpty()) return null
        return buildString {
            appendLine("Результаты поиска:")
            val count = maxOf(titles.size, snippets.size).coerceAtMost(5)
            for (i in 0 until count) {
                val title = titles.getOrNull(i)
                val snippet = snippets.getOrNull(i)
                if (!title.isNullOrBlank()) appendLine("${i + 1}. $title")
                if (!snippet.isNullOrBlank()) appendLine("   $snippet")
            }
        }.trim().takeIf { it.isNotBlank() }
    }

    private fun fetchWeather(query: String): String? {
        val location = extractLocation(query) ?: return null
        val encoded = URLEncoder.encode(location, Charsets.UTF_8.name())
        val url = "https://wttr.in/$encoded?format=j1&lang=ru"
        return httpGet(url, accept = "application/json")?.let { body ->
            formatWttrJson(body, location)
        }
    }

    private fun extractLocation(query: String): String? {
        locationPattern.find(query)?.groupValues?.getOrNull(1)?.trim()?.let { raw ->
            val cleaned =
                raw
                    .replace(Regex("""(?i)(сегодня|завтра|сейчас|today|tomorrow|now)"""), "")
                    .trim()
                    .trimEnd('?', '.', ',', '!')
            if (cleaned.length >= 2) return cleaned
        }
        val tokens =
            query
                .split(Regex("""\s+"""))
                .map { it.trim('?', '.', ',', '!') }
                .filter { it.length >= 3 }
        return tokens.lastOrNull { token ->
            token.first().isUpperCase() || token.any { it in 'А'..'я' || it in 'A'..'Z' }
        }
    }

    private fun formatWttrJson(json: String, location: String): String? =
        runCatching {
            val root = JSONObject(json)
            val current = root.optJSONArray("current_condition")?.optJSONObject(0) ?: return null
            val tempC = current.optString("temp_C").trim()
            val feels = current.optString("FeelsLikeC").trim()
            val humidity = current.optString("humidity").trim()
            val windKmph = current.optString("windspeedKmph").trim()
            val desc =
                current.optJSONArray("weatherDesc")
                    ?.optJSONObject(0)
                    ?.optString("value")
                    ?.trim()
                    .orEmpty()
            val area = root.optJSONArray("nearest_area")?.optJSONObject(0)
            val areaName =
                area?.optJSONArray("areaName")?.optJSONObject(0)?.optString("value")?.trim()
                    ?: location
            buildString {
                appendLine("Погода ($areaName):")
                if (desc.isNotBlank()) appendLine("- $desc")
                if (tempC.isNotBlank()) append("- $tempC°C")
                if (feels.isNotBlank() && feels != tempC) append(" (ощущается $feels°C)")
                appendLine()
                if (humidity.isNotBlank()) appendLine("- Влажность: $humidity%")
                if (windKmph.isNotBlank()) appendLine("- Ветер: $windKmph км/ч")
                append("(источник: wttr.in)")
            }.trim()
        }.getOrNull()

    private fun httpGet(url: String, accept: String = "application/json, text/plain, */*"): String? {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .get()
                .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LocalLlmLog.w("Web research HTTP ${response.code} for ${url.take(80)}")
                    return@runCatching null
                }
                response.body?.string()?.trim()?.takeIf { it.isNotEmpty() }
            }
        }.onFailure { LocalLlmLog.w("Web research GET failed", it) }
            .getOrNull()
    }

    private fun formatBody(body: String, endpoint: String): String =
        when {
            body.startsWith("{") || body.startsWith("[") ->
                when {
                    endpoint.contains("duckduckgo.com", ignoreCase = true) ->
                        formatDuckDuckGo(body)
                    else -> formatGenericJson(body)
                }
            else -> body
        }

    private fun formatDuckDuckGo(json: String): String =
        buildString {
            val root = JSONObject(json)
            val answer = root.optString("Answer").trim()
            if (answer.isNotBlank()) {
                appendLine("Ответ: $answer")
            }
            val heading = root.optString("Heading").trim()
            val abstract =
                root.optString("AbstractText").trim().ifBlank { root.optString("Abstract").trim() }
            val source = root.optString("AbstractSource").trim()
            if (abstract.isNotBlank()) {
                if (heading.isNotBlank()) appendLine("# $heading")
                appendLine(abstract)
                if (source.isNotBlank()) appendLine("(источник: $source)")
                appendLine()
            }
            appendResults(root.optJSONArray("Results"))
            appendRelatedTopics(root.optJSONArray("RelatedTopics"), depth = 0)
        }.trim()

    private fun StringBuilder.appendResults(array: JSONArray?) {
        if (array == null) return
        for (i in 0 until minOf(array.length(), 5)) {
            val item = array.optJSONObject(i) ?: continue
            val text = item.optString("Text").trim()
            val url = item.optString("FirstURL").trim()
            if (text.isNotBlank()) {
                appendLine("- $text")
                if (url.isNotBlank()) appendLine("  $url")
            }
        }
    }

    private fun StringBuilder.appendRelatedTopics(array: JSONArray?, depth: Int) {
        if (array == null || depth > 2) return
        val limit = if (depth == 0) 5 else 3
        var added = 0
        for (i in 0 until array.length()) {
            if (added >= limit) break
            when (val item = array.get(i)) {
                is JSONObject -> {
                    if (item.has("Topics")) {
                        appendRelatedTopics(item.optJSONArray("Topics"), depth + 1)
                    } else {
                        val text = item.optString("Text").trim()
                        val url = item.optString("FirstURL").trim()
                        if (text.isNotBlank()) {
                            appendLine("- $text")
                            if (url.isNotBlank()) appendLine("  $url")
                            added++
                        }
                    }
                }
            }
        }
    }

    private fun formatGenericJson(json: String): String {
        val blocks = mutableListOf<String>()
        collectJsonSnippets(json, blocks)
        return blocks.distinct().take(8).joinToString("\n\n")
    }

    private fun collectJsonSnippets(raw: String, out: MutableList<String>) {
        runCatching {
            when {
                raw.trimStart().startsWith("[") -> walkJsonArray(JSONArray(raw), out)
                else -> walkJsonObject(JSONObject(raw), out)
            }
        }
    }

    private fun walkJsonObject(obj: JSONObject, out: MutableList<String>) {
        val title =
            sequenceOf("title", "name", "heading")
                .mapNotNull { key -> obj.optString(key).trim().takeIf { it.isNotBlank() } }
                .firstOrNull()
        val snippet =
            sequenceOf("snippet", "content", "description", "abstract", "text", "body", "answer")
                .mapNotNull { key -> obj.optString(key).trim().takeIf { it.isNotBlank() } }
                .firstOrNull()
        val url =
            sequenceOf("url", "link", "href", "FirstURL")
                .mapNotNull { key -> obj.optString(key).trim().takeIf { it.isNotBlank() } }
                .firstOrNull()
        if (snippet != null) {
            out +=
                buildString {
                    if (title != null) appendLine("# $title")
                    append(snippet)
                    if (url != null) append("\n($url)")
                }.trim()
        }
        sequenceOf("results", "items", "data", "RelatedTopics", "topics", "organic")
            .forEach { key ->
                obj.optJSONArray(key)?.let { walkJsonArray(it, out) }
            }
    }

    private fun walkJsonArray(array: JSONArray, out: MutableList<String>) {
        for (i in 0 until minOf(array.length(), 12)) {
            when (val item = array.opt(i)) {
                is JSONObject -> walkJsonObject(item, out)
                is JSONArray -> walkJsonArray(item, out)
                is String -> {
                    val t = item.trim()
                    if (t.length > 20) out += t
                }
            }
        }
    }

    private fun decodeHtml(raw: String): String =
        raw
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}

