package com.kate.assistant.core.permissions

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionManager {
    val REQUIRED = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.CAMERA
    )

    fun missingPermissions(activity: Activity) = REQUIRED.filter {
        ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
    }

    fun requestAll(activity: Activity, requestCode: Int = 100) {
        val missing = missingPermissions(activity)
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
    }

    fun requestExactAlarm(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = activity.getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
