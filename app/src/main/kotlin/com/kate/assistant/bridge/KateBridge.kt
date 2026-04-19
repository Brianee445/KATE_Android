package com.kate.assistant.bridge

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class KateBridge(private val context: Context) {

    init {
        val modelPath = copyModelToInternal()
        nativeInit(modelPath)
    }

    private fun copyModelToInternal(): String {
        val file = File(context.filesDir, "model_intent.tflite")
        if (!file.exists()) {
            context.assets.open("model_intent.tflite").use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
        return file.absolutePath
    }

    // Called from C via JNI — do NOT rename
    @Suppress("unused")
    fun onNativeEvent(type: String, payload: String) {
        when (type) {
            "WAKE_WORD" -> KateEventBus.emit(KateEvent.WakeWordDetected)
            "INTENT" -> {
                val parts   = payload.split("|")
                val intent  = parts.getOrNull(0)?.let { runCatching { IntentType.valueOf(it) }.getOrNull() } ?: IntentType.UNKNOWN
                val entity  = parts.getOrNull(1) ?: ""
                val emotion = parts.getOrNull(2)?.let { runCatching { EmotionType.valueOf(it) }.getOrNull() } ?: EmotionType.NEUTRAL
                KateEventBus.emit(KateEvent.IntentEvent(intent, entity, emotion))
            }
            "HABIT_UPDATE" -> {
                val parts = payload.split("|")
                val intent = parts.getOrNull(0) ?: return
                val entity = parts.getOrNull(1) ?: return
                KateEventBus.emit(KateEvent.HabitUpdate(intent, entity))
            }
            "SUGGESTION" -> KateEventBus.emit(KateEvent.Suggestion(payload))
            "ERROR"      -> KateEventBus.emit(KateEvent.Error(payload))
        }
    }

    external fun nativeInit(modelPath: String)
    external fun processText(text: String)
    external fun startAudio()
    external fun stopAudio()
    external fun updateAppList(apps: Array<String>)
    external fun loadHabits(habits: Array<String>)
    external fun requestSuggestion()

    companion object {
        init { System.loadLibrary("kate_core") }
    }
}
