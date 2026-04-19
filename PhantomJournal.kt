package com.kate.assistant.features.phantom

import android.content.Context
import android.util.Log
import com.kate.assistant.data.db.KateDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhantomJournal(context: Context) {

    private val db = KateDatabase.getDatabase(context)

    fun logAppOpen(pkg: String) {

        CoroutineScope(Dispatchers.IO).launch {
            Log.d("PhantomJournal", "Logging app: $pkg")

            db.journalDao().insertEvent(
                "APP_OPEN|$pkg|${System.currentTimeMillis()}"
            )
        }
    }
}