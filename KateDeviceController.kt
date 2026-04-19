package com.kate.assistant.features.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager

class KateDeviceController(private val context: Context) {
    private val pm = context.packageManager

    fun openApp(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun makeCall(number: String) {
        Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .let { context.startActivity(it) }
    }

    fun sendSms(number: String, message: String) {
        runCatching { SmsManager.getDefault().sendTextMessage(number, null, message, null, null) }
    }
}
