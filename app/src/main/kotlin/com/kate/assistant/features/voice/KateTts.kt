package com.kate.assistant.features.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

class KateTts(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureVoice()
                ready = true
            }
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        if (!ready) return
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, text.hashCode().toString())
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts   = null
        ready = false
    }

    private fun configureVoice() {
        tts?.let { engine ->
            val kateVoice: Voice? = engine.voices
                ?.filter { it.locale.language == Locale.ENGLISH.language }
                ?.filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS) }
                ?.firstOrNull { it.name.contains("female", ignoreCase = true) }
                ?: engine.voices?.firstOrNull { it.locale == Locale.US }
            kateVoice?.let { engine.voice = it }
            engine.setPitch(0.95f)
            engine.setSpeechRate(0.97f)
        }
    }
