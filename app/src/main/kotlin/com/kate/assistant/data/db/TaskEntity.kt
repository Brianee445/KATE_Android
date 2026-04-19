package com.kate.assistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueTime: Long? = null,
    val isCompleted: Boolean = false
)
