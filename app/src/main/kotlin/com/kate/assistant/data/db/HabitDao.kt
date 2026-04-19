package com.kate.assistant.data.db

import androidx.room.*

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity)
}
