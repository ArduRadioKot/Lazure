package com.rassvet.essential.data.recording

import android.content.Context

enum class SpeechModelCategory { Asr, Diarization, Denoise, Separation }

/** A speech model that ships in the APK. `assets` are all files the model needs; the entry is installed only if all exist. */
data class SpeechModelEntry(
    val id: String,
    val category: SpeechModelCategory,
    val title: String,
    val version: String,
    val languages: String,
    val description: String,
    val license: String,
    val assets: List<String>,
    val sizeBytes: Long,
    val installed: Boolean,
)

/** Reflects what is really in the APK: nothing is listed that does not exist, nothing installed that is missing. */
object SpeechModelCatalog {
    private data class Def(val id: String, val cat: SpeechModelCategory, val title: String, val version: String, val langs: String,
                           val desc: String, val license: String, val assets: List<String>)

    private val defs = listOf(
        Def(SpeechModelPaths.ASR_ID, SpeechModelCategory.Asr, "GigaAM v3 CTC", "2025-12 · int8", "ru",
            "Russian speech recognition with punctuation (Sber GigaAM, NeMo CTC export).", "MIT",
            listOf(SpeechModelPaths.ASR_MODEL, SpeechModelPaths.ASR_TOKENS)),
        Def("silero-vad", SpeechModelCategory.Asr, "Silero VAD", "v4", "any",
            "Finds speech segments before recognition.", "MIT", listOf(SpeechModelPaths.VAD)),
        Def(SpeechModelPaths.DIARIZATION_ID, SpeechModelCategory.Diarization, "pyannote 3.0 + CAM++", "seg 3.0 · CAM++ zh/en", "language-independent",
            "Who speaks when: pyannote segmentation, CAM++ speaker embeddings, clustering.", "MIT / Apache-2.0",
            listOf(SpeechModelPaths.SEGMENTATION, SpeechModelPaths.EMBEDDING)),
        Def("dtln", SpeechModelCategory.Denoise, "DTLN", "model_1/2", "any",
            "Noise suppression used to make speech detection robust in noisy rooms.", "MIT",
            listOf(SpeechModelPaths.DTLN_1, SpeechModelPaths.DTLN_2)),
        Def("mossformer2-ss-16k-int8", SpeechModelCategory.Separation, "MossFormer2 SS", "16 kHz · int8", "any",
            "Separates two overlapping voices so both are transcribed. Slower than real time on phones.", "Apache-2.0",
            listOf(SpeechModelPaths.SEPARATOR)),
    )

    fun entries(context: Context): List<SpeechModelEntry> = defs.map { d ->
        // openFd only works for uncompressed assets (.onnx); small text files like tokens.txt are compressed in the APK
        val sizes = d.assets.map { a ->
            runCatching { context.assets.openFd(a).use { it.length } }.getOrNull()
                ?: runCatching { context.assets.open(a).use { it.readBytes().size.toLong() } }.getOrNull()
        }
        SpeechModelEntry(d.id, d.cat, d.title, d.version, d.langs, d.desc, d.license, d.assets,
            sizes.filterNotNull().sum(), sizes.all { it != null })
    }
}
