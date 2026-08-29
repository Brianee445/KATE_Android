package com.dti.kate.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dti.kate.data.db.KateDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = KateDatabase.getInstance(context).reminderDao()
                val pending = dao.getPendingAfter(System.currentTimeMillis())
                for (reminder in pending) {
                    ReminderScheduler.schedule(context, reminder.id, reminder.text, reminder.triggerAtMillis)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
