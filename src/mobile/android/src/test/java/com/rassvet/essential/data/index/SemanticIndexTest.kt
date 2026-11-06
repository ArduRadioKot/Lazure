package com.rassvet.essential.data.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticIndexTest {
    @Test
    fun chunkText_splitsLongBodyWithOverlap() {
        val text = "a".repeat(2000)
        val chunks = SemanticIndex.chunkText(text, chunkSize = 900, overlap = 120)
        assertTrue(chunks.size >= 2)
        assertEquals(900, chunks.first().length)
    }

    @Test
    fun findSimilar_prefersMatchingDocument() {
        val index = SemanticIndex()
        index.loadForTest(
            listOf(
                Triple("uri://kotlin", "Kotlin", "Kotlin coroutines suspend functions async programming"),
                Triple("uri://recipes", "Recipes", "Tomato soup pasta basil dinner cooking"),
            ),
        )
        val results = index.findSimilar("kotlin coroutines async", limit = 2)
        assertTrue(results.isNotEmpty())
        assertEquals("uri://kotlin", results.first().uri)
    }

    @Test
    fun findSimilar_emptyQueryReturnsEmpty() {
        val index = SemanticIndex()
        index.loadForTest(listOf(Triple("uri://a", "A", "Some meaningful text here")))
        assertTrue(index.findSimilar("  ", limit = 3).isEmpty())
    }
}
