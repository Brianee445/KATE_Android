package com.dti.kate.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
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

    suspend fun speakAndAwait(text: String) {
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
        platformTts?.stop()
    }

    fun close() {
        platformTts?.stop()
        platformTts?.shutdown()
        platformTts = null
    }
}
