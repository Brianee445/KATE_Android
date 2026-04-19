package com.kate.assistant.data.db

import android.content.Context
import androidx.room.*

@Database(entities = [HabitEntity::class, JournalEntry::class, TaskEntity::class], version = 1, exportSchema = false)
abstract class KateDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun journalDao(): JournalDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: KateDatabase? = null
        fun getDatabase(context: Context): KateDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, KateDatabase::class.java, "kate_db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
