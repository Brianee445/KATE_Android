package com.dti.kate.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

/**
 * speakAndAwait(text) wrapping Android's platform TextToSpeech.
 *
 * This was previously a Piper-vs-platform-TTS chooser (see git history /
 * PiperTtsEngine.kt if that comes back later) - Piper is on hold
 * (piper-plus-g2p-android requires Kotlin 2.1.0+, not worth carrying that
 * version bump for an unused dependency - see app/build.gradle.kts's
 * comment where it was removed), so this is plain platform TTS for now.
 * Kept as its own class rather than inlining TextToSpeech directly into
 * every caller, so re-adding a Piper (or any other) engine later is a
 * change in one place - KateOverlayService and KateForegroundService both
 * already call this rather than TextToSpeech directly.
 */
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
