package com.dti.kate.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale
import java.util.UUID

class KateTtsEngine(private val context: Context) {

    private var platformTts: TextToSpeech? = null
    private var isPlatformTtsReady = false

    suspend fun initialize() {
        val ready = CompletableDeferred<Boolean>()
        platformTts = TextToSpeech(context) { status ->
            ready.complete(status == TextToSpeech.SUCCESS)
        }
        isPlatformTtsReady = ready.await()
    }

    /** English-locale voices available from whatever TTS engine is
     * installed on this device - for SettingsScreen's voice picker.
     * Previously nothing let the user choose a specific voice at all -
     * speakAndAwait only ever set a *language* (Locale.US), and which
     * actual Voice the system picks for that language is entirely up to
     * the device/engine, which is why it defaulted to a male-sounding
     * voice here with no way to change it. */
    fun availableVoices(): List<Voice> {
        val engine = platformTts ?: return emptyList()
        return engine.voices
            ?.filter { it.locale.language == Locale.US.language && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            ?.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
            ?: emptyList()
    }

    /**
     * @param tone When provided, applies the same tone-based rate/pitch/
     * locale tuning HomeScreen originally applied only to its own local
     * TextToSpeech instance - slightly slower + lower pitch than platform
     * defaults, layered under the tone slider, for a calmer delivery.
     * Kept optional so callers with no tone context (e.g. a raw prompt)
     * still work, just without the tuning.
     */
    suspend fun speakAndAwait(text: String, tone: KateTone? = null) {
        val engine = platformTts
        if (!isPlatformTtsReady || engine == null) return

        if (tone != null) {
            val rate = when (tone) {
                KateTone.PROFESSIONAL -> 0.88f
                KateTone.BALANCED -> 0.92f
                KateTone.SASSY -> 0.97f
            }
            engine.setSpeechRate(rate)
            engine.setPitch(0.95f)
        }

        val preferredVoiceName = LocalSettingsStore(context).getPreferredVoiceName()
        val selectedVoice = preferredVoiceName?.let { name -> engine.voices?.firstOrNull { it.name == name } }
        if (selectedVoice != null) {
            // Setting a specific Voice already carries its own locale -
            // no need to also set engine.language here.
            engine.voice = selectedVoice
        } else {
            engine.language = Locale.US
        }

        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (!done.isCompleted) done.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (!done.isCompleted) done.complete(Unit) }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        done.await()
    }

    fun stop() {
        platformTts?.stop()
    }

    fun close() {
        platformTts?.stop()
        platformTts?.shutdown()
        platformTts = null
    }
}
