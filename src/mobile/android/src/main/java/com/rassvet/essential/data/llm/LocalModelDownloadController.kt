package com.rassvet.essential.data.llm

import android.content.Context
import com.rassvet.essential.data.local.VaultPreferencesRepository
import com.rassvet.essential.service.LocalModelDownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalModelDownloadController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val repository = GgufRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(LocalModelDownloadState.idle())
    val state: StateFlow<LocalModelDownloadState> = _state.asStateFlow()

    private var control: LocalModelDownloadControl? = null
    private var onFinished: ((Result<Unit>, LocalModelCatalog.Preset) -> Unit)? = null

    init {
        scope.launch(Dispatchers.IO) {
            restorePausedPartial()
        }
    }

    fun start(
        preset: LocalModelCatalog.Preset,
        allowCellular: Boolean,
        onFinished: (Result<Unit>, LocalModelCatalog.Preset) -> Unit,
    ) {
        if (_state.value.status == LocalModelDownloadState.Status.Downloading &&
            _state.value.fileName == preset.fileName
        ) {
            return
        }
        if (_state.value.isActive &&
            _state.value.fileName != null &&
            _state.value.fileName != preset.fileName
        ) {
            return
        }

        scope.launch {
            val check =
                withContext(Dispatchers.IO) {
                    GgufDownloadPolicy.check(appContext, preset, allowCellular)
                }
            if (!check.allowed) {
                val message =
                    when (check.reason) {
                        GgufDownloadPolicy.BlockReason.CellularWithoutConsent -> "wifi_required"
                        GgufDownloadPolicy.BlockReason.InsufficientStorage -> "storage_required"
                        GgufDownloadPolicy.BlockReason.NoNetwork -> "network_required"
                        null -> "blocked"
                    }
                _state.value =
                    LocalModelDownloadState(
                        fileName = preset.fileName,
                        modelLabel = preset.label,
                        status = LocalModelDownloadState.Status.Failed,
                        errorMessage = message,
                    )
                onFinished(Result.failure(IllegalStateException(message)), preset)
                return@launch
            }

            this@LocalModelDownloadController.onFinished = onFinished
            val downloadControl = LocalModelDownloadControl()
            control = downloadControl

            val partial = withContext(Dispatchers.IO) { repository.partialBytes(preset.fileName) }
            val total = preset.expectedBytes
            _state.value =
                LocalModelDownloadState(
                    fileName = preset.fileName,
                    modelLabel = preset.label,
                    status = LocalModelDownloadState.Status.Downloading,
                    progress = progressOf(partial, total),
                    bytesDownloaded = partial,
                    totalBytes = total,
                )

            LocalModelDownloadService.start(appContext, preset.fileName, preset.label)

            val result =
                withContext(Dispatchers.IO) {
                    repository.downloadResumable(
                        url = preset.url,
                        targetFileName = preset.fileName,
                        expectedBytes = preset.expectedBytes,
                        sha256Hex = preset.sha256Hex,
                        control = downloadControl,
                        onProgress = { progress -> publishProgress(preset, progress, downloadControl) },
                    )
                }

            LocalModelDownloadService.stop(appContext)
            control = null

            when {
                result.isSuccess -> {
                    _state.value =
                        LocalModelDownloadState(
                            fileName = preset.fileName,
                            modelLabel = preset.label,
                            status = LocalModelDownloadState.Status.Completed,
                            progress = 1f,
                            bytesDownloaded = preset.expectedBytes ?: repository.fileForName(preset.fileName).length(),
                            totalBytes = preset.expectedBytes,
                        )
                    onFinished(result, preset)
                    _state.value = LocalModelDownloadState.idle()
                }
                result.exceptionOrNull() is CancellationException -> {
                    val paused = downloadControl.paused || repository.partialBytes(preset.fileName) > 0L
                    if (paused && !downloadControl.cancelled) {
                        val bytes = repository.partialBytes(preset.fileName)
                        _state.value =
                            LocalModelDownloadState(
                                fileName = preset.fileName,
                                modelLabel = preset.label,
                                status = LocalModelDownloadState.Status.Paused,
                                progress = progressOf(bytes, preset.expectedBytes),
                                bytesDownloaded = bytes,
                                totalBytes = preset.expectedBytes,
                            )
                    } else {
                        withContext(Dispatchers.IO) { repository.deletePartial(preset.fileName) }
                        _state.value =
                            LocalModelDownloadState(
                                fileName = preset.fileName,
                                modelLabel = preset.label,
                                status = LocalModelDownloadState.Status.Cancelled,
                            )
                        _state.value = LocalModelDownloadState.idle()
                    }
                }
                else -> {
                    _state.value =
                        LocalModelDownloadState(
                            fileName = preset.fileName,
                            modelLabel = preset.label,
                            status = LocalModelDownloadState.Status.Failed,
                            errorMessage = result.exceptionOrNull()?.message,
                            bytesDownloaded = repository.partialBytes(preset.fileName),
                            totalBytes = preset.expectedBytes,
                        )
                    onFinished(result, preset)
                }
            }
            this@LocalModelDownloadController.onFinished = null
        }
    }

    fun resume(
        preset: LocalModelCatalog.Preset,
        allowCellular: Boolean,
        onFinished: (Result<Unit>, LocalModelCatalog.Preset) -> Unit,
    ) {
        if (_state.value.fileName == preset.fileName &&
            _state.value.status == LocalModelDownloadState.Status.Paused &&
            control != null
        ) {
            this.onFinished = onFinished
            control?.resume()
            _state.value = _state.value.copy(status = LocalModelDownloadState.Status.Downloading)
            LocalModelDownloadService.start(appContext, preset.fileName, preset.label)
            return
        }
        start(preset, allowCellular, onFinished)
    }

    fun resumeFromNotification(preset: LocalModelCatalog.Preset) {
        if (_state.value.fileName != preset.fileName ||
            _state.value.status != LocalModelDownloadState.Status.Paused
        ) {
            return
        }
        val activeControl = control
        if (activeControl != null) {
            activeControl.resume()
            _state.value = _state.value.copy(status = LocalModelDownloadState.Status.Downloading)
            LocalModelDownloadService.start(appContext, preset.fileName, preset.label)
            return
        }
        scope.launch {
            val allowCellular =
                allowCellular(VaultPreferencesRepository(appContext))
            start(preset, allowCellular) { _, _ -> }
        }
    }

    fun pause() {
        val current = _state.value
        if (current.status != LocalModelDownloadState.Status.Downloading) return
        control?.pause()
        _state.value =
            current.copy(
                status = LocalModelDownloadState.Status.Paused,
            )
        LocalModelDownloadService.update(appContext, current.fileName.orEmpty(), current.modelLabel.orEmpty(), paused = true)
    }

    fun cancel() {
        val current = _state.value
        control?.cancel()
        scope.launch(Dispatchers.IO) {
            current.fileName?.let { repository.deletePartial(it) }
        }
        LocalModelDownloadService.stop(appContext)
        _state.value = LocalModelDownloadState.idle()
        onFinished = null
        control = null
    }

    private fun publishProgress(
        preset: LocalModelCatalog.Preset,
        progress: GgufRepository.DownloadProgress,
        downloadControl: LocalModelDownloadControl,
    ) {
        val status =
            if (downloadControl.paused) {
                LocalModelDownloadState.Status.Paused
            } else {
                LocalModelDownloadState.Status.Downloading
            }
        _state.value =
            LocalModelDownloadState(
                fileName = preset.fileName,
                modelLabel = preset.label,
                status = status,
                progress = progress.fraction,
                bytesDownloaded = progress.bytesDownloaded,
                totalBytes = progress.totalBytes ?: preset.expectedBytes,
            )
        LocalModelDownloadService.update(
            appContext,
            preset.fileName,
            preset.label,
            bytesDownloaded = progress.bytesDownloaded,
            totalBytes = progress.totalBytes ?: preset.expectedBytes,
            paused = downloadControl.paused,
        )
    }

    private suspend fun restorePausedPartial() {
        val partials = repository.listPausedPartials()
        if (partials.isEmpty()) return
        val fileName = partials.last()
        val preset = LocalModelCatalog.presetForFileName(fileName) ?: return
        val bytes = repository.partialBytes(fileName)
        _state.value =
            LocalModelDownloadState(
                fileName = fileName,
                modelLabel = preset.label,
                status = LocalModelDownloadState.Status.Paused,
                progress = progressOf(bytes, preset.expectedBytes),
                bytesDownloaded = bytes,
                totalBytes = preset.expectedBytes,
            )
    }

    private fun progressOf(bytes: Long, total: Long?): Float? =
        if (total != null && total > 0L) {
            (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    companion object {
        @Volatile private var instance: LocalModelDownloadController? = null

        fun get(context: Context): LocalModelDownloadController =
            instance ?: synchronized(this) {
                instance ?: LocalModelDownloadController(context.applicationContext).also { instance = it }
            }

        suspend fun allowCellular(prefs: VaultPreferencesRepository): Boolean =
            prefs.ggufAllowCellular.first()
    }
}


