package com.rassvet.essential.data.chat

import android.net.Uri
import com.rassvet.essential.ui.PendingNoteContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VaultContextHelperTest {
    @Test
    fun mergeChatSources_pinsNoteContextFirst() {
        val vaultSources =
            listOf(
                ChatSourceNote("vault://note-a", "Note A", "snippet a"),
            )
        val pinned =
            PendingNoteContext(
                title = "Pinned",
                body = "Pinned body line",
                uri = Uri.parse("vault://pinned"),
            )
        val merged = mergeChatSources(vaultSources, pinned)
        assertEquals(2, merged.size)
        assertEquals(ChatSourceNote.Kind.PINNED, merged.first().kind)
        assertEquals("vault://pinned", merged.first().uri)
    }

    @Test
    fun mergeChatSources_withoutNoteContextReturnsVaultOnly() {
        val vaultSources =
            listOf(
                ChatSourceNote("vault://note-a", "Note A", "snippet a"),
            )
        val merged = mergeChatSources(vaultSources, noteContext = null)
        assertEquals(1, merged.size)
        assertEquals("vault://note-a", merged.first().uri)
    }

    @Test
    fun mergeChatSources_deduplicatesPinnedUri() {
        val vaultSources =
            listOf(
                ChatSourceNote("vault://same", "Duplicate", "snippet"),
                ChatSourceNote("vault://other", "Other", "other"),
            )
        val pinned =
            PendingNoteContext(
                title = "Same",
                body = "Body",
                uri = Uri.parse("vault://same"),
            )
        val merged = mergeChatSources(vaultSources, pinned)
        assertEquals(2, merged.size)
        assertTrue(merged.none { it.uri == "vault://same" && it.kind != ChatSourceNote.Kind.PINNED })
    }
}
