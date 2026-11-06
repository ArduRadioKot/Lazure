package com.rassvet.essential.ui.markdown

fun isNoteMarkdownFence(lang: String): Boolean {
    val key = lang.trim().lowercase()
    return key in setOf("note", "markdown", "md")
}


fun isCompactLatex(latex: String): Boolean {
    val t = latex.trim()
    if (t.isEmpty()) return true
    if (t.contains("\\begin")) return false
    return t.lines().size <= 1 && t.length <= 140
}


