package com.kate.assistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val key: String,
    val intent: String,
    val entity: String,
    val count:  Int = 1
)
