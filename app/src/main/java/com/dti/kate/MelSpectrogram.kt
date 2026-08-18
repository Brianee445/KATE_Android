package com.dti.kate.wakeword

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * Log-mel spectrogram feature extractor for the wake-word model.
 *
 * These parameters are the *contract* with the Colab training script
 * (training/wake_word/train_wake_word.py) - both sides must compute
 * identical features from identical audio, or the model that trains
 * cleanly in Python will perform nothing like that on-device. If you
 * change any constant here, change it in train_wake_word.py's
 * `extract_features()` too, and retrain.
 *
 *   Sample rate:    16000 Hz (matches AudioCapture / VoskManager)
 *   Window:         480 samples (30ms)
 *   Hop:            320 samples (20ms)
 *   FFT size:       512 (next pow2 >= window)
 *   Mel bins:       40
 *   Frequency range: 20 Hz - 8000 Hz (Nyquist)
 *   Input duration: 1.0s (16000 samples) -> 49 frames x 40 mel bins
 */
object MelSpectrogram {
    const val SAMPLE_RATE = 16000
    const val WINDOW_SIZE = 480
    const val HOP_SIZE = 320
    const val FFT_SIZE = 512
    const val MEL_BINS = 40
    const val INPUT_SAMPLES = 16000
    const val NUM_FRAMES = (INPUT_SAMPLES - WINDOW_SIZE) / HOP_SIZE + 1 // 49

    private val hannWindow = DoubleArray(WINDOW_SIZE) { i ->
        0.5 - 0.5 * cos(2.0 * PI * i / (WINDOW_SIZE - 1))
    }

    private val melFilterbank: Array<DoubleArray> by lazy { buildMelFilterbank() }

    /**
     * Converts [INPUT_SAMPLES] PCM16 samples (mono, 16kHz) into a
     * [NUM_FRAMES] x [MEL_BINS] log-mel spectrogram, flattened row-major
     * (frame-major) for direct feed into the TFLite interpreter.
     */
    fun extract(samples: ShortArray): FloatArray {
        require(samples.size == INPUT_SAMPLES) { "expected $INPUT_SAMPLES samples, got ${samples.size}" }

        val out = FloatArray(NUM_FRAMES * MEL_BINS)
        val frame = DoubleArray(FFT_SIZE)

        for (f in 0 until NUM_FRAMES) {
            val start = f * HOP_SIZE
            java.util.Arrays.fill(frame, 0.0)
            for (i in 0 until WINDOW_SIZE) {
                frame[i] = samples[start + i] / 32768.0 * hannWindow[i]
            }

            val (re, im) = fft(frame)

            // Power spectrum, positive frequencies only (0..FFT_SIZE/2).
            val power = DoubleArray(FFT_SIZE / 2 + 1)
            for (k in power.indices) {
                power[k] = re[k] * re[k] + im[k] * im[k]
            }

            for (m in 0 until MEL_BINS) {
                var energy = 0.0
                val filter = melFilterbank[m]
                for (k in filter.indices) {
                    energy += filter[k] * power[k]
                }
                // log(energy + eps) - standard floor to avoid log(0).
                out[f * MEL_BINS + m] = ln(max(energy, 1e-10)).toFloat()
            }
        }
        return out
    }

    /** In-place iterative radix-2 Cooley-Tukey FFT. Returns (real, imag) of length FFT_SIZE. Input is real-valued (imag=0), zero-padded to FFT_SIZE by caller. */
    private fun fft(input: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val n = FFT_SIZE
        val re = input.copyOf(n)
        val im = DoubleArray(n)

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        // Iterative Cooley-Tukey.
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe

                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm

                    val nextRe = curRe * wRe - curIm * wIm
                    val nextIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                    curIm = nextIm
                }
                i += len
            }
            len = len shl 1
        }
        return re to im
    }

    /** Standard triangular mel filterbank, HTK-style mel scale: mel = 2595 * log10(1 + f/700). */
    private fun buildMelFilterbank(): Array<DoubleArray> {
        val fMin = 20.0
        val fMax = SAMPLE_RATE / 2.0
        val numBins = FFT_SIZE / 2 + 1

        fun hzToMel(hz: Double) = 2595.0 * kotlin.math.log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val melPoints = DoubleArray(MEL_BINS + 2) { i -> melMin + i * (melMax - melMin) / (MEL_BINS + 1) }
        val hzPoints = melPoints.map { melToHz(it) }
        val binPoints = hzPoints.map { (it * FFT_SIZE / SAMPLE_RATE).toInt().coerceIn(0, numBins - 1) }

        return Array(MEL_BINS) { m ->
            val filter = DoubleArray(numBins)
            val left = binPoints[m]
            val center = binPoints[m + 1]
            val right = binPoints[m + 2]

            if (center > left) {
                for (k in left until center) {
                    filter[k] = (k - left).toDouble() / (center - left)
                }
            }
            if (right > center) {
                for (k in center until right) {
                    filter[k] = (right - k).toDouble() / (right - center)
                }
            }
            filter
        }
    }
}
