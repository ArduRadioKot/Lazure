package com.rassvet.essential.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY created_at DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings")
    suspend fun observeAllOnce(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun get(id: String): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE recordings SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long): Int

    @Query("UPDATE recordings SET processing_status = :status, processing_progress = :progress, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, progress: Int, updatedAt: Long): Int

    @Query("SELECT * FROM recordings WHERE processing_status IN ('queued','processing')")
    suspend fun interrupted(): List<RecordingEntity>

    @Query("UPDATE recordings SET selected_asr_model_id = :asr, selected_diarization_model_id = :diar, language = :lang WHERE id = :id")
    suspend fun setModels(id: String, asr: String, diar: String, lang: String): Int

    @Query("SELECT * FROM transcript_segments WHERE recording_id = :recordingId ORDER BY start_ms")
    suspend fun segments(recordingId: String): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM transcript_segments WHERE recording_id = :recordingId ORDER BY start_ms")
    fun observeSegments(recordingId: String): Flow<List<TranscriptSegmentEntity>>

    @Query("UPDATE transcript_segments SET text = :text, original_text = COALESCE(original_text, :originalText) WHERE id = :id AND recording_id = :recordingId")
    suspend fun updateSegmentText(recordingId: String, id: Long, text: String, originalText: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query("DELETE FROM transcript_segments WHERE recording_id = :recordingId")
    suspend fun deleteSegments(recordingId: String)
}

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM speakers WHERE recording_id = :recordingId ORDER BY color_index")
    suspend fun speakers(recordingId: String): List<SpeakerEntity>

    @Query("SELECT * FROM speakers WHERE recording_id = :recordingId ORDER BY color_index")
    fun observeSpeakers(recordingId: String): Flow<List<SpeakerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(speaker: SpeakerEntity)

    @Query("UPDATE speakers SET display_name = :displayName WHERE recording_id = :recordingId AND raw_speaker_id = :rawSpeakerId")
    suspend fun rename(recordingId: String, rawSpeakerId: String, displayName: String): Int

    @Query("DELETE FROM speakers WHERE recording_id = :recordingId")
    suspend fun deleteForRecording(recordingId: String)
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM recording_summaries WHERE recording_id = :recordingId ORDER BY created_at DESC")
    fun observe(recordingId: String): Flow<List<SummaryEntity>>

    @Insert
    suspend fun insert(summary: SummaryEntity): Long

    @Query("DELETE FROM recording_summaries WHERE recording_id = :recordingId")
    suspend fun deleteForRecording(recordingId: String)
}

@Dao
interface RecordingChatSessionDao {
    @Query("SELECT * FROM recording_chat_sessions WHERE recording_id = :recordingId ORDER BY created_at DESC")
    fun observe(recordingId: String): Flow<List<RecordingChatSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: RecordingChatSessionEntity)

    @Query("DELETE FROM recording_chat_sessions WHERE id = :id AND recording_id = :recordingId")
    suspend fun delete(id: String, recordingId: String)

    @Query("DELETE FROM recording_chat_sessions WHERE recording_id = :recordingId")
    suspend fun deleteForRecording(recordingId: String)
}

@Dao
interface RecordingChatMessageDao {
    @Query("SELECT * FROM recording_chat_messages WHERE recording_id = :recordingId ORDER BY created_at, id")
    suspend fun messages(recordingId: String): List<RecordingChatMessageEntity>

    @Insert
    suspend fun insert(message: RecordingChatMessageEntity): Long

    @Query("DELETE FROM recording_chat_messages WHERE recording_id = :recordingId")
    suspend fun deleteForRecording(recordingId: String)
}
