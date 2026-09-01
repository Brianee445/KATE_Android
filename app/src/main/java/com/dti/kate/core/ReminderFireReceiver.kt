package com.dti.kate.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dti.kate.R
import com.dti.kate.data.db.KateDatabase
import com.dti.kate.ui.KateActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when AlarmManager's alarm for a reminder goes off - see
 * ReminderScheduler for how it's scheduled. Posts a heads-up notification
 * (not a TTS announcement - by the time this fires the overlay/foreground
 * service may not even be running, e.g. if the user force-stopped or
 * rebooted without reopening the app, so a notification is the one
 * channel guaranteed to reach them) and marks the reminder as fired so
 * BootReceiver doesn't try to re-schedule an already-delivered one.
 */
class ReminderFireReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TEXT = "reminder_text"
        private const val CHANNEL_ID = "kate_reminders_channel_v2"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val text = intent.getStringExtra(EXTRA_REMINDER_TEXT).orEmpty().ifBlank { "Reminder" }

        createChannelIfNeeded(context)
        postNotification(context, reminderId, text)

        if (reminderId != -1L) {
            // BroadcastReceiver.onReceive must return quickly and the
            // process can be killed the moment it does - goAsync() extends
            // the receiver's lifetime just long enough for this one Room
            // write to land.
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    KateDatabase.getInstance(context).reminderDao().markFired(reminderId)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminders you've asked Kate to set"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
            // Explicit rather than relying on the channel's implicit
            // default sound - that was silent in testing on this device.
            // TYPE_ALARM (not TYPE_NOTIFICATION) + USAGE_ALARM plays
            // louder and is more attention-grabbing, appropriate for
            // something the user specifically asked to be reminded of at
            // an exact time, closer to how a real alarm behaves.
            val alarmSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            setSound(
                alarmSound,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun postNotification(context: Context, reminderId: Long, text: String) {
        val openAppIntent = Intent(context, KateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, reminderId.toInt(), openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_kate_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(reminderId.toInt(), notification)
    }
}
