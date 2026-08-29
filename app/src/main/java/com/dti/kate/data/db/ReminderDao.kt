package com.dti.kate.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReminderDao {

    /** @return the new row's id - needed immediately to key the AlarmManager PendingIntent/notification to this specific reminder. */
    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    /** Soonest-first, only ones not yet fired - what ReminderScreen shows as "upcoming". */
    @Query("SELECT * FROM reminders WHERE fired = 0 ORDER BY triggerAtMillis ASC")
    suspend fun getUpcoming(): List<Reminder>

    /** Used by BootReceiver: everything still pending needs its AlarmManager alarm re-armed, since those don't survive a reboot. */
    @Query("SELECT * FROM reminders WHERE fired = 0 AND triggerAtMillis > :nowMillis")
    suspend fun getPendingAfter(nowMillis: Long): List<Reminder>

    @Query("UPDATE reminders SET fired = 1 WHERE id = :id")
    suspend fun markFired(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}
