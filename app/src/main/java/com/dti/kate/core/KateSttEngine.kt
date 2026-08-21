package com.dti.kate.core

import android.content.Context
import com.dti.kate.repository.Repository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Single listen() call site for both HomeScreen and KateOverlayService,
 * branching on the user's chosen mode (Settings, stored as an internal id
 * via LocalSettingsStore.getSttMode() - "classic"/"smart"/"pro" map to the
 * UI's "Kate Classic"/"Kate Smart"/"Kate Pro" labels; nothing here or in
 * Settings shows "Vosk"/"Google"/"Deepgram" to the user).
 *
 * - Kate Classic: Vosk, fully local/offline (unchanged from before this
 *   engine existed - this is the same capture loop HomeScreen and the
 *   overlay each had their own copy of).
 * - Kate Smart: Google's on-device/cloud recognizer (GoogleSttEngine) -
 *   falls back to Classic if Google's recognizer is unavailable or errors,
 *   since that requires a fresh listen (Google owns its own mic access,
 *   there's no raw audio left over to reuse for a fallback).
 * - Kate Pro: runs the exact same Vosk capture as Classic (so there's
 *   always a local result as a baseline) while also buffering the raw
 *   audio, then sends that buffer to Deepgram via the backend and prefers
 *   its result if it succeeds. Never a wasted second listen - if Deepgram
 *   fails or times out, the Vosk result that was computed anyway is used
 *   directly, no fallback re-listen needed.
 */
class KateSttEngine(
    private val context: Context,
    private val voskManager: VoskManager,
    private val audioCapture: AudioCapture,
    private val localSettings: LocalSettingsStore,
    /** Null when the user isn't authenticated / Repository isn't available - Kate Pro degrades to Classic's result in that case rather than erroring. */
    private val repository: Repository?,
) {
    companion object {
        private const val DEFAULT_TIMEOUT_MS = 6000L
        private const val CLOUD_TIMEOUT_MS = 4000L
    }

    private val googleStt = GoogleSttEngine(context)

    suspend fun listen(scope: CoroutineScope, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        return when (localSettings.getSttMode()) {
            "smart" -> listenSmart(scope, timeoutMs)
            "pro" -> listenPro(scope, timeoutMs)
            else -> listenClassic(scope, timeoutMs).first
        }
    }

    private suspend fun listenSmart(scope: CoroutineScope, timeoutMs: Long): String? {
        if (!googleStt.isAvailable()) return listenClassic(scope, timeoutMs).first
        val result = googleStt.listenOnce()
        return result ?: listenClassic(scope, timeoutMs).first
    }

    private suspend fun listenPro(scope: CoroutineScope, timeoutMs: Long): String? {
        val (localResult, rawAudio) = listenClassic(scope, timeoutMs, captureRawBytes = true)

        if (repository == null || rawAudio == null || rawAudio.isEmpty()) return localResult

        val cloudResult = try {
            kotlinx.coroutines.withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
                repository.transcribeCloud(rawAudio).getOrNull()
            }
        } catch (e: Exception) {
            null
        }

        return if (!cloudResult?.text.isNullOrBlank()) cloudResult!!.text else localResult
    }

    /**
     * The one canonical Vosk capture loop - previously duplicated (with
     * minor drift) between HomeScreen.startListening/stopListeningAndProcess
     * and KateOverlayService.listenForTranscript. Both now call this.
     */
    private suspend fun listenClassic(
        scope: CoroutineScope,
        timeoutMs: Long,
        captureRawBytes: Boolean = false,
    ): Pair<String?, ByteArray?> {
        if (!voskManager.startListening()) return null to null

        val rawBuffer = if (captureRawBytes) ByteArrayOutputStream() else null
        val reheard = CompletableDeferred<String?>()
        val timeoutJob = scope.launch {
            delay(timeoutMs)
            if (!reheard.isCompleted) reheard.complete(null)
        }

        audioCapture.start(context, scope) { chunk ->
            rawBuffer?.write(chunk)
            val finalResult = voskManager.feedAudio(chunk)
            if (finalResult != null && !reheard.isCompleted) {
                reheard.complete(finalResult)
            }
        }

        val heard = reheard.await()
        timeoutJob.cancel()
        audioCapture.stop()
        val finalText = heard ?: voskManager.stopListening()
        return finalText to rawBuffer?.toByteArray()
    }
}
