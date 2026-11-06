package com.rassvet.essential.data.api

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JwtPayloadTest {
    @Test
    fun sessionId_extractsJti() {
        val payload =
            Base64.encodeToString(
                """{"jti":"session-42","sub":"user"}""".toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
        val token = "header.$payload.signature"
        assertEquals("session-42", JwtPayload.sessionId(token))
    }

    @Test
    fun sessionId_stripsBearerPrefix() {
        val payload =
            Base64.encodeToString(
                """{"jti":"abc"}""".toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
        val token = "Bearer header.$payload.sig"
        assertEquals("abc", JwtPayload.sessionId(token))
    }

    @Test
    fun sessionId_invalidTokenReturnsNull() {
        assertNull(JwtPayload.sessionId(null))
        assertNull(JwtPayload.sessionId(""))
        assertNull(JwtPayload.sessionId("not-a-jwt"))
        assertNull(JwtPayload.sessionId("a.b"))
    }
}
