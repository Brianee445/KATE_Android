package com.dti.kate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.dti.kate.R
import com.dti.kate.core.KateResponseGenerator
import com.dti.kate.core.LocalSettingsStore
import com.dti.kate.core.toneFromSlider
import java.util.Locale

class KateForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "kate_foreground_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private var tts: TextToSpeech? = null
    private lateinit var localSettings: LocalSettingsStore
    private val responseGenerator = KateResponseGenerator()

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val tone = toneFromSlider(localSettings.getToneLevel())
            val message = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> responseGenerator.speechForChargerConnected(tone)
                Intent.ACTION_POWER_DISCONNECTED -> responseGenerator.speechForChargerDisconnected(tone)
                else -> null
            }
            message?.let { speak(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        localSettings = LocalSettingsStore(this)
        createNotificationChannel()

        tts = TextToSpeech(this) { }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(powerReceiver)
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun speak(text: String) {
        val tone = toneFromSlider(localSettings.getToneLevel())
        val rate = when (tone) {
            com.dti.kate.core.KateTone.PROFESSIONAL -> 0.95f
            com.dti.kate.core.KateTone.BALANCED -> 1.0f
            com.dti.kate.core.KateTone.SASSY -> 1.05f
        }
        tts?.setSpeechRate(rate)
        tts?.language = Locale.US
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kate_charging_event")
    }

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
