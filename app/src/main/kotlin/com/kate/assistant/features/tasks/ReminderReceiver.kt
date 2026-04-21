package com.kate.assistant.features.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val task = intent.getStringExtra("task") ?: "your task"
        Log.d("ReminderReceiver", "Reminder fired for: $task")

        // TODO: post a notification or emit a KateEvent here
        // e.g. KateEventBus.emit(KateEvent.ReminderFired(task))
    }
}
