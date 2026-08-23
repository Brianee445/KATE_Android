package com.dti.kate.core

import android.content.Context
import com.dti.kate.repository.Repository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Single listen() call site for both HomeScreen and KateOverlayService,
 * branching on the user's chosen mode (Settings, stored as an internal id
 * via LocalSettingsStore.getSttMode() - "classic"/"pro" map to the UI's
 * "Kate Classic"/"Kate Pro" labels; nothing here or in Settings shows
 * "Google"/"Deepgram" to the user).
 *
 * Vosk was removed entirely (offline recognition consistently froze/hung
 * on low-RAM devices, especially Transsion - the exact hardware Kate
 * targets - and its ~130MB bundled model was most of the APK's 175MB).
 * There is now no offline fallback; both modes require connectivity.
 *
 * - Kate Classic: Google's on-device/cloud recognizer (GoogleSttEngine).
 *   Free, no backend round-trip, does its own endpoint detection.
 * - Kate Pro: records raw audio directly (no STT engine in the loop -
 *   Deepgram receives the buffer, not text from an intermediate engine),
 *   using a lightweight RMS-based silence detector as its own endpointer
 *   since there's no longer a Vosk feedAudio() result to signal "done
 *   speaking" for free. Sends the buffer to Deepgram via the backend; on
 *   failure, timeout, or no repository (unauthenticated), falls back to a
 *   fresh Kate Classic listen rather than failing silently.
 */
class KateSttEngine(
    private val context: Context,
    private val audioCapture: AudioCapture,
    private val localSettings: LocalSettingsStore,
    /** Null when the user isn't authenticated / Repository isn't available - Kate Pro degrades to Classic in that case rather than erroring. */
    private val repository: Repository?,
) {
    companion object {
        private const val CLOUD_TIMEOUT_MS = 4000L

        // How long continuous low-RMS audio must persist, after real speech
        // was already heard, before we treat the utterance as finished.
        // Mirrors natural end-of-speech pause length.
        private const val SILENCE_END_MS = 1200L

        // Post-AGC RMS threshold for "this chunk is speech, not noise
        // floor." AudioCapture's software AGC boosts real speech toward
        // TARGET_RMS=3500 and leaves noise-floor chunks (measured ~40-65 on
        // Transsion hardware) untouched, so 300 sits safely between the two
        // regardless of which regime a given chunk is in.
        private const val SPEECH_RMS_THRESHOLD = 300.0

        // Hard ceiling regardless of silence detection, so a false "still
        // speaking" read (e.g. sustained background noise just above
        // threshold) can't hang Kate Pro's capture indefinitely.
        private const val MAX_RAW_CAPTURE_MS = 12000L
    }

    private val googleStt = GoogleSttEngine(context)

    suspend fun listen(scope: CoroutineScope): String? {
        val mode = localSettings.getSttMode()
        DebugLog.log(context, "KateSttEngine", "listen() called, mode=$mode")
        val result = when (mode) {
            "pro" -> listenPro(scope)
            else -> listenClassic()
        }
        DebugLog.log(context, "KateSttEngine", "listen() returning: ${result?.let { "\"$it\"" } ?: "null"}")
        return result
    }

    private suspend fun listenClassic(): String? {
        val available = googleStt.isAvailable()
        DebugLog.log(context, "KateSttEngine", "listenClassic: googleStt.isAvailable()=$available")
        if (!available) return null
        val text = googleStt.listenOnce()
        DebugLog.log(context, "KateSttEngine", "listenClassic: googleStt.listenOnce() -> ${text?.let { "\"$it\"" } ?: "null"}")
        return text
    }

    private suspend fun listenPro(scope: CoroutineScope): String? {
        if (repository == null) {
            DebugLog.log(context, "KateSttEngine", "listenPro: repository is null (not authenticated), falling back to Classic immediately")
            return listenClassic()
        }

        val rawAudio = captureRawAudioUntilSilence(scope)
        DebugLog.log(context, "KateSttEngine", "listenPro: captured ${rawAudio?.size ?: 0} bytes of raw audio")
        if (rawAudio == null || rawAudio.isEmpty()) return listenClassic()

        val cloudResult = try {
            withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
                repository.transcribeCloud(rawAudio).fold(
                    onSuccess = { it },
                    onFailure = { e ->
                        DebugLog.log(context, "KateSttEngine", "listenPro: transcribeCloud failed: ${e.javaClass.simpleName}: ${e.message}")
                        null
                    }
                )
            }
        } catch (e: Exception) {
            DebugLog.log(context, "KateSttEngine", "listenPro: transcribeCloud threw: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

        if (cloudResult == null) {
            DebugLog.log(context, "KateSttEngine", "listenPro: cloud call timed out (>${CLOUD_TIMEOUT_MS}ms) or returned no result")
        } else {
            DebugLog.log(context, "KateSttEngine", "listenPro: cloud returned text=\"${cloudResult.text}\"")
        }

        return if (!cloudResult?.text.isNullOrBlank()) {
            cloudResult!!.text
        } else {
            DebugLog.log(context, "KateSttEngine", "listenPro: falling back to Classic")
            listenClassic()
        }
    }

    /**
     * Records raw PCM until ~1.2s of continuous silence following detected
     * speech, or MAX_RAW_CAPTURE_MS elapses - whichever comes first. This
     * is Kate Pro's own endpointer, since (unlike the old Vosk-backed
     * version) no STT engine runs alongside this capture to provide one.
     * Returns null if AudioCapture.start() itself fails (e.g. AudioRecord
     * couldn't initialize).
     */
    private suspend fun captureRawAudioUntilSilence(scope: CoroutineScope): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val done = CompletableDeferred<Unit>()
        var hasHeardSpeech = false
        var silenceMs = 0L

        val maxJob = scope.launch {
            delay(MAX_RAW_CAPTURE_MS)
            if (!done.isCompleted) done.complete(Unit)
        }

        val started = audioCapture.start(context, scope) { chunk ->
            buffer.write(chunk)
            val rms = rmsOf(chunk)
            val chunkMs = (chunk.size / 2) * 1000L / AudioCapture.SAMPLE_RATE
            if (rms >= SPEECH_RMS_THRESHOLD) {
                hasHeardSpeech = true
                silenceMs = 0L
            } else if (hasHeardSpeech) {
                silenceMs += chunkMs
                if (silenceMs >= SILENCE_END_MS && !done.isCompleted) {
                    done.complete(Unit)
                }
            }
        }
        if (!started) {
            maxJob.cancel()
            return null
        }

        done.await()
        maxJob.cancel()
        audioCapture.stop()
        return buffer.toByteArray()
    }

    private fun rmsOf(pcm16: ByteArray): Double {
        if (pcm16.size < 2) return 0.0
        var sumSquares = 0.0
        var count = 0
        var i = 0
        while (i + 1 < pcm16.size) {
            val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort().toInt()
            sumSquares += (sample * sample).toDouble()
            i += 2
            count++
        }
        return if (count > 0) sqrt(sumSquares / count) else 0.0
    }
}
