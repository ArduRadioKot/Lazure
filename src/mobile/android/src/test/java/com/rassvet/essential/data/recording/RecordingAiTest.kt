package com.rassvet.essential.data.recording

import com.rassvet.essential.data.local.TranscriptSegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingAiTest {
    private fun seg(startMin: Int, text: String) =
        TranscriptSegmentEntity(recordingId = "r", speakerId = "speaker_1", startMs = startMin * 60_000L, endMs = startMin * 60_000L + 5000, text = text)

    @Test fun timecodeRoundTrip() {
        assertEquals("1:05", RecordingAi.timecode(65_000))
        assertEquals(65_000, RecordingAi.parseTimecodeMs("1:05"))
        assertEquals(listOf(65_000L, 3_725_000L), RecordingAi.citedTimecodes("см. [1:05] и [1:02:05]"))
    }

    @Test fun retrievalFindsRelevantSegment() {
        val segs = listOf(seg(0, "Привет, начнём встречу"), seg(1, "Бюджет проекта утверждён на следующий квартал"),
            seg(2, "Иван отвечает за подготовку отчёта"), seg(3, "Спасибо, до свидания"))
        val hits = RecordingAi.retrieve("Кто отвечает за отчёт?", segs, k = 1)
        assertEquals("Иван отвечает за подготовку отчёта", hits.single().text)
        assertTrue(RecordingAi.retrieve("что было на 4 минуте", segs, k = 1).single().text.startsWith("Спасибо"))
    }
}
