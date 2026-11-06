package com.rassvet.essential.data.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWebResearchTest {
    @Test
    fun fetchContext_blankQueryReturnsEmpty() = runBlocking {
        assertEquals("", LocalWebResearch.fetchContext("  ", customEndpoint = null))
    }

    @Test
    fun queryPlaceholder_isDocumentedConstant() {
        assertEquals("{query}", LocalWebResearch.QUERY_PLACEHOLDER)
        assertTrue(LocalWebResearch.DEFAULT_ENDPOINT.contains(LocalWebResearch.QUERY_PLACEHOLDER))
    }
}
