package com.rassvet.essential.data.llm


object LocalModelFormats {
    const val LITERTLM_EXT = ".litertlm"

    fun isLocalModelFileName(fileName: String): Boolean =
        fileName.endsWith(LITERTLM_EXT, ignoreCase = true)

    fun displayStem(fileName: String): String =
        fileName.removeSuffix(LITERTLM_EXT).removeSuffix(".litertlm")
}


