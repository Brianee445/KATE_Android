package com.dti.kate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dti.kate.R

class KateForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "kate_foreground_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        // TODO: wire NativeBridge.startListening() / VoskManager here once
        // the always-listening pipeline is ready.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kate Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kate is listening in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kate Assistant")
            .setContentText("Listening in the background")
            .setSmallIcon(R.drawable.ic_kate_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
