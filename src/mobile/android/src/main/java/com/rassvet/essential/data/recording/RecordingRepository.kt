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
    fun summaries(recordingId: String) = db.summaryDao().observe(recordingId)
    suspend fun saveSummary(recordingId: String, content: String, modelId: String) = withContext(Dispatchers.IO) {
        db.summaryDao().insert(com.rassvet.essential.data.local.SummaryEntity(
            recordingId = recordingId, content = content, createdAt = System.currentTimeMillis(), modelId = modelId))
    }
    suspend fun chatMessages(recordingId: String) = withContext(Dispatchers.IO) { db.recordingChatMessageDao().messages(recordingId) }
    suspend fun addChatMessage(recordingId: String, role: String, text: String, sourceStarts: List<Long>) = withContext(Dispatchers.IO) {
        db.recordingChatMessageDao().insert(com.rassvet.essential.data.local.RecordingChatMessageEntity(
            recordingId = recordingId, role = role, text = text, sourceStarts = sourceStarts.joinToString(","),
            createdAt = System.currentTimeMillis()))
    }
    suspend fun clearChat(recordingId: String) = withContext(Dispatchers.IO) { db.recordingChatMessageDao().deleteForRecording(recordingId) }
    suspend fun segmentsOnce(recordingId: String) = withContext(Dispatchers.IO) { db.recordingDao().segments(recordingId) }
    suspend fun speakersOnce(recordingId: String) = withContext(Dispatchers.IO) { db.speakerDao().speakers(recordingId) }
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
                processingStatus = "queued",
            )
            db.recordingDao().insert(recording)
            RecordingProcessor.enqueue(context, db, id)
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
            processingStatus = RecordingProcessingStatus.Queued.storedValue,
        )
        db.recordingDao().insert(recording)
        RecordingProcessor.enqueue(context, db, recording.id)
        recording
    }

    /** Re-run the full pipeline (retry after failure or reprocess a finished recording). */
    suspend fun reprocess(id: String) = withContext(Dispatchers.IO) {
        db.recordingDao().updateStatus(id, "queued", 0, System.currentTimeMillis())
        RecordingProcessor.enqueue(context, db, id)
    }

    /**
     * Recordings whose capture died with the process: a WAV in files/recordings that is not in the database.
     * The service patches the header every ~2 s, so the audio up to the last patch (plus anything after it,
     * recomputed here from the file length) is recovered and queued for normal processing.
     */
    suspend fun recoverOrphanedRecordings(): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "recordings")
        val known = db.recordingDao().observeAllOnce().map { it.audioPath }.toSet()
        val active = com.rassvet.essential.service.CaptureSession.state.value.file?.absolutePath
        var recovered = 0
        dir.listFiles { f -> f.isFile && f.extension == "wav" }.orEmpty().forEach { f ->
            if (f.absolutePath in known || f.absolutePath == active) return@forEach
            if (System.currentTimeMillis() - f.lastModified() < 20_000) return@forEach   // still being written
            if (f.length() <= 44 + 16_000) { f.delete(); return@forEach }
            runCatching {
                java.io.RandomAccessFile(f, "rw").use { raf ->
                    raf.seek(24); val rate = Integer.reverseBytes(raf.readInt()).takeIf { it in 8000..96000 } ?: 16_000
                    com.rassvet.essential.service.RecordingService.patchWavHeader(raf, rate, (raf.length() - 44) / 2)
                }
                registerRecordedAudio(f, "Recovered recording")
                recovered++
            }.onFailure { android.util.Log.w("RecordingRepository", "orphan recovery failed for ${f.name}", it) }
        }
        recovered
    }

    suspend fun recoverInterrupted() = withContext(Dispatchers.IO) {
        recoverOrphanedRecordings()
        RecordingProcessor.recoverInterrupted(db)
        db.recordingDao().observeAllOnce().filter { it.processingStatus == "awaiting_asr" }
            .forEach { reprocess(it.id) }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val recording = db.recordingDao().get(id) ?: return@withContext
        RecordingProcessor.cancel(id)
        db.withTransaction {
            db.recordingDao().deleteSegments(id)
            db.speakerDao().deleteForRecording(id)
            db.summaryDao().deleteForRecording(id)
            db.recordingChatSessionDao().deleteForRecording(id)
            db.recordingChatMessageDao().deleteForRecording(id)
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
