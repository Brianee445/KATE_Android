package com.kate.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kate_prefs")

class KatePreferences(private val context: Context) {
    companion object {
        val WAKE_WORD_ENROLLED = booleanPreferencesKey("wake_word_enrolled")
        val PREFERRED_VOICE    = stringPreferencesKey("preferred_voice")
    }

    val wakeWordEnrolled: Flow<Boolean> = context.dataStore.data.map { it[WAKE_WORD_ENROLLED] ?: false }

    suspend fun setWakeWordEnrolled(value: Boolean) {
        context.dataStore.edit { it[WAKE_WORD_ENROLLED] = value }
    }
}
