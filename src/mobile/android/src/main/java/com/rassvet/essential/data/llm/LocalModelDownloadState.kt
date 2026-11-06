package com.rassvet.essential.data.llm

data class LocalModelDownloadState(
    val fileName: String? = null,
    val modelLabel: String? = null,
    val status: Status = Status.Idle,
    val progress: Float? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
) {
    enum class Status {
        Idle,
        Downloading,
        Paused,
        Verifying,
        Completed,
        Failed,
        Cancelled,
    }

    val isActive: Boolean =
        status == Status.Downloading || status == Status.Paused || status == Status.Verifying

    companion object {
        fun idle() = LocalModelDownloadState()
    }
}

class LocalModelDownloadControl {
    @Volatile var paused: Boolean = false

    @Volatile var cancelled: Boolean = false

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun cancel() {
        cancelled = true
        paused = false
    }
}


