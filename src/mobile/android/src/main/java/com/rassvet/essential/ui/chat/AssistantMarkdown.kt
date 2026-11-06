package com.rassvet.essential.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle


fun assistantMarkdownAnnotated(text: String, baseColor: Color): AnnotatedString =
    buildAnnotatedString {
        if (text.isEmpty()) return@buildAnnotatedString
        appendSimpleMarkdown(text, baseColor)
    }

private fun codeBlockBackground(base: Color): Color =
    if (base.luminance() > 0.5f) Color(0x14000000) else Color(0x33FFFFFF)

private fun inlineCodeBackground(base: Color): Color =
    if (base.luminance() > 0.5f) Color(0x0F000000) else Color(0x22FFFFFF)

private fun AnnotatedString.Builder.appendSimpleMarkdown(s: String, base: Color) {
    val blockBg = codeBlockBackground(base)
    val inlineBg = inlineCodeBackground(base)
    var i = 0
    val n = s.length
    while (i < n) {
        when {
            s.startsWith("```", i) -> {
                val end = s.indexOf("```", startIndex = i + 3)
                if (end == -1) {
                    withStyle(
                        SpanStyle(
                            color = base,
                            fontFamily = FontFamily.Monospace,
                            background = blockBg,
                        ),
                    ) {
                        append(s, i, n)
                    }
                    return
                }
                var innerStart = i + 3
                val nl = s.indexOf('\n', startIndex = i + 3)
                if (nl != -1 && nl < end) {
                    val firstLine = s.substring(i + 3, nl).trim()
                    if (firstLine.isNotEmpty() &&
                        firstLine.all { ch ->
                            ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.'
                        }
                    ) {
                        innerStart = nl + 1
                    }
                }
                val codeFrom = innerStart.coerceAtMost(end)
                withStyle(
                    SpanStyle(
                        color = base,
                        fontFamily = FontFamily.Monospace,
                        background = blockBg,
                    ),
                ) {
                    append(s, codeFrom, end)
                }
                i = end + 3
            }
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", startIndex = i + 2)
                if (end == -1) {
                    withStyle(SpanStyle(color = base)) {
                        append(s, i, n)
                    }
                    return
                }
                withStyle(SpanStyle(color = base, fontWeight = FontWeight.Bold)) {
                    append(s, i + 2, end)
                }
                i = end + 2
            }
            s[i] == '`' -> {
                val end = s.indexOf('`', startIndex = i + 1)
                if (end == -1) {
                    withStyle(SpanStyle(color = base)) {
                        append(s[i])
                    }
                    i++
                } else {
                    withStyle(
                        SpanStyle(
                            color = base,
                            fontFamily = FontFamily.Monospace,
                            background = inlineBg,
                        ),
                    ) {
                        append(s, i + 1, end)
                    }
                    i = end + 1
                }
            }
            else -> {
                val cand =
                    listOf(
                        s.indexOf("```", i),
                        s.indexOf("**", i),
                        s.indexOf('`', i),
                    ).filter { it >= 0 }.minOrNull() ?: n
                val nextPlain = cand.coerceAtMost(n).coerceAtLeast(i + 1)
                withStyle(SpanStyle(color = base)) {
                    append(s, i, nextPlain)
                }
                i = nextPlain
            }
        }
    }
}


