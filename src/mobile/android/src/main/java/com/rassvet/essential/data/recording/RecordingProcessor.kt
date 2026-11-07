package com.rassvet.essential.data.recording

import android.content.Context
import androidx.room.withTransaction
import com.rassvet.essential.data.local.AppDatabase
import com.rassvet.essential.data.local.SpeakerEntity
import com.rassvet.essential.data.local.TranscriptSegmentEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single process-wide queue: Live, Import and retry all go through the same pipeline, one job at a time. */
object RecordingProcessor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Mutex()
    private val jobs = HashMap<String, Job>()

    fun enqueue(context: Context, db: AppDatabase, recordingId: String) {
        synchronized(jobs) {
            if (jobs[recordingId]?.isActive == true) return
            jobs[recordingId] = scope.launch { gate.withLock { process(context.applicationContext, db, recordingId) } }
        }
    }

    fun cancel(recordingId: String) = synchronized(jobs) { jobs.remove(recordingId)?.cancel() }

    /** Anything left queued/processing after a process death is marked failed so the user can retry. */
    suspend fun recoverInterrupted(db: AppDatabase) {
        val dao = db.recordingDao()
        dao.interrupted().forEach { dao.updateStatus(it.id, "failed", 0, System.currentTimeMillis()) }
    }

    private suspend fun process(context: Context, db: AppDatabase, id: String) {
        val dao = db.recordingDao()
        val rec = dao.get(id) ?: return
        val pipeline = SpeechPipeline(context)
        suspend fun status(s: String, p: Int) = dao.updateStatus(id, s, p, System.currentTimeMillis())
        if (!pipeline.modelsAvailable()) { status("failed", 0); return }
        try {
            status("processing", 1)
            val weights = mapOf("decode" to (0 to 8), "enhance" to (8 to 20), "vad" to (20 to 22), "asr" to (22 to 70),
                "diarization" to (70 to 85), "separation" to (85 to 97), "align" to (97 to 99))
            var last = -1
            val prefs = com.rassvet.essential.data.local.VaultPreferencesRepository(context)
            val options = PipelineOptions(
                denoise = prefs.speechDenoiseEnabled.first(),
                separate = prefs.speechSeparationEnabled.first(),
            )
            val segments = pipeline.run(rec.audioPath, options = options, onProgress = { stage, f ->
                val (a, b) = weights[stage] ?: (0 to 0)
                val p = (a + (b - a) * f).toInt().coerceIn(1, 99)
                if (p != last) { last = p; kotlinx.coroutines.runBlocking { status("processing", p) } }
            })
            val speakerIds = segments.map { it.speaker }.distinct().sorted()
            val rawId = { n: Int -> "speaker_${speakerIds.indexOf(n) + 1}" }
            db.withTransaction {
                dao.deleteSegments(id)
                db.speakerDao().deleteForRecording(id)
                speakerIds.forEachIndexed { i, _ ->
                    db.speakerDao().upsert(SpeakerEntity(id, "speaker_${i + 1}", "Speaker ${i + 1}", i))
                }
                if (segments.isNotEmpty()) dao.insertSegments(segments.map {
                    TranscriptSegmentEntity(recordingId = id, speakerId = rawId(it.speaker), startMs = it.startMs,
                        endMs = it.endMs, text = it.text, overlap = it.overlap)
                })
                dao.setModels(id, SpeechModelPaths.ASR_ID, SpeechModelPaths.DIARIZATION_ID, "ru")
                dao.updateStatus(id, "completed", 100, System.currentTimeMillis())
            }
        } catch (e: CancellationException) {
            status("cancelled", 0); throw e
        } catch (e: Throwable) {
            android.util.Log.e("RecordingProcessor", "processing failed", e)
            status("failed", 0)
        }
    }
}
