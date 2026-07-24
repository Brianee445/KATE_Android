package com.dti.kate.core

import android.content.Context

class LocalSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kate_local_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TONE = "tone_level"
        private const val KEY_TIMEOUT = "timeout_seconds"
        private const val KEY_OFFLINE_MODE = "offline_mode"
    }

    fun getToneLevel(): Float = prefs.getFloat(KEY_TONE, 0.5f)
    fun setToneLevel(value: Float) = prefs.edit().putFloat(KEY_TONE, value).apply()

    fun getTimeoutSeconds(): Int = prefs.getInt(KEY_TIMEOUT, 10)
    fun setTimeoutSeconds(value: Int) = prefs.edit().putInt(KEY_TIMEOUT, value).apply()

    fun getOfflineMode(): Boolean = prefs.getBoolean(KEY_OFFLINE_MODE, false)
    fun setOfflineMode(value: Boolean) = prefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()
}
