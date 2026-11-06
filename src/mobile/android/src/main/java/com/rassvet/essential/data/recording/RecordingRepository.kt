package com.rassvet.essential.data.recording

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.withTransaction
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.local.RecordingEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RecordingRepository(private val context: Context, private val db: AppDatabase) : TranscriptExporter {
    val recordings: Flow<List<RecordingEntity>> = db.recordingDao().observeAll()
    fun observeSegments(recordingId: String) = db.recordingDao().observeSegments(recordingId)
    fun speakers(recordingId: String) = db.speakerDao().observeSpeakers(recordingId)

    suspend fun updateSegmentText(recordingId: String, segmentId: Long, text: String) = withContext(Dispatchers.IO) {
        val updated = text.trim()
        require(updated.isNotEmpty() && updated.length <= 10_000) { "Transcript text must contain 1–10,000 characters" }
        val existing = db.recordingDao().segments(recordingId).firstOrNull { it.id == segmentId }
            ?: error("Transcript segment no longer exists")
        check(db.recordingDao().updateSegmentText(recordingId, segmentId, updated, existing.text) == 1)
    }

    suspend fun renameSpeaker(recordingId: String, rawSpeakerId: String, displayName: String) = withContext(Dispatchers.IO) {
        val name = displayName.trim()
        require(name.isNotEmpty() && name.length <= 80) { "Speaker name must contain 1–80 characters" }
        check(db.speakerDao().rename(recordingId, rawSpeakerId, name) == 1) { "Speaker no longer exists" }
    }
    suspend fun rename(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val title = newTitle.trim()
        require(title.isNotEmpty() && title.length <= 120) { "Title must contain 1–120 characters" }
        check(db.recordingDao().rename(id, title, System.currentTimeMillis()) == 1) { "Recording no longer exists" }
    }

    suspend fun transition(
        id: String,
        target: RecordingProcessingStatus,
        progress: Int = 0,
    ) = withContext(Dispatchers.IO) {
        require(progress in 0..100) { "Progress must be between 0 and 100" }
        db.withTransaction {
            val current = db.recordingDao().get(id) ?: error("Recording no longer exists")
            val sourceStatus = RecordingProcessingStatus.fromStoredValue(current.processingStatus)
                ?: error("Unknown recording state: ${current.processingStatus}")
            check(sourceStatus.canTransitionTo(target)) {
                "Invalid recording transition: ${sourceStatus.storedValue} → ${target.storedValue}"
            }
            val nextProgress = if (target == RecordingProcessingStatus.Completed) 100 else progress
            check(db.recordingDao().updateStatus(id, target.storedValue, nextProgress, System.currentTimeMillis()) == 1)
        }
    }

    suspend fun importAudio(uri: Uri): RecordingEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val audioDir = File(context.filesDir, "recordings").apply { mkdirs() }
        val target = File(audioDir, "$id.audio")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Cannot open selected audio file")
            val duration = readDuration(context.contentResolver, uri, target)
            require(duration > 0) { "The selected file has no readable audio track" }
            val title = queryDisplayName(context.contentResolver, uri) ?: "Recording"
            val recording = RecordingEntity(
                id = id, title = title.substringBeforeLast('.', title), audioPath = target.absolutePath,
                durationMs = duration, createdAt = System.currentTimeMillis(),
                processingStatus = "awaiting_asr",
            )
            db.recordingDao().insert(recording)
            recording
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    suspend fun registerRecordedAudio(file: File, title: String = "Recording"): RecordingEntity = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() > 0L) { "Recorded audio file is empty" }
        val retriever = MediaMetadataRetriever()
        val duration = try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
        require(duration > 0) { "The recording has no readable audio track" }
        val now = System.currentTimeMillis()
        val recording = RecordingEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Recording" },
            audioPath = file.absolutePath,
            durationMs = duration,
            createdAt = now,
            updatedAt = now,
            processingStatus = RecordingProcessingStatus.AwaitingAsr.storedValue,
        )
        db.recordingDao().insert(recording)
        recording
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val recording = db.recordingDao().get(id) ?: return@withContext
        db.withTransaction {
            db.recordingDao().deleteSegments(id)
            db.speakerDao().deleteForRecording(id)
            db.summaryDao().deleteForRecording(id)
            db.recordingChatSessionDao().deleteForRecording(id)
            db.recordingDao().delete(id)
        }
        File(recording.audioPath).delete()
    }

    override suspend fun exportJson(recordingId: String): String = withContext(Dispatchers.IO) {
        TranscriptExport.encodeSegments(db.recordingDao().segments(recordingId))
    }

    private fun readDuration(resolver: ContentResolver, uri: Uri, copied: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            runCatching { retriever.setDataSource(context, uri) }
                .getOrElse { retriever.setDataSource(copied.absolutePath) }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally { retriever.release() }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
