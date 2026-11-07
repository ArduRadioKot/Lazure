package com.rassvet.essential.data.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkFilterTest {
    private fun run(hide: Boolean, vararg chunks: String): String {
        val f = ThinkFilter(hide); return chunks.joinToString("") { f.feed(it) } + f.finish()
    }

    @Test fun hidesReasoningAcrossSplitTags() =
        assertEquals("Ответ", run(true, "<thi", "nk>\nдумаю\n</th", "ink>\n\nОтв", "ет"))

    @Test fun emptyThinkBlockFromNoThinkMode() = assertEquals("Привет", run(true, "<think>\n\n</think>\n\nПривет"))

    @Test fun showsReasoningAsQuote() =
        assertEquals("> *думаю*\n\nОтвет", run(false, "<think>думаю</think>Ответ"))

    @Test fun plainTextUntouched() = assertEquals("a < b и <i>", run(true, "a < b ", "и <i>"))
}
