package com.dti.kate.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.piperplus.g2p.PiperPlusG2p
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.LongBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Neural TTS via piper-plus (MIT-licensed Piper fork, own espeak-ng-free
 * G2P) + ONNX Runtime for the VITS acoustic/vocoder model. See
 * training/tts/README.md for why piper-plus over the official Piper repos,
 * and for where voice.onnx / voice.onnx.json come from.
 *
 * ONNX I/O contract (standard Piper VITS export, confirmed against Piper's
 * own docs/source - piper-plus is architecturally a Piper fork so this
 * should carry over, but VERIFY against your actual voice.onnx.json once
 * you have one, particularly phoneme_id_map's BOS/EOS/pad convention below):
 *   inputs:  "input"         int64 [1, N]   - phoneme ids
 *            "input_lengths" int64 [1]      - N
 *            "scales"        float32 [3]    - [noise_scale, length_scale, noise_w]
 *   output:  "output"        float32 [1, 1, samples] - raw PCM, [-1, 1]
 *
 * Silently unavailable (isReady == false) if assets/piper_voice.onnx or
 * assets/piper_voice.onnx.json aren't bundled - same "opt-in, no crash"
 * pattern as WakeWordDetector, since Android's TextToSpeech is a perfectly
 * functional fallback (see KateTtsEngine) until a piper-plus voice exists.
 */
class PiperTtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "PiperTtsEngine"
        private const val MODEL_ASSET = "piper_voice.onnx"
        private const val CONFIG_ASSET = "piper_voice.onnx.json"
        private const val LANGUAGE_CODE = "en" // matches PiperPlusG2p.phonemize's lang param
    }

    var isReady: Boolean = false
        private set

    private var ortSession: OrtSession? = null
    private var ortEnvironment: OrtEnvironment? = null
    private var g2p: PiperPlusG2p? = null

    private var sampleRate = 22050
    private var noiseScale = 0.667f
    private var lengthScale = 1.0f
    private var noiseW = 0.8f
    private var phonemeIdMap: Map<String, List<Long>> = emptyMap()

    private var audioTrack: AudioTrack? = null

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val configJson = readAssetTextOrNull(CONFIG_ASSET) ?: run {
                Log.w(TAG, "No piper voice bundled (assets/$MODEL_ASSET) - falling back to platform TTS")
                return@withContext false
            }
            parseConfig(configJson)

            val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            ortEnvironment = OrtEnvironment.getEnvironment()
            ortSession = ortEnvironment!!.createSession(modelBytes)

            g2p = PiperPlusG2p.create(context)

            isReady = true
            DebugLog.log(context, TAG, "initialize(): loaded, sampleRate=$sampleRate phonemes=${phonemeIdMap.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Piper - falling back to platform TTS", e)
            DebugLog.log(context, TAG, "initialize() EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            isReady = false
            false
        }
    }

    /** Synthesizes and plays [text] to completion. No-op if [isReady] is false - caller (KateTtsEngine) is responsible for falling back to platform TTS in that case. */
    suspend fun speakAndAwait(text: String) = withContext(Dispatchers.IO) {
        val session = ortSession ?: return@withContext
        val env = ortEnvironment ?: return@withContext
        val phonemizer = g2p ?: return@withContext

        val phonemes = try {
            phonemizer.phonemize(text, LANGUAGE_CODE).phonemeList
        } catch (e: Exception) {
            Log.e(TAG, "Phonemization failed", e)
            return@withContext
        }
        if (phonemes.isEmpty()) return@withContext

        val ids = phonemesToIds(phonemes)
        val audio = try {
            runInference(session, env, ids)
        } catch (e: Exception) {
            Log.e(TAG, "Piper inference failed", e)
            return@withContext
        }
        if (audio.isEmpty()) return@withContext

        playPcm(audio)
    }

    /**
     * Standard Piper phoneme->id convention: BOS ("^"), then each phoneme
     * id interleaved with the pad id ("_"), then EOS ("$"). VERIFY this
     * against your voice's actual config - if piper-plus's G2P already
     * emits BOS/EOS/pad markers as part of phonemeList, this will double
     * them up and needs simplifying to just the direct id lookup.
     */
    private fun phonemesToIds(phonemes: List<String>): LongArray {
        val pad = phonemeIdMap["_"]?.firstOrNull() ?: 0L
        val bos = phonemeIdMap["^"]?.firstOrNull() ?: 1L
        val eos = phonemeIdMap["$"]?.firstOrNull() ?: 2L

        val ids = mutableListOf<Long>()
        ids.add(bos)
        ids.add(pad)
        for (phoneme in phonemes) {
            val mapped = phonemeIdMap[phoneme] ?: continue // unknown phoneme - skip rather than crash
            ids.addAll(mapped)
            ids.add(pad)
        }
        ids.add(eos)
        return ids.toLongArray()
    }

    private fun runInference(session: OrtSession, env: OrtEnvironment, ids: LongArray): FloatArray {
        val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
        val lengthTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1))
        val scalesTensor = OnnxTensor.createTensor(
            env, java.nio.FloatBuffer.wrap(floatArrayOf(noiseScale, lengthScale, noiseW)), longArrayOf(3),
        )

        inputTensor.use { inp ->
            lengthTensor.use { len ->
                scalesTensor.use { scales ->
                    val inputs = mapOf("input" to inp, "input_lengths" to len, "scales" to scales)
                    session.run(inputs).use { result ->
                        // Using the single output by iteration rather than
                        // result.get("output") - the exact accessor name in
                        // onnxruntime-android's Result class varies by
                        // version; iteration (Result implements
                        // Iterable<Map.Entry<String, OnnxValue>>) is the
                        // stable path and this model only has one output
                        // regardless of what it's named.
                        val output = result.iterator().next().value.value
                        return flattenAudioOutput(output)
                    }
                }
            }
        }
    }

    /** ONNX output nesting depth varies by export ([1,1,N] is typical, some exports are just [N]) - flatten defensively rather than assuming a fixed rank. */
    private fun flattenAudioOutput(value: Any?): FloatArray {
        return when (value) {
            is FloatArray -> value
            is Array<*> -> value.flatMap { flattenAudioOutput(it).toList() }.toFloatArray()
            else -> FloatArray(0)
        }
    }

    private fun playPcm(samples: FloatArray) {
        val pcm16 = ShortArray(samples.size) { i ->
            (min(1f, max(-1f, samples[i])) * 32767f).toInt().toShort()
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBufferSize, pcm16.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack = track
        track.write(pcm16, 0, pcm16.size)
        track.play()

        // MODE_STATIC has no completion callback - block (we're already on
        // Dispatchers.IO here) for approximately the clip's real duration
        // so speakAndAwait's caller can rely on it returning when done,
        // matching TextToSpeech's onDone semantics.
        val durationMs = (samples.size.toDouble() / sampleRate * 1000).toLong()
        Thread.sleep(durationMs.coerceAtLeast(0))
        track.stop()
        track.release()
        audioTrack = null
    }

    fun stop() {
        audioTrack?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        audioTrack = null
    }

    fun close() {
        stop()
        g2p?.close()
        ortSession?.close()
        g2p = null
        ortSession = null
        isReady = false
    }

    private fun parseConfig(json: String) {
        val root = JSONObject(json)
        root.optJSONObject("audio")?.let { audio ->
            sampleRate = audio.optInt("sample_rate", sampleRate)
        }
        root.optJSONObject("inference")?.let { inference ->
            noiseScale = inference.optDouble("noise_scale", noiseScale.toDouble()).toFloat()
            lengthScale = inference.optDouble("length_scale", lengthScale.toDouble()).toFloat()
            noiseW = inference.optDouble("noise_w", noiseW.toDouble()).toFloat()
        }
        val map = mutableMapOf<String, List<Long>>()
        root.optJSONObject("phoneme_id_map")?.let { idMap ->
            idMap.keys().forEach { phoneme ->
                val arr = idMap.getJSONArray(phoneme)
                map[phoneme] = (0 until arr.length()).map { arr.getLong(it) }
            }
        }
        phonemeIdMap = map
    }

    private fun readAssetTextOrNull(name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (e: java.io.FileNotFoundException) {
        null
    }
}
