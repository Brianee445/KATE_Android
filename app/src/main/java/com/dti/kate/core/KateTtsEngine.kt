package com.dti.kate.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
