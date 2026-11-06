package com.rassvet.essential.ui.markdown

sealed interface MdBlock {
    data class Heading(val level: Int, val content: String) : MdBlock
    data class Paragraph(val content: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Math(val latex: String, val display: Boolean) : MdBlock
    data class BulletItem(val content: String) : MdBlock
    data class TaskItem(val checked: Boolean, val content: String, val sourceLineIndex: Int) : MdBlock
    data object Rule : MdBlock
}

sealed interface InlineSegment {
    data class Text(val value: String) : InlineSegment
    data class Bold(val value: String) : InlineSegment
    data class Italic(val value: String) : InlineSegment
    data class Code(val value: String) : InlineSegment
    data class Math(val latex: String) : InlineSegment
}


fun looksLikeMathLine(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty()) return false
    if (t.startsWith("$$") || t.endsWith("$$")) return true
    if (t.startsWith("\\[") || t.endsWith("\\]")) return true
    if (t.startsWith("\\(") && t.endsWith("\\)")) return true
    if (t.startsWith("\\")) return true
    val dollarCount = t.count { it == '$' }
    if (dollarCount >= 2 && t.contains('$')) return true
    return false
}

private val TASK_LINE = Regex("""^[-*+]\s+\[([ xX])\]\s+(.*)$""")
private val STANDALONE_TASK_LINE = Regex("""^\[([ xX])\]\s+(.*)$""")

private fun parseTaskLine(trimmed: String): Pair<Boolean, String>? {
    val match = TASK_LINE.matchEntire(trimmed) ?: STANDALONE_TASK_LINE.matchEntire(trimmed) ?: return null
    val content = match.groupValues[2]
    if (looksLikeMathLine(content.trim())) return null
    val checked = match.groupValues[1].lowercase() == "x"
    return checked to content
}

fun toggleTaskLineAtIndex(text: String, lineIndex: Int): String {
    val lines = text.lines().toMutableList()
    if (lineIndex !in lines.indices) return text
    val original = lines[lineIndex]
    val trimmed = original.trim()
    val match = TASK_LINE.matchEntire(trimmed) ?: STANDALONE_TASK_LINE.matchEntire(trimmed) ?: return text
    val leading = original.takeWhile { it.isWhitespace() }
    val isChecked = match.groupValues[1].lowercase() == "x"
    val newCheck = if (isChecked) "[ ]" else "[x]"
    val updatedTrimmed = trimmed.replaceFirst(Regex("""\[[ xX]\]"""), newCheck)
    lines[lineIndex] = leading + updatedTrimmed
    return lines.joinToString("\n")
}

private fun isListMarkerLine(trimmed: String): Boolean {
    if (trimmed.length < 2) return false
    val content =
        when {
            trimmed.startsWith("- ") -> trimmed.drop(2).trim()
            trimmed.startsWith("* ") -> trimmed.drop(2).trim()
            trimmed.startsWith("+ ") -> trimmed.drop(2).trim()
            else -> return false
        }
    if (looksLikeMathLine(content)) return false
    return true
}

