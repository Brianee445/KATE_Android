package com.kate.assistant.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kate.assistant.services.KateService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, KateService::class.java))
        }
    }
}
