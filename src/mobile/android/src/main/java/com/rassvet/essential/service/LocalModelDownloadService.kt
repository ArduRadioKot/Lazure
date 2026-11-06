package com.rassvet.essential.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rassvet.essential.MainActivity
import com.rassvet.essential.R
import com.rassvet.essential.data.llm.LocalModelCatalog
import com.rassvet.essential.data.llm.LocalModelDownloadController

class LocalModelDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> LocalModelDownloadController.get(this).pause()
            ACTION_RESUME -> {
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
                val preset = LocalModelCatalog.presetForFileName(fileName) ?: return START_NOT_STICKY
                LocalModelDownloadController.get(this).resumeFromNotification(preset)
            }
            ACTION_CANCEL -> LocalModelDownloadController.get(this).cancel()
            ACTION_START, null -> {
                val fileName = intent?.getStringExtra(EXTRA_FILE_NAME).orEmpty()
                val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
                NotificationHelper.ensureChannel(this)
                startForeground(
                    NOTIFICATION_ID,
                    NotificationHelper.build(this, fileName, label, 0L, null, paused = false),
                )
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "local_model_download"
        private const val NOTIFICATION_ID = 42

        const val ACTION_START = "com.rassvet.essential.download.START"
        const val ACTION_PAUSE = "com.rassvet.essential.download.PAUSE"
        const val ACTION_RESUME = "com.rassvet.essential.download.RESUME"
        const val ACTION_CANCEL = "com.rassvet.essential.download.CANCEL"
        const val EXTRA_FILE_NAME = "fileName"
        const val EXTRA_LABEL = "label"

        fun start(context: Context, fileName: String, label: String) {
            val intent =
                Intent(context, LocalModelDownloadService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_FILE_NAME, fileName)
                    putExtra(EXTRA_LABEL, label)
                }
            context.startForegroundService(intent)
        }

        fun update(
            context: Context,
            fileName: String,
            label: String,
            bytesDownloaded: Long = 0L,
            totalBytes: Long? = null,
            paused: Boolean = false,
        ) {
            NotificationHelper.ensureChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.notify(
                NOTIFICATION_ID,
                NotificationHelper.build(context, fileName, label, bytesDownloaded, totalBytes, paused),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocalModelDownloadService::class.java))
        }
    }
}

private object NotificationHelper {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                "local_model_download",
                context.getString(R.string.chat_model_download_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.chat_model_download_channel_desc)
            }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        fileName: String,
        label: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
        paused: Boolean,
    ): Notification {
        val openIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val toggleIntent =
            pendingServiceAction(
                context,
                if (paused) LocalModelDownloadService.ACTION_RESUME else LocalModelDownloadService.ACTION_PAUSE,
                fileName,
                1,
            )
        val cancelIntent =
            pendingServiceAction(context, LocalModelDownloadService.ACTION_CANCEL, fileName, 2)

        val progressText =
            if (totalBytes != null && totalBytes > 0L) {
                "${LocalModelCatalog.formatDownloadSize(bytesDownloaded)} / ${LocalModelCatalog.formatDownloadSize(totalBytes)}"
            } else {
                LocalModelCatalog.formatDownloadSize(bytesDownloaded)
            }
        val title =
            if (paused) {
                context.getString(R.string.chat_model_download_paused_notification, label.ifBlank { fileName })
            } else {
                context.getString(R.string.chat_model_download_notification, label.ifBlank { fileName })
            }

        val builder =
            NotificationCompat.Builder(context, "local_model_download")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(progressText)
                .setContentIntent(openIntent)
                .setOngoing(!paused)
                .setOnlyAlertOnce(true)
                .addAction(
                    if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    context.getString(
                        if (paused) {
                            R.string.chat_model_download_resume
                        } else {
                            R.string.chat_model_download_pause
                        },
                    ),
                    toggleIntent,
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.chat_model_download_cancel),
                    cancelIntent,
                )

        if (totalBytes != null && totalBytes > 0L) {
            builder.setProgress(
                100,
                ((bytesDownloaded * 100L) / totalBytes).toInt().coerceIn(0, 100),
                false,
            )
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun pendingServiceAction(
        context: Context,
        action: String,
        fileName: String,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(context, LocalModelDownloadService::class.java).apply {
                this.action = action
                putExtra(LocalModelDownloadService.EXTRA_FILE_NAME, fileName)
            }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}


