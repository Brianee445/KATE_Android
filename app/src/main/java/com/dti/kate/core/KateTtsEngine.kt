package com.dti.kate.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

/**
 * speakAndAwait(text) that prefers Piper (see PiperTtsEngine) when a voice
 * is bundled, and transparently falls back to Android's TextToSpeech
 * otherwise. Callers (KateOverlayService, KateForegroundService) don't need
 * their own fallback logic - this is the one place that decision lives.
 */
class KateTtsEngine(private val context: Context) {

    private val piper = PiperTtsEngine(context)
    private var platformTts: TextToSpeech? = null
    private var isPlatformTtsReady = false

    suspend fun initialize() {
        val piperReady = piper.initialize()
        if (!piperReady) {
            val ready = CompletableDeferred<Boolean>()
            platformTts = TextToSpeech(context) { status ->
                ready.complete(status == TextToSpeech.SUCCESS)
            }
            isPlatformTtsReady = ready.await()
        }
    }

    suspend fun speakAndAwait(text: String) {
        if (piper.isReady) {
            piper.speakAndAwait(text)
            return
        }

        val engine = platformTts
        if (!isPlatformTtsReady || engine == null) return

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
        piper.stop()
        platformTts?.stop()
    }

    fun close() {
        piper.close()
        platformTts?.stop()
        platformTts?.shutdown()
        platformTts = null
    }
}
