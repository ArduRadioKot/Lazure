package com.rassvet.essential.data.llm

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GgufRepository(
    private val context: Context,
) {
    private val http =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    data class ModelVerification(
        val fileName: String,
        val isComplete: Boolean,
        val sizeBytes: Long = 0L,
        val reason: String? = null,
    )

    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) {
        val fraction: Float? =
            if (totalBytes != null && totalBytes > 0L) {
                (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    fun modelsDir(): File =
        File(context.filesDir, "llm_models").apply {
            if (!exists()) mkdirs()
        }

    fun partFileForName(fileName: String): File = File(modelsDir(), "$fileName.part")

    fun listLocalModelFileNames(): List<String> =
        modelsDir()
            .listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    LocalModelFormats.isLocalModelFileName(file.name) &&
                    !file.name.endsWith(PART_SUFFIX)
            }
            .map { it.name }
            .sorted()

    fun listGgufFileNames(): List<String> = listLocalModelFileNames()

    fun listVerifiedLocalModelFileNames(): List<String> =
        listLocalModelFileNames().filter { fileName ->
            verifyModel(fileName).isComplete
        }

    fun listVerifiedGgufFileNames(): List<String> = listVerifiedLocalModelFileNames()

    fun listPausedPartials(): List<String> =
        modelsDir()
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(PART_SUFFIX) }
            .map { it.name.removeSuffix(PART_SUFFIX) }
            .filter { LocalModelFormats.isLocalModelFileName(it) }
            .sorted()

    fun fileForName(fileName: String): File = File(modelsDir(), fileName)

    fun partialBytes(fileName: String): Long = partFileForName(fileName).length().coerceAtLeast(0L)

    fun verifyModel(
        fileName: String,
        preset: LocalModelCatalog.Preset? = LocalModelCatalog.presetForFileName(fileName),
    ): ModelVerification {
        val file = fileForName(fileName)
        if (!file.isFile) {
            return ModelVerification(fileName, false, reason = "Файл не найден")
        }
        val size = file.length()
        if (size <= 0L) {
            return ModelVerification(fileName, false, sizeBytes = size, reason = "Пустой файл")
        }
        if (!fileName.endsWith(LocalModelFormats.LITERTLM_EXT, ignoreCase = true)) {
            return ModelVerification(fileName, false, sizeBytes = size, reason = "Неподдерживаемый формат")
        }
        val expected = preset?.expectedBytes
        if (expected != null && expected > 0L && size < minAcceptedBytes(expected)) {
            return ModelVerification(
                fileName,
                false,
                sizeBytes = size,
                reason = "Файл неполный: ${LocalModelCatalog.formatDownloadSize(size)} из ${LocalModelCatalog.formatDownloadSize(expected)}",
            )
        }
        if (size < MIN_MODEL_BYTES) {
            return ModelVerification(
                fileName,
                false,
                sizeBytes = size,
                reason = "Файл слишком мал для модели LiteRT-LM",
            )
        }
        return ModelVerification(fileName, true, sizeBytes = size)
    }

    fun pruneIncompleteModels(): Int {
        var removed = 0
        listLocalModelFileNames().forEach { name ->
            if (!verifyModel(name).isComplete) {
                if (deleteModel(name)) removed++
            }
        }
        listPausedPartials().forEach { name ->
            if (deletePartial(name)) removed++
        }
        return removed
    }

    suspend fun downloadFromUrl(
        url: String,
        targetFileName: String,
        expectedBytes: Long? = null,
        sha256Hex: String? = null,
        onProgress: (Float) -> Unit,
    ): Result<Unit> =
        downloadResumable(
            url = url,
            targetFileName = targetFileName,
            expectedBytes = expectedBytes,
            sha256Hex = sha256Hex,
            control = LocalModelDownloadControl(),
            onProgress = { progress ->
                onProgress(progress.fraction ?: -1f)
            },
        )

    suspend fun downloadResumable(
        url: String,
        targetFileName: String,
        expectedBytes: Long? = null,
        sha256Hex: String? = null,
        control: LocalModelDownloadControl,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                downloadResumableInternal(
                    url = url,
                    targetFileName = targetFileName,
                    expectedBytes = expectedBytes,
                    sha256Hex = sha256Hex,
                    control = control,
                    onProgress = onProgress,
                )
                Result.success(Unit)
            } catch (e: CancellationException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun downloadResumableInternal(
        url: String,
        targetFileName: String,
        expectedBytes: Long? = null,
        sha256Hex: String? = null,
        control: LocalModelDownloadControl,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val trimmedUrl = url.trim()
        val dest = fileForName(targetFileName)
        val part = partFileForName(targetFileName)
        val wantSha = sha256Hex?.trim()?.lowercase()?.filter { it in "0123456789abcdef" }
        val totalHint = expectedBytes?.takeIf { it > 0L }

        if (dest.exists()) dest.delete()
        var existing = if (part.isFile) part.length().coerceAtLeast(0L) else 0L

        val md: MessageDigest? =
            if (!wantSha.isNullOrEmpty()) MessageDigest.getInstance("SHA-256") else null
        if (md != null && existing > 0L) {
            hashFile(part, md)
        }

        downloadLoop@ while (coroutineContext.isActive) {
            if (control.cancelled) throw CancellationException("download cancelled")
            awaitIfPaused(control)

            val requestBuilder =
                Request.Builder()
                    .url(trimmedUrl)
                    .header("User-Agent", "Lazure-Android/1.0")
                    .header("Accept", "*/*")
            if (existing > 0L) {
                requestBuilder.header("Range", "bytes=$existing-")
            }
            val response = http.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                if (control.cancelled) throw CancellationException("download cancelled")

                when (resp.code) {
                    200 -> {
                        if (existing > 0L) {
                            part.delete()
                            existing = 0L
                            md?.reset()
                        }
                    }
                    206 -> Unit
                    416 -> {
                        if (existing > 0L && totalHint != null && existing >= minAcceptedBytes(totalHint)) {
                            break@downloadLoop
                        }
                        part.delete()
                        existing = 0L
                        md?.reset()
                        return@use
                    }
                    else -> {
                        val err = resp.body?.string()?.take(200) ?: "HTTP ${resp.code}"
                        error("Сервер вернул ${resp.code}: $err")
                    }
                }

                val contentRangeTotal =
                    resp.header("Content-Range")
                        ?.substringAfter("/")
                        ?.trim()
                        ?.toLongOrNull()
                val contentLength = resp.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
                val totalBytes =
                    when {
                        contentRangeTotal != null && contentRangeTotal > 0L -> contentRangeTotal
                        existing > 0L && resp.code == 206 && contentLength > 0L ->
                            existing + contentLength
                        totalHint != null -> totalHint
                        contentLength > 0L -> contentLength
                        else -> null
                    }

                onProgress(DownloadProgress(existing, totalBytes))

                resp.body?.byteStream()?.use { input ->
                    part.outputStream().buffered(BUFFER_SIZE).use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        while (coroutineContext.isActive) {
                            if (control.cancelled) throw CancellationException("download cancelled")
                            awaitIfPaused(control)
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            md?.update(buf, 0, n)
                            existing += n
                            onProgress(DownloadProgress(existing, totalBytes))
                        }
                        out.flush()
                    }
                } ?: error("Пустой ответ сервера")
            }

            break@downloadLoop
        }

        awaitIfPaused(control)
        if (control.cancelled) throw CancellationException("download cancelled")

        val downloaded = part.length()
        if (totalHint != null && downloaded < minAcceptedBytes(totalHint)) {
            error(
                "Файл неполный: ${LocalModelCatalog.formatDownloadSize(downloaded)} " +
                    "из ${LocalModelCatalog.formatDownloadSize(totalHint)}",
            )
        }

        if (md != null && !wantSha.isNullOrEmpty()) {
            val got = md.digest().joinToString("") { b -> "%02x".format(b) }
            if (got != wantSha) {
                error("SHA-256 не совпадает")
            }
        }

        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }

        val preset = LocalModelCatalog.presetForFileName(targetFileName)
        val verification = verifyModel(targetFileName, preset)
        if (!verification.isComplete) {
            dest.delete()
            error(verification.reason ?: "Модель не прошла проверку")
        }

        onProgress(DownloadProgress(dest.length(), dest.length()))
    }

    fun deletePartial(fileName: String): Boolean {
        val part = partFileForName(fileName)
        return !part.exists() || part.delete()
    }

    fun deleteModel(fileName: String): Boolean {
        deletePartial(fileName)
        return fileForName(fileName).delete()
    }

    private suspend fun awaitIfPaused(control: LocalModelDownloadControl) {
        while (control.paused && !control.cancelled) {
            delay(250)
        }
    }

    private fun hashFile(file: File, md: MessageDigest) {
        file.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
    }

    private fun minAcceptedBytes(expected: Long): Long = (expected * 0.98).toLong()

    companion object {
        private const val MIN_MODEL_BYTES = 100L * 1024L * 1024L
        private const val BUFFER_SIZE = 256 * 1024
        private const val PART_SUFFIX = ".part"
    }
}


