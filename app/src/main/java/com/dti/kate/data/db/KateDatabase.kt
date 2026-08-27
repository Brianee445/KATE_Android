package com.dti.kate.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * First real use of the Room dependency already in the version catalog -
 * previously declared but unused (no @Database existed anywhere in the
 * project). Singleton via getInstance so the overlay service, chat screen,
 * and voice path all share one connection instead of racing to open the
 * same file.
 */
@Database(entities = [ConversationTurn::class], version = 1, exportSchema = true)
abstract class KateDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var instance: KateDatabase? = null

        fun getInstance(context: Context): KateDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KateDatabase::class.java,
                    "kate_local.db",
                ).build().also { instance = it }
            }
    }
}
