package com.example.speech.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.speech.SpeechApp

/** Foreground-сервис с типом microphone: без него Android 14+ остановит захват в фоне. */
class CaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as SpeechApp).repository.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Запись речи", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setContentTitle("Идёт запись и распознавание")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        Thread { (application as SpeechApp).repository.start() }.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        (application as SpeechApp).repository.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.speech.STOP"
        private const val CH = "capture"
    }
}