fun parseBlocks(text: String): List<MdBlock> {
    val lines = text.lines()
    val result = mutableListOf<MdBlock>()
    val paraLines = mutableListOf<String>()
    var i = 0

    fun flushParagraph() {
        if (paraLines.isNotEmpty()) {
            result.add(MdBlock.Paragraph(paraLines.joinToString("\n")))
            paraLines.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                result.add(MdBlock.Code(lang, codeLines.joinToString("\n")))
            }
            trimmed.startsWith("\\[") -> {
                flushParagraph()
                if (trimmed.endsWith("\\]") && trimmed.length > 4) {
                    val inner = trimmed.removePrefix("\\[").removeSuffix("\\]").trim()
                    result.add(MdBlock.Math(inner, display = true))
                } else {
                    val firstPart = trimmed.removePrefix("\\[").trimEnd().removeSuffix("\\]").trim()
                    val mathLines = mutableListOf<String>()
                    if (firstPart.isNotEmpty()) mathLines.add(firstPart)
                    i++
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        if (t.endsWith("\\]")) {
                            val last = t.removeSuffix("\\]").trim()
                            if (last.isNotEmpty()) mathLines.add(last)
                            break
                        }
                        mathLines.add(lines[i])
                        i++
                    }
                    result.add(MdBlock.Math(mathLines.joinToString("\n"), display = true))
                }
            }
            trimmed.startsWith("$$") -> {
                flushParagraph()
                if (trimmed.length > 4 && trimmed.endsWith("$$") && trimmed.count { it == '$' } >= 4) {
                    val inner = trimmed.trim('$', ' ').trim()
                    result.add(MdBlock.Math(inner, display = true))
                } else {
                    val firstPart = trimmed.removePrefix("$$").trimEnd().removeSuffix("$$").trim()
                    val mathLines = mutableListOf<String>()
                    if (firstPart.isNotEmpty()) mathLines.add(firstPart)
                    i++
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        if (t.endsWith("$$")) {
                            val last = t.removeSuffix("$$").trim()
                            if (last.isNotEmpty()) mathLines.add(last)
                            break
                        }
                        mathLines.add(lines[i])
                        i++
                    }
                    result.add(MdBlock.Math(mathLines.joinToString("\n"), display = true))
                }
            }
            trimmed.length >= 1 && trimmed.all { it == '#' } -> {
                paraLines.add(line)
            }
            trimmed.startsWith("#") -> {
                flushParagraph()
                var level = 0
                while (level < trimmed.length && trimmed[level] == '#') level++
                val content = trimmed.drop(level).trim()
                result.add(MdBlock.Heading(level.coerceIn(1, 6), content))
            }
            trimmed.matches(Regex("[-*_]{3,}")) -> {
                flushParagraph()
                result.add(MdBlock.Rule)
            }
            parseTaskLine(trimmed) != null -> {
                flushParagraph()
                val (checked, content) = parseTaskLine(trimmed)!!
                result.add(MdBlock.TaskItem(checked, content, i))
            }
            isListMarkerLine(trimmed) -> {
                flushParagraph()
                val content =
                    when {
                        trimmed.startsWith("- ") -> trimmed.drop(2)
                        trimmed.startsWith("* ") -> trimmed.drop(2)
                        else -> trimmed.drop(2)
                    }
                if (looksLikeMathLine(content.trim())) {
                    result.add(MdBlock.Paragraph(content.trim()))
                } else {
                    result.add(MdBlock.BulletItem(content))
                }
            }
            trimmed.isEmpty() -> flushParagraph()
            else -> paraLines.add(line)
        }
        i++
    }
    flushParagraph()
    return result
}

fun parseInlineSegments(text: String): List<InlineSegment> {
    val out = mutableListOf<InlineSegment>()
    var i = 0
    val n = text.length

    fun pushText(raw: String) {
        if (raw.isNotEmpty()) out.add(InlineSegment.Text(raw))
    }

    while (i < n) {
        if (text[i] == '\\' && i + 1 < n && text[i + 1] == '$') {
            pushText("$")
            i += 2
            continue
        }
        when {
            text.startsWith("\\(", i) -> {
                val end = text.indexOf("\\)", i + 2)
                if (end == -1) {
                    pushText(text.substring(i))
                    return out
                }
                out.add(InlineSegment.Math(text.substring(i + 2, end)))
                i = end + 2
            }
            text.startsWith("$$", i) -> {
                val end = text.indexOf("$$", i + 2)
                if (end == -1) {
                    pushText(text.substring(i))
                    return out
                }
                out.add(InlineSegment.Math(text.substring(i + 2, end)))
                i = end + 2
            }
            text[i] == '$' && !text.startsWith("$$", i) -> {
                val end = text.indexOf('$', i + 1)
                if (end == -1) {
                    pushText(text[i].toString())
                    i++
                } else {
                    out.add(InlineSegment.Math(text.substring(i + 1, end)))
                    i = end + 1
                }
            }
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) {
                    pushText(text.substring(i))
                    return out
                }
                out.add(InlineSegment.Bold(text.substring(i + 2, end)))
                i = end + 2
            }
            (text.startsWith("*", i) && !text.startsWith("**", i)) ||
                (text[i] == '_' && (i == 0 || text[i - 1].isWhitespace())) -> {
                val delim = text[i]
                val end = text.indexOf(delim, i + 1)
                if (end == -1) {
                    pushText(text[i].toString())
                    i++
                } else {
                    out.add(InlineSegment.Italic(text.substring(i + 1, end)))
                    i = end + 1
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end == -1) {
                    pushText(text[i].toString())
                    i++
                } else {
                    out.add(InlineSegment.Code(text.substring(i + 1, end)))
                    i = end + 1
                }
            }
            else -> {
                val next =
                    listOf(
                        text.indexOf("\\(", i),
                        text.indexOf("$$", i),
                        text.indexOf('$', i),
                        text.indexOf("**", i),
                        text.indexOf('*', i),
                        text.indexOf('_', i),
                        text.indexOf('`', i),
                    ).filter { it > i }.minOrNull() ?: n
                pushText(text.substring(i, next))
                i = next
            }
        }
    }
    return out
}


