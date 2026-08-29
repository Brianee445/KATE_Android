package com.dti.kate.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single voice-set reminder: "remind me to call mom at 5pm" -> text
 * "call mom", triggerAtMillis = today's 5pm. Persisted (not just an
 * AlarmManager PendingIntent) for two reasons: AlarmManager alarms don't
 * survive a reboot on their own, so BootReceiver needs something to
 * re-schedule from, and ReminderScreen (list of upcoming/past reminders)
 * needs a source of truth beyond "whatever's currently scheduled in the OS".
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val triggerAtMillis: Long,
    val createdAtMillis: Long,
    /** False until the notification has actually fired - lets BootReceiver skip re-scheduling ones that already went off before the reboot. */
    val fired: Boolean = false,
)
