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
import kotlinx.coroutines.withTimeoutOrNull

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
 * (most phones have it, not guaranteed on every OEM skin). No API key
 * either way - this is free, unlike "Kate Pro" (Deepgram).
 *
 * Must be constructed and used from the main thread - SpeechRecognizer is
 * not thread-safe and its callbacks land on whatever thread it was
 * created on (a Looper thread), which callers here bounce back to
 * Dispatchers.Main via createSpeechRecognizer's own threading + this
 * class's use of the main dispatcher for the listener registration.
 */
class GoogleSttEngine(
    private val context: Context,
    private val localSettings: LocalSettingsStore = LocalSettingsStore(context),
) {

    /** True if Google's recognizer is present at all on this device - check before use, since "Kate Smart" should silently be unavailable rather than error-loop on devices without it. */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null

    /**
     * Listens once and returns the recognized text, or null on timeout/
     * error/no speech.
     *
     * Previously relied entirely on RecognizerIntent's own EXTRA_SPEECH_*
     * silence-detection extras, left unset - which meant the platform's
     * own (short, OEM-variable) default silence timeout governed cutoff,
     * completely ignoring the user's configured listen duration in
     * Settings. Now explicitly sets those extras from
     * localSettings.getTimeoutSeconds(), and additionally wraps the whole
     * listen in a matching withTimeoutOrNull as a hard ceiling, since the
     * EXTRA_SPEECH_* extras are honored inconsistently across OEM
     * recognizer implementations (Transsion in particular).
     */
    suspend fun listenOnce(): String? = withContext(Dispatchers.Main) {
        if (!isAvailable()) return@withContext null

        val timeoutMs = (localSettings.getTimeoutSeconds().coerceAtLeast(1) * 1000L)

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
            // Drive the recognizer's own silence detection off the user's
            // configured duration rather than leaving it at the platform
            // default, which is what was cutting the mic off early.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, timeoutMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, timeoutMs)
            // NOT derived from the user's setting - this is the minimum
            // length of the utterance itself before the recognizer will
            // even consider it complete, unrelated to "how long to wait
            // in silence." Kept as a small fixed floor so short utterances
            // like a bare "hi" aren't held open for the full configured
            // duration before returning.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            // EXTRA_PREFER_OFFLINE deliberately NOT set. Confirmed via
            // real-device testing (Transsion hardware) that it does not
            // gracefully fall through to network recognition when the
            // on-device language pack isn't installed, as assumed/
            // documented - it hard-fails with ERROR_LANGUAGE_UNAVAILABLE
            // (code 13) instead, breaking Kate Classic entirely on
            // exactly the low-end devices this app targets, where that
            // pack is rarely pre-downloaded. Network-based recognition
            // still shows no UI popup either way (class-based API).
        }
        sr.startListening(intent)

        // Hard ceiling on top of the extras above - some OEM recognizer
        // implementations don't honor EXTRA_SPEECH_INPUT_* reliably, so
        // this is what actually guarantees the configured duration is
        // respected rather than just requested.
        val text = withTimeoutOrNull(timeoutMs + 2000L) { result.await() }
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
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        10 -> "ERROR_TOO_MANY_REQUESTS"
        11 -> "ERROR_SERVER_DISCONNECTED"
        12 -> "ERROR_LANGUAGE_NOT_SUPPORTED"
        13 -> "ERROR_LANGUAGE_UNAVAILABLE"
        14 -> "ERROR_CANNOT_CHECK_SUPPORT_FOR_LANGUAGE"
        15 -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS"
        else -> "UNKNOWN_ERROR"
    }
}
