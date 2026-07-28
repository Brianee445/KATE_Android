package com.dti.kate.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioCapture {

    companion object {
        const val SAMPLE_RATE = 16000 // matches VoskManager's Recognizer sample rate
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

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

        audioRecord = record
        record.startRecording()

        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(minBufferSize)
            var chunkIndex = 0
            while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    chunkIndex++
                    if (chunkIndex == 1 || chunkIndex % 25 == 0) {
                        val (rms, peak) = amplitudeOf(chunk)
                        DebugLog.log(
                            context, "AudioCapture",
                            "chunk #$chunkIndex: rms=${"%.1f".format(rms)} peak=$peak (of 32767)"
                        )
                    }
                    onAudioChunk(chunk)
                }
            }
        }
        return true
    }

    /** Returns (RMS, peak absolute value) of a PCM16LE byte buffer, for gauging real signal level. */
    private fun amplitudeOf(pcm16: ByteArray): Pair<Double, Int> {
        var sumSquares = 0.0
        var peak = 0
        var i = 0
        while (i + 1 < pcm16.size) {
            val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort().toInt()
            sumSquares += (sample * sample).toDouble()
            if (kotlin.math.abs(sample) > peak) peak = kotlin.math.abs(sample)
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
    }
}
