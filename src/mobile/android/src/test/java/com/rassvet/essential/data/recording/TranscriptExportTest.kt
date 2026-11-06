package com.rassvet.essential.data.recording

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TranscriptExportTest {
    @Test
    fun exportHasOnlyCompetitionSchemaKeys() {
        val json = JSONObject(TranscriptExport.encode(listOf(TranscriptExportItem("speaker_1", 1250, 4380, "Добрый день!"))))
        assertEquals(setOf("transcript"), keys(json))
        val item = json.getJSONArray("transcript").getJSONObject(0)
        assertEquals(setOf("speaker_id", "start_ms", "end_ms", "text"), keys(item))
        assertEquals("speaker_1", item.getString("speaker_id"))
        assertEquals(1250, item.getLong("start_ms"))
        assertEquals(4380, item.getLong("end_ms"))
    }

    @Test
    fun emptyRecordingExportsValidEmptyTranscript() {
        assertEquals("{\"transcript\":[]}", JSONObject(TranscriptExport.encode(emptyList())).toString())
    }

    @Test
    fun rejectsInvalidOrUnorderedTimestamps() {
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptExport.encode(listOf(TranscriptExportItem("speaker_1", 0, 0, "bad")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptExport.encode(listOf(
                TranscriptExportItem("speaker_1", 500, 700, "later"),
                TranscriptExportItem("speaker_1", 100, 300, "earlier"),
            ))
        }
    }

    private fun keys(json: JSONObject): Set<String> = json.keys().asSequence().toSet()
}
