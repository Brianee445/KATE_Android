package com.kate.assistant.data.db

import androidx.room.*

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal ORDER BY timestamp DESC")
    suspend fun getAll(): List<JournalEntry>

    @Insert
    suspend fun insert(entry: JournalEntry)
}
