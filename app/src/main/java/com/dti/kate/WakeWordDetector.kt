package com.dti.kate.wakeword

import android.content.Context
import android.util.Log
import com.dti.kate.core.DebugLog
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Streaming wake-word ("Hey Kate") detector.
 *
 * Runs a small TFLite classifier (trained via training/wake_word/
 * train_wake_word.py on Colab - see that script's header) over a rolling
 * 1-second window of 16kHz mono audio, fed in small chunks from
 * KateForegroundService's continuous background AudioRecord loop.
 *
 * Model contract (must match train_wake_word.py exactly):
 *   input:  [1, MelSpectrogram.NUM_FRAMES, MelSpectrogram.MEL_BINS, 1] float32
 *   output: [1, 2] float32, softmax - index 0 = not-wake-word, index 1 = wake-word
 *
 * This class is intentionally forgiving of a missing/invalid model file -
 * the wake word feature is opt-in on top of an app that already works via
 * tap/raise/shake, so a model that hasn't been trained/dropped in yet
 * should disable this feature silently, not crash the service.
 */
class WakeWordDetector(private val context: Context) {

    companion object {
        private const val TAG = "WakeWordDetector"
        private const val MODEL_ASSET = "wakeword.tflite"

        // Softmax confidence required to fire a detection.
        private const val DETECTION_THRESHOLD = 0.90f

        // Minimum gap between consecutive detections, so one utterance of
        // "Hey Kate" can't fire twice as it slides through the window.
        private const val DEBOUNCE_MS = 2500L

        // How often we run inference against the rolling buffer. Doesn't
        // need to be every chunk - the word takes several hundred ms to
        // say, running every 200ms still catches it comfortably while
        // keeping CPU/battery cost of the always-on listener low.
        private const val INFERENCE_INTERVAL_MS = 200L
    }

    private var interpreter: Interpreter? = null

    /** True once a valid model was loaded. If false, feedAudio() is always a no-op - check this after [initialize] to decide whether to bother starting the background AudioRecord loop at all. */
    var isReady: Boolean = false
        private set

    // Rolling ring buffer of the last INPUT_SAMPLES (1s) of audio.
    private val ringBuffer = ShortArray(MelSpectrogram.INPUT_SAMPLES)
    private var ringWritePos = 0
    private var samplesBuffered = 0

    private var lastInferenceAt = 0L
    private var lastDetectionAt = 0L

    private val inputBuffer = Array(1) { Array(MelSpectrogram.NUM_FRAMES) { FloatArray(MelSpectrogram.MEL_BINS) { 0f } } }
    private val outputBuffer = Array(1) { FloatArray(2) }

    fun initialize(): Boolean {
        return try {
            val modelFile = resolveModelFile() ?: run {
                Log.w(TAG, "No wake-word model bundled (assets/$MODEL_ASSET) - wake word disabled")
                DebugLog.log(context, TAG, "initialize(): no model found, feature disabled")
                isReady = false
                return false
            }
            interpreter = Interpreter(modelFile)
            isReady = true
            DebugLog.log(context, TAG, "initialize(): model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load wake-word model - disabling feature", e)
            DebugLog.log(context, TAG, "initialize() EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            isReady = false
            false
        }
    }

    /**
     * Feed a chunk of 16-bit PCM mono audio at 16kHz. Returns true exactly
     * once per detected "Hey Kate" (debounced) - caller should treat that
     * the same as the existing shake/raise gesture trigger.
     */
    fun feedAudio(pcm16: ByteArray): Boolean {
        if (!isReady) return false

        appendToRingBuffer(pcm16)
        if (samplesBuffered < MelSpectrogram.INPUT_SAMPLES) return false // still filling on first run

        val now = System.currentTimeMillis()
        if (now - lastInferenceAt < INFERENCE_INTERVAL_MS) return false
        lastInferenceAt = now

        val confidence = runInference()
        if (confidence >= DETECTION_THRESHOLD && now - lastDetectionAt >= DEBOUNCE_MS) {
            lastDetectionAt = now
            DebugLog.log(context, TAG, "wake word detected, confidence=${"%.3f".format(confidence)}")
            return true
        }
        return false
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isReady = false
    }

    private fun appendToRingBuffer(pcm16: ByteArray) {
        var i = 0
        while (i + 1 < pcm16.size) {
            val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort()
            ringBuffer[ringWritePos] = sample
            ringWritePos = (ringWritePos + 1) % ringBuffer.size
            if (samplesBuffered < ringBuffer.size) samplesBuffered++
            i += 2
        }
    }

    /** Reads the ring buffer out in chronological order (oldest first) as a contiguous array for feature extraction. */
    private fun linearizeRingBuffer(): ShortArray {
        val out = ShortArray(ringBuffer.size)
        val start = ringWritePos // oldest sample is right where we're about to overwrite next
        for (i in ringBuffer.indices) {
            out[i] = ringBuffer[(start + i) % ringBuffer.size]
        }
        return out
    }

    private fun runInference(): Float {
        val samples = linearizeRingBuffer()
        val features = MelSpectrogram.extract(samples)

        var idx = 0
        for (f in 0 until MelSpectrogram.NUM_FRAMES) {
            for (m in 0 until MelSpectrogram.MEL_BINS) {
                inputBuffer[0][f][m] = features[idx++]
            }
        }

        return try {
            interpreter?.run(inputBuffer, outputBuffer)
            outputBuffer[0][1] // wake-word class probability
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            0f
        }
    }

    /** Loads assets/wakeword.tflite as a memory-mapped buffer (standard TFLite-on-Android pattern - avoids copying the whole model into the Java heap). Returns null if the asset doesn't exist, rather than throwing, so callers can treat "not trained yet" as a normal state. */
    private fun resolveModelFile(): MappedByteBuffer? {
        return try {
            val afd = context.assets.openFd(MODEL_ASSET)
            FileInputStream(afd.fileDescriptor).use { input ->
                input.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        } catch (e: java.io.FileNotFoundException) {
            null
        }
    }
}
