package com.rassvet.essential.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiBaseUrlsTest {
    @Test
    fun normalize_emptyUsesHttpsHost() {
        assertEquals("https://myessentiality.ru", ApiBaseUrls.normalize(""))
    }

    @Test
    fun normalize_httpHostUpgradesToHttps() {
        assertEquals("https://myessentiality.ru", ApiBaseUrls.normalize("http://myessentiality.ru"))
        assertEquals(
            "https://myessentiality.ru/api",
            ApiBaseUrls.normalize("http://myessentiality.ru/api"),
        )
    }

    @Test
    fun normalize_otherBaseUnchanged() {
        assertEquals("https://example.com", ApiBaseUrls.normalize("https://example.com"))
    }
}
