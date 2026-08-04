package com.dti.kate.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AppLauncher(private val context: Context) {

    /** Returns the display names of all launchable apps on the device, for use as grammar vocabulary. */
    fun getInstalledAppNames(): List<String> {
        val pm = context.packageManager
        val launchableApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        )
        return launchableApps.map { it.loadLabel(pm).toString() }.distinct()
    }

    /** Returns true if a matching installed app was found and launched. */
    fun openAppByName(spokenName: String): Boolean {
        val pm = context.packageManager
        val launchableApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        )

        val query = spokenName.trim().lowercase()

        val match = launchableApps.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString().lowercase()
            label == query || label.contains(query) || query.contains(label)
        } ?: return false

        val packageName = match.activityInfo.packageName
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
