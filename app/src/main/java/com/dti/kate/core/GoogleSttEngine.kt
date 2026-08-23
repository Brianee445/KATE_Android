package com.dti.kate.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Kate Smart" mode - Google's speech recognition, via the class-based
 * SpeechRecognizer + RecognitionListener API. Deliberately NOT the
 * intent-based RecognizerIntent.ACTION_RECOGNIZE_SPEECH approach - that
 * launches Google's own full-screen "Listening..." UI over the app, which
 * is wrong for an always-available background assistant. This approach
 * runs silently; Kate's own overlay ring is the only UI the user sees.
 *
 * Caveat inherent to the platform, not this code: still requires Google's
 * speech recognition service to actually be present/enabled on the device
 * (most phones have it, not guaranteed on every OEM skin), and on-device
 * recognition support varies by device - on hardware without it, this
 * silently falls through to needing network. No API key either way - this
 * is free, unlike "Kate Pro" (Deepgram).
 *
 * Must be constructed and used from the main thread - SpeechRecognizer is
 * not thread-safe and its callbacks land on whatever thread it was
 * created on (a Looper thread), which callers here bounce back to
 * Dispatchers.Main via createSpeechRecognizer's own threading + this
 * class's use of the main dispatcher for the listener registration.
 */
class GoogleSttEngine(private val context: Context) {

    /** True if Google's recognizer is present at all on this device - check before use, since "Kate Smart" should silently be unavailable rather than error-loop on devices without it. */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null

    /**
     * Listens once and returns the recognized text, or null on timeout/
     * error/no speech. Times out via RecognizerIntent's own EXTRA_SPEECH_*
     * silence-detection extras rather than an external timer, since the
     * platform recognizer already does endpoint detection internally.
     */
    suspend fun listenOnce(): String? = withContext(Dispatchers.Main) {
        if (!isAvailable()) return@withContext null

        val result = CompletableDeferred<String?>()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!result.isCompleted) result.complete(matches?.firstOrNull())
            }
            override fun onError(error: Int) {
                DebugLog.log(context, "GoogleSttEngine", "onError: ${errorName(error)} (code $error)")
                if (!result.isCompleted) result.complete(null)
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Prefer on-device recognition where the device supports it -
            // faster and doesn't need network. Silently ignored on devices
            // that don't support it; falls through to network-based
            // recognition instead, still without any UI popup.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        sr.startListening(intent)

        val text = result.await()
        sr.destroy()
        recognizer = null
        text
    }

    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    /** Human-readable name for SpeechRecognizer's ERROR_* int constants, for logging only. */
    private fun errorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        else -> "UNKNOWN_ERROR"
    }
}
