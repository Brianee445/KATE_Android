package com.kate.assistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface JournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(entry: JournalEntity)

    @Query("SELECT * FROM journal")
    suspend fun getAll(): List<JournalEntity>
}
