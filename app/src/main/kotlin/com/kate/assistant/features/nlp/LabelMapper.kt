package com.kate.assistant.features.nlp

import android.content.Context

class LabelMapper(private val context: Context) {

    // Labels now matched in C ml_inference.c
    fun getLabel(index: Int): String {
        return when (index) {
            0 -> "COMMUNICATION"
            1 -> "MEDIA_CONTROL"
            2 -> "OPEN_APP"
            3 -> "REMINDER"
            4 -> "SYSTEM_CONTROL"
            else -> "UNKNOWN"
        }
    }
}
