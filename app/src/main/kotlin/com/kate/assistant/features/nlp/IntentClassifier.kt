package com.kate.assistant.features.nlp

import android.content.Context

class IntentClassifier(private val context: Context) {

    // TFLite removed — intent classification handled by C rules engine
    // This class kept as a stub for future on-device model integration

    fun classify(text: String): String {
        // All classification now done in C via bridge.processText()
        return "UNKNOWN"
    }

    fun close() {
        // Nothing to close
    }
}
