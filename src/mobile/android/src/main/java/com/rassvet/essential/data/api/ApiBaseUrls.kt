package com.rassvet.essential.data.api


object ApiBaseUrls {
    private const val HOST = "myessentiality.ru"

    @JvmStatic
    fun normalize(base: String): String {
        val trimmed = base.trim().trimEnd('/')
        return when {
            trimmed.equals("http://$HOST", ignoreCase = true) -> "https://$HOST"
            trimmed.startsWith("http://$HOST/", ignoreCase = true) ->
                "https://$HOST" + trimmed.removePrefix("http://$HOST")
            trimmed.isEmpty() -> "https://$HOST"
            else -> trimmed
        }
    }
}


