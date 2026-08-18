package com.dti.kate.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AudioCapture {

    companion object {
        const val SAMPLE_RATE = 16000 // matches VoskManager's Recognizer sample rate

        // Target RMS for the software AGC fallback. Vosk's acoustic model was
        // trained on speech around this level; too quiet starves the model of
        // signal, too loud clips it. ~28% of full scale (32767) is a safe,
        // non-clipping target for normal speaking voice.
        private const val TARGET_RMS = 3500.0

        // Only apply gain when there's plausibly real speech, not silence/
        // room noise floor - otherwise silence gets amplified into a wall of
        // hiss that Vosk can mis-transcribe as speech.
        private const val NOISE_FLOOR_RMS = 40.0

        // Cap how hard we'll boost a single chunk, so a very quiet pop/click
        // doesn't get amplified into a clipped spike.
        private const val MAX_GAIN = 12.0
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var hardwareAgc: AutomaticGainControl? = null

    // Smoothed gain across chunks so consecutive chunks don't jump loudness
    // abruptly (which itself hurts recognition of run-on words).
    private var smoothedGain = 1.0

    @SuppressLint("MissingPermission") // caller must have already checked RECORD_AUDIO
    fun start(context: Context, scope: CoroutineScope, onAudioChunk: (ByteArray) -> Unit): Boolean {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) return false

        val record = AudioRecord(
            // VOICE_RECOGNITION is OEM-tuned - Android makes no guarantee
            // about its gain staging, and on this device (TECNO/Transsion)
            // it was measured producing audio at ~1-6% of full scale
            // (RMS ~65, peak ~130-500 out of 32767), i.e. noise-floor level,
            // even during active speech. MIC is the raw/unprocessed source
            // and isn't subject to that vendor-specific DSP tuning.
            //
            // Trade-off: MIC also skips whatever noise suppression/AGC the
            // OEM would have chained onto VOICE_RECOGNITION. We claw that
            // back explicitly below - hardware effects where the device
            // actually implements them, software AGC as a fallback since
            // many budget OEM chips (this one included) report the effect
            // classes as "available" but don't meaningfully process audio.
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        attachHardwareEffects(record.audioSessionId)

        audioRecord = record
        smoothedGain = 1.0
        record.startRecording()

        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(minBufferSize)
            var chunkIndex = 0
            while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var chunk = buffer.copyOf(read)
                    val (rmsBefore, _) = amplitudeOf(chunk)
                    chunk = applySoftwareAgc(chunk, rmsBefore)

                    chunkIndex++
                    if (chunkIndex == 1 || chunkIndex % 25 == 0) {
                        val (rmsAfter, peakAfter) = amplitudeOf(chunk)
                        DebugLog.log(
                            context, "AudioCapture",
                            "chunk #$chunkIndex: rms=${"%.1f".format(rmsBefore)}->${"%.1f".format(rmsAfter)} " +
                                "peak=$peakAfter gain=${"%.2f".format(smoothedGain)} (of 32767)"
                        )
                    }
                    onAudioChunk(chunk)
                }
            }
        }
        return true
    }

    /** Attaches OEM/platform NS, AEC and AGC audio effects where the device actually supports them. Best-effort - absence isn't fatal, the software AGC below covers the gap. */
    private fun attachHardwareEffects(audioSessionId: Int) {
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) { /* best-effort */ }

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) { /* best-effort */ }

        try {
            if (AutomaticGainControl.isAvailable()) {
                hardwareAgc = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) { /* best-effort */ }
    }

    /**
     * Software gain normalization toward TARGET_RMS. Runs regardless of
     * whether a hardware AGC claimed to attach above, since on this class of
     * device (see attachHardwareEffects doc) that claim has proven unreliable
     * - cheap insurance either way, as a no-op multiply-by-~1 when the
     * hardware path is actually doing its job.
     */
    private fun applySoftwareAgc(pcm16: ByteArray, rms: Double): ByteArray {
        if (rms < NOISE_FLOOR_RMS) {
            // Likely silence/room tone - don't amplify noise into false speech.
            smoothedGain = smoothedGain * 0.9 + 1.0 * 0.1
            return pcm16
        }

        val targetGain = (TARGET_RMS / rms).coerceIn(1.0 / MAX_GAIN, MAX_GAIN)
        // Exponential smoothing (attack/release) so gain doesn't jump between
        // consecutive ~20-30ms chunks and introduce audible/spectral artifacts.
        smoothedGain = smoothedGain * 0.8 + targetGain * 0.2

        if (abs(smoothedGain - 1.0) < 0.05) return pcm16 // not worth the copy

        val out = ByteArray(pcm16.size)
        var i = 0
        while (i + 1 < pcm16.size) {
            val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort().toInt()
            val boosted = (sample * smoothedGain).toInt().coerceIn(-32768, 32767)
            out[i] = (boosted and 0xFF).toByte()
            out[i + 1] = ((boosted shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    /** Returns (RMS, peak absolute value) of a PCM16LE byte buffer, for gauging real signal level. */
    private fun amplitudeOf(pcm16: ByteArray): Pair<Double, Int> {
        var sumSquares = 0.0
        var peak = 0
        var i = 0
        while (i + 1 < pcm16.size) {
            val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort().toInt()
            sumSquares += (sample * sample).toDouble()
            if (abs(sample) > peak) peak = abs(sample)
            i += 2
        }
        val sampleCount = pcm16.size / 2
        val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount) else 0.0
        return rms to peak
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.let {
            if (it.state == AudioRecord.STATE_INITIALIZED) {
                it.stop()
            }
            it.release()
        }
        audioRecord = null

        try { noiseSuppressor?.release() } catch (e: Exception) { }
        try { echoCanceler?.release() } catch (e: Exception) { }
        try { hardwareAgc?.release() } catch (e: Exception) { }
        noiseSuppressor = null
        echoCanceler = null
        hardwareAgc = null
    }
}
