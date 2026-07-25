package com.dti.kate.core

import android.content.Context

class LocalSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("kate_local_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TONE = "tone_level"
        private const val KEY_TIMEOUT = "timeout_seconds"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        private const val KEY_RAISE_ENABLED = "wake_raise_enabled"
        private const val KEY_SHAKE_ENABLED = "wake_shake_enabled"
    }

    fun getToneLevel(): Float = prefs.getFloat(KEY_TONE, 0.5f)
    fun setToneLevel(value: Float) = prefs.edit().putFloat(KEY_TONE, value).apply()

    fun getTimeoutSeconds(): Int = prefs.getInt(KEY_TIMEOUT, 10)
    fun setTimeoutSeconds(value: Int) = prefs.edit().putInt(KEY_TIMEOUT, value).apply()

    fun getOfflineMode(): Boolean = prefs.getBoolean(KEY_OFFLINE_MODE, false)
    fun setOfflineMode(value: Boolean) = prefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply()

    fun getRaiseToWakeEnabled(): Boolean = prefs.getBoolean(KEY_RAISE_ENABLED, true)
    fun setRaiseToWakeEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_RAISE_ENABLED, value).apply()

    fun getShakeEnabled(): Boolean = prefs.getBoolean(KEY_SHAKE_ENABLED, false)
    fun setShakeEnabled(value: Boolean) = prefs.edit().putBoolean(KEY_SHAKE_ENABLED, value).apply()

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
