package com.rassvet.essential.data.recording

import androidx.room.withTransaction
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.local.RecordingEntity
import com.rassvet.essential.data.local.SpeakerEntity
import com.rassvet.essential.data.local.SummaryEntity
import com.rassvet.essential.data.local.TranscriptSegmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomTranscriptRepository(private val db: AppDatabase) : TranscriptRepository {
    override suspend fun recording(id: String): RecordingEntity? = withContext(Dispatchers.IO) {
        db.recordingDao().get(id)
    }

    override suspend fun segments(recordingId: String): List<TranscriptSegmentEntity> = withContext(Dispatchers.IO) {
        db.recordingDao().segments(recordingId)
    }

    override suspend fun replaceSegments(recordingId: String, segments: List<TranscriptSegmentEntity>) =
        withContext(Dispatchers.IO) {
            require(segments.all { it.recordingId == recordingId }) { "Transcript contains a different recording ID" }
            require(segments.zipWithNext().all { (left, right) -> left.startMs <= right.startMs }) {
                "Transcript segments must be ordered"
            }
            require(segments.all { it.startMs >= 0 && it.endMs > it.startMs }) { "Invalid transcript timestamps" }
            db.withTransaction {
                db.recordingDao().deleteSegments(recordingId)
                if (segments.isNotEmpty()) db.recordingDao().insertSegments(segments)
            }
        }

    override suspend fun speakers(recordingId: String): List<SpeakerEntity> = withContext(Dispatchers.IO) {
        db.speakerDao().speakers(recordingId)
    }

    override suspend fun saveSummary(summary: SummaryEntity) = withContext(Dispatchers.IO) {
        db.summaryDao().insert(summary)
        Unit
    }
}
