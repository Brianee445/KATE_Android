package com.kate.assistant.features.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class ReminderScheduler(private val context: Context) {

    fun schedule(task: String, delayMs: Long) {

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("task", task)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMs,
            pendingIntent
        )
    }
}
