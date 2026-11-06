package com.rassvet.essential.data.recording

import com.rassvet.essential.data.local.TranscriptSegmentEntity
import org.json.JSONArray
import org.json.JSONObject

data class TranscriptExportItem(
    val speakerId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

object TranscriptExport {
    fun validate(items: List<TranscriptExportItem>) {
        require(items.zipWithNext().all { (a, b) -> a.startMs <= b.startMs }) { "Segments must be ordered by start time" }
        require(items.all { it.speakerId.isNotBlank() }) { "Speaker IDs must not be blank" }
        require(items.all { it.startMs >= 0 && it.endMs > it.startMs }) { "Invalid segment timestamps" }
    }

    fun encode(items: List<TranscriptExportItem>): String {
        validate(items)
        val transcript = JSONArray()
        items.forEach { item ->
            transcript.put(JSONObject().put("speaker_id", item.speakerId)
                .put("start_ms", item.startMs).put("end_ms", item.endMs).put("text", item.text))
        }
        return JSONObject().put("transcript", transcript).toString(2)
    }

    fun encodeSegments(segments: List<TranscriptSegmentEntity>): String = encode(
        segments.map { TranscriptExportItem(it.speakerId, it.startMs, it.endMs, it.text) },
    )
}
