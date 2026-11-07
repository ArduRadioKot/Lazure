package com.rassvet.essential.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recordings", indices = [Index(value = ["created_at"])])
data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "audio_path") val audioPath: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "processing_status") val processingStatus: String,
    @ColumnInfo(name = "processing_progress", defaultValue = "0") val processingProgress: Int = 0,
    val language: String? = null,
    @ColumnInfo(name = "selected_asr_model_id") val selectedAsrModelId: String? = null,
    @ColumnInfo(name = "selected_diarization_model_id") val selectedDiarizationModelId: String? = null,
)

@Entity(
    tableName = "transcript_segments",
    indices = [Index(value = ["recording_id", "start_ms"])],
)
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recording_id") val recordingId: String,
    @ColumnInfo(name = "speaker_id") val speakerId: String,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    val text: String,
    @ColumnInfo(name = "original_text") val originalText: String? = null,
    /** True when this stretch overlaps another speaker (interruption / simultaneous speech). */
    @ColumnInfo(name = "overlap", defaultValue = "0") val overlap: Boolean = false,
)

@Entity(
    tableName = "speakers",
    primaryKeys = ["recording_id", "raw_speaker_id"],
    indices = [Index(value = ["recording_id"])],
)
data class SpeakerEntity(
    @ColumnInfo(name = "recording_id") val recordingId: String,
    @ColumnInfo(name = "raw_speaker_id") val rawSpeakerId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
)

@Entity(tableName = "recording_summaries", indices = [Index(value = ["recording_id", "created_at"])])
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recording_id") val recordingId: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "model_id") val modelId: String,
)

@Entity(tableName = "recording_chat_sessions", indices = [Index(value = ["recording_id", "created_at"])])
data class RecordingChatSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recording_id") val recordingId: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "recording_chat_messages", indices = [Index(value = ["recording_id", "created_at"])])
data class RecordingChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recording_id") val recordingId: String,
    val role: String,
    val text: String,
    /** Comma-separated start_ms of the transcript segments that were retrieved as sources. */
    @ColumnInfo(name = "source_starts") val sourceStarts: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
