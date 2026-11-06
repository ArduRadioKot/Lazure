package com.rassvet.essential.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun looksLikeMathLine_detectsDollarMath() {
        assertTrue(looksLikeMathLine("""${'$'}${'$'}E=mc^2${'$'}${'$'}"""))
        assertTrue(looksLikeMathLine("\\frac{a}{b}"))
    }

    @Test
    fun parseBlocks_headingAndParagraph() {
        val blocks = parseBlocks("# Title\n\nHello world")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals("Title", (blocks[0] as MdBlock.Heading).content)
        assertTrue(blocks[1] is MdBlock.Paragraph)
    }

    @Test
    fun parseBlocks_codeFence() {
        val blocks = parseBlocks("```kotlin\nval x = 1\n```")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertEquals("kotlin", code.lang)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun parseBlocks_taskItem() {
        val blocks = parseBlocks("- [x] Done task")
        assertEquals(1, blocks.size)
        val task = blocks[0] as MdBlock.TaskItem
        assertTrue(task.checked)
        assertEquals("Done task", task.content)
    }

    @Test
    fun toggleTaskLineAtIndex_togglesCheckbox() {
        val text = "- [ ] Open\n- [x] Done"
        val toggled = toggleTaskLineAtIndex(text, lineIndex = 0)
        assertTrue(toggled.contains("- [x] Open"))
    }

    @Test
    fun parseInlineSegments_boldAndCode() {
        val segments = parseInlineSegments("**bold** and `code`")
        assertTrue(segments.any { it is InlineSegment.Bold })
        assertTrue(segments.any { it is InlineSegment.Code })
    }
}
