package com.dti.kate.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Thin wrapper around AlarmManager for reminders. Kept separate from
 * ReminderFireReceiver (which handles the alarm actually going off) so
 * KateCommandProcessor and BootReceiver share one place that knows how to
 * build the PendingIntent for a given reminder id - getting that wrong
 * (e.g. mismatched request codes) is the classic way scheduled-then-
 * canceled alarms silently keep firing anyway.
 */
object ReminderScheduler {

    fun schedule(context: Context, reminderId: Long, text: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, reminderId, text)

        // Exact-alarm permission (SCHEDULE_EXACT_ALARM) can be revoked by
        // the user at any time on API 31+, independent of it being granted
        // at install. Rather than build a whole permission-request flow for
        // this pass, fall back to an inexact alarm - AlarmManager still
        // fires it close to on-time (typically within a few minutes), it
        // just isn't guaranteed to the second. A reminder a bit late beats
        // a crash or a silently-never-scheduled one.
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canBeExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, reminderId, text = ""))
    }

    private fun buildPendingIntent(context: Context, reminderId: Long, text: String): PendingIntent {
        val intent = Intent(context, ReminderFireReceiver::class.java).apply {
            putExtra(ReminderFireReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderFireReceiver.EXTRA_REMINDER_TEXT, text)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(), // unique per reminder so canceling one doesn't touch others
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
