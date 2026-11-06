package com.rassvet.essential.data.recording

import com.rassvet.essential.data.local.RecordingEntity
import com.rassvet.essential.data.local.SpeakerEntity
import com.rassvet.essential.data.local.SummaryEntity
import com.rassvet.essential.data.local.TranscriptSegmentEntity
import kotlinx.coroutines.flow.Flow

/** Persisted states are deliberately explicit; unsupported inference must stay awaiting_asr. */
enum class RecordingProcessingStatus(val storedValue: String) {
    AwaitingAsr("awaiting_asr"),
    Queued("queued"),
    Processing("processing"),
    Completed("completed"),
    Failed("failed"),
    Cancelled("cancelled");

    fun canTransitionTo(next: RecordingProcessingStatus): Boolean = next in transitions.getValue(this)

    companion object {
        private val transitions = mapOf(
            AwaitingAsr to setOf(Queued, Failed, Cancelled),
            Queued to setOf(Processing, Failed, Cancelled),
            Processing to setOf(Completed, Failed, Cancelled),
            Completed to setOf(Queued),
            Failed to setOf(Queued, Cancelled),
            Cancelled to setOf(Queued),
        )

        fun fromStoredValue(value: String): RecordingProcessingStatus? = entries.firstOrNull { it.storedValue == value }
    }
}

data class AudioBuffer(
    val samples: FloatArray,
    val sampleRateHz: Int,
    val channels: Int,
) {
    init {
        require(sampleRateHz > 0) { "Sample rate must be positive" }
        require(channels > 0) { "Channel count must be positive" }
    }
}

data class SpeechUtterance(val startMs: Long, val endMs: Long, val text: String, val confidence: Float? = null)
data class SpeakerTurn(val startMs: Long, val endMs: Long, val speakerId: String, val confidence: Float? = null)
data class RecordingPipelineProgress(val stage: String, val progress: Float)
data class RecordingPipelineResult(val utterances: List<TranscriptSegmentEntity>, val speakers: List<SpeakerEntity>)

interface AudioDecoder {
    suspend fun decode(audioPath: String): AudioBuffer
}

interface AudioPreprocessor {
    suspend fun prepare(audio: AudioBuffer, targetSampleRateHz: Int): AudioBuffer
}

interface VoiceActivityDetector {
    suspend fun speechIntervals(audio: AudioBuffer): List<LongRange>
}

interface SpeechRecognitionEngine {
    val modelId: String
    suspend fun transcribe(audio: AudioBuffer, intervalOffsetMs: Long = 0): List<SpeechUtterance>
}

interface SpeakerDiarizationEngine {
    val modelId: String
    suspend fun identifySpeakers(audio: AudioBuffer): List<SpeakerTurn>
}

interface TranscriptAlignmentEngine {
    suspend fun align(utterances: List<SpeechUtterance>, speakerTurns: List<SpeakerTurn>): RecordingPipelineResult
}

interface TranscriptPostProcessor {
    suspend fun process(segments: List<TranscriptSegmentEntity>): List<TranscriptSegmentEntity>
}

interface RecordingProcessingCoordinator {
    fun process(recordingId: String): Flow<RecordingPipelineProgress>
    suspend fun cancel(recordingId: String)
}

interface AudioRecorder {
    val state: Flow<RecorderState>
    suspend fun start(outputPath: String)
    suspend fun pause()
    suspend fun resume()
    suspend fun stop(): Long
}

enum class RecorderState { Idle, Recording, Paused, Stopped, Failed }

interface TranscriptRepository {
    suspend fun recording(id: String): RecordingEntity?
    suspend fun segments(recordingId: String): List<TranscriptSegmentEntity>
    suspend fun replaceSegments(recordingId: String, segments: List<TranscriptSegmentEntity>)
    suspend fun speakers(recordingId: String): List<SpeakerEntity>
    suspend fun saveSummary(summary: SummaryEntity)
}

interface TranscriptExporter {
    suspend fun exportJson(recordingId: String): String
}

interface ModelRepository {
    fun installedModels(): Flow<List<InstalledModel>>
    suspend fun select(modelId: String)
    suspend fun remove(modelId: String): Result<Unit>
}

data class InstalledModel(
    val id: String,
    val displayName: String,
    val kind: ModelKind,
    val version: String?,
    val sizeBytes: Long?,
    val languages: Set<String>,
    val bundled: Boolean,
    val installed: Boolean,
    val selected: Boolean,
)

enum class ModelKind { Llm, Asr, Diarization }
