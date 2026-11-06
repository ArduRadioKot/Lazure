package com.rassvet.essential.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatAttachmentJsonTest {
    @Test
    fun attachmentsJson_roundTrip() {
        val original =
            listOf(
                PersistedAttachment("note.pdf", "application/pdf", null, 1024),
                PersistedAttachment("photo.png", "image/png", "aGVsbG8=", 512),
            )
        val json = attachmentsToJson(original)
        val restored = attachmentsFromJson(json)
        assertEquals(2, restored.size)
        assertEquals("note.pdf", restored[0].displayName)
        assertEquals("application/pdf", restored[0].mimeType)
        assertTrue(restored[0].isPdf)
        assertTrue(restored[1].isImage)
        assertEquals("aGVsbG8=", restored[1].base64)
    }

    @Test
    fun attachmentsFromJson_blankReturnsEmpty() {
        assertTrue(attachmentsFromJson("").isEmpty())
        assertTrue(attachmentsFromJson("   ").isEmpty())
    }

    @Test
    fun attachmentsFromJson_legacyStringEntry() {
        val restored = attachmentsFromJson("""["legacy.txt"]""")
        assertEquals(1, restored.size)
        assertEquals("legacy.txt", restored[0].displayName)
    }

    @Test
    fun persistedAttachment_typeFlags() {
        val image = PersistedAttachment("a.jpg", "image/jpeg", null, 0)
        val pdf = PersistedAttachment("b.pdf", "application/pdf", null, 0)
        assertTrue(image.isImage)
        assertTrue(pdf.isPdf)
    }
}
