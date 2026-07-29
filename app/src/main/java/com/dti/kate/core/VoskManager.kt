package com.dti.kate.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipFile
import kotlin.concurrent.withLock

/**
 * Speech-to-text manager.
 *
 * Backed by the official org.vosk (JNA) bindings, pinned to vosk-android
 * 0.3.47 - NOT Kate's custom C++ engine (kate_engine.so), which this class
 * used to route through.
 *
 * Why the switch back: the custom engine's native pipeline passed every
 * structural check we could find - model loaded, engine initialized,
 * states transitioned correctly, JNI callbacks proven working via
 * onStateChange - and yet consistently produced zero transcriptions on
 * clearly speech-level audio, across several rounds of fixing real bugs
 * in that pipeline (a JSON double-parsing bug, the original JSON
 * extraction bug, audio source/gain). The remaining failure was never
 * isolated.
 *
 * Meanwhile a sibling Kate project (different package name, same org)
 * uses org.vosk directly and works correctly - its only documented bug
 * was a thread-safety race (SIGSEGV from two threads touching the
 * non-thread-safe Recognizer at once), never a recognition failure. That
 * project pins vosk-android 0.3.47, which is the last release before a
 * ~2.5 year gap in upstream releases. We'd previously hit
 * UnsatisfiedLinkError (vosk_recognizer_set_endpointer_delays undefined
 * symbol) on 0.3.75 and 0.3.70 - both from *after* that gap, where the
 * Java bindings and bundled native .so were evidently out of sync - and
 * concluded org.vosk was unusable here without ever trying 0.3.47
 * specifically. It avoids that crash.
 *
 * This port keeps that project's two real fixes:
 *  - recognizerLock (ReentrantLock) around every native Vosk call, since
 *    libvosk.so's Recognizer is not thread-safe and our feedAudio() runs
 *    on AudioCapture's IO-dispatcher thread while stopListening() can be
 *    triggered from a different (UI/coroutine) thread.
 *  - Stricter model validation (am/ AND conf/ subdirectories present),
 *    not just a single file's existence.
 */
class VoskManager(private val context: Context) {

    companion object {
        private const val TAG = "VoskManager"
        private const val MODEL_DIR = "vosk-model"
        private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val ZIP_NAME = "$MODEL_NAME.zip"
        private const val SAMPLE_RATE = 16000f
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null

    // All native Vosk calls (acceptWaveForm, result, partialResult, reset,
    // close) are serialized through this lock - see class doc.
    private val recognizerLock = ReentrantLock()

    private val _status = MutableStateFlow<VoskStatus>(VoskStatus.NotInitialized)
    val status: StateFlow<VoskStatus> = _status

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private var chunkCount = 0

    sealed class VoskStatus {
        object NotInitialized : VoskStatus()
        object Initializing : VoskStatus()
        object Ready : VoskStatus()
        object Error : VoskStatus()
        object LoadingModel : VoskStatus()
    }

    suspend fun initialize(callback: (Boolean) -> Unit) = withContext(Dispatchers.IO) {
        try {
            _status.value = VoskStatus.Initializing
            Log.d(TAG, "Initializing org.vosk speech engine...")
            DebugLog.log(context, TAG, "initialize() start (org.vosk / vosk-android 0.3.47)")

            val modelDirPath = ensureModelAvailable()
            if (modelDirPath == null) {
                _status.value = VoskStatus.Error
                DebugLog.log(context, TAG, "initialize() FAILED: no valid model available")
                callback(false)
                return@withContext
            }

            recognizerLock.withLock {
                model = Model(modelDirPath)
                recognizer = Recognizer(model, SAMPLE_RATE)
            }

            _status.value = VoskStatus.Ready
            Log.d(TAG, "org.vosk initialized successfully")
            DebugLog.log(context, TAG, "initialize() succeeded, modelDir=$modelDirPath")
            callback(true)

        } catch (e: Exception) {
            Log.e(TAG, "org.vosk initialization failed", e)
            DebugLog.log(context, TAG, "initialize() EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            _status.value = VoskStatus.Error
            callback(false)
        }
    }

    fun startListening(): Boolean {
        if (recognizer == null) {
            Log.e(TAG, "Cannot start listening - recognizer not initialized")
            DebugLog.log(context, TAG, "startListening() FAILED: recognizer is null")
            return false
        }
        pendingFinalText = null
        _transcription.value = ""
        chunkCount = 0
        recognizerLock.withLock {
            try { recognizer?.reset() } catch (e: Exception) {
                Log.e(TAG, "reset() before listening failed", e)
            }
        }
        _isListening.value = true
        DebugLog.log(context, TAG, "startListening() -> true")
        return true
    }

    @Volatile
    private var pendingFinalText: String? = null

    /** Feeds a chunk of PCM16 mono audio. Returns the final transcript if this chunk completed one, else null. */
    fun feedAudio(audioData: ByteArray): String? {
        if (!_isListening.value || recognizer == null) return null

        chunkCount++
        val logThisCall = (chunkCount == 1 || chunkCount % 25 == 0)

        return try {
            var isFinal = false
            var resultJson = "{}"
            var partialJson = "{}"

            recognizerLock.withLock {
                isFinal = try {
                    recognizer?.acceptWaveForm(audioData, audioData.size) ?: false
                } catch (e: Exception) {
                    Log.e(TAG, "acceptWaveForm failed", e)
                    DebugLog.log(context, TAG, "acceptWaveForm() EXCEPTION at chunk #$chunkCount: ${e.javaClass.simpleName}: ${e.message}")
                    false
                }

                if (isFinal) {
                    resultJson = try { recognizer?.result ?: "{}" } catch (e: Exception) { "{}" }
                } else {
                    partialJson = try { recognizer?.partialResult ?: "{}" } catch (e: Exception) { "{}" }
                }
            }

            if (isFinal) {
                val text = JSONObject(resultJson).optString("text", "").trim()
                if (logThisCall || text.isNotEmpty()) {
                    DebugLog.log(context, TAG, "final at chunk #$chunkCount: \"$text\" raw=$resultJson")
                }
                if (text.isNotEmpty()) {
                    _transcription.value = text
                    pendingFinalText = text
                    return text
                }
                null
            } else {
                val partial = JSONObject(partialJson).optString("partial", "").trim()
                if (logThisCall) {
                    DebugLog.log(context, TAG, "partial at chunk #$chunkCount: \"$partial\" raw=$partialJson")
                }
                if (partial.isNotEmpty()) {
                    _transcription.value = partial
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process audio", e)
            DebugLog.log(context, TAG, "feedAudio() EXCEPTION at chunk #$chunkCount: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun stopListening(): String? {
        _isListening.value = false
        return try {
            var resultJson = "{}"
            recognizerLock.withLock {
                resultJson = try { recognizer?.finalResult ?: recognizer?.result ?: "{}" } catch (e: Exception) { "{}" }
            }
            val text = JSONObject(resultJson).optString("text", "").trim()
            val result = text.ifEmpty { pendingFinalText } ?: _transcription.value.takeIf { it.isNotBlank() }
            pendingFinalText = null
            Log.d(TAG, "Stopped listening. Final: $result")
            DebugLog.log(context, TAG, "stopListening() -> chunks fed=$chunkCount, result=${result?.let { "\"$it\"" } ?: "null"}, raw=$resultJson")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop listening", e)
            DebugLog.log(context, TAG, "stopListening() EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Returns a real filesystem path to the model, copying it out of
     * assets on first run if needed. Falls back to a network download
     * only if the model isn't bundled in assets at all.
     *
     * Validates both am/ and conf/ subdirectories exist, not just one
     * file's presence - a lesson from the sibling project's
     * isValidModelDir(), which is a meaningfully stricter check than what
     * this class used to do.
     */
    private suspend fun ensureModelAvailable(): String? = withContext(Dispatchers.IO) {
        val internalModelDir = File(context.filesDir, MODEL_DIR)
        if (isValidModelDir(internalModelDir)) {
            Log.d(TAG, "Model already present in internal storage")
            DebugLog.log(context, TAG, "ensureModelAvailable(): cached model found and validated (am/ + conf/ present)")
            return@withContext internalModelDir.absolutePath
        }

        val copiedFromAssets = copyAssetModelToInternalStorage(internalModelDir)
        if (copiedFromAssets && isValidModelDir(internalModelDir)) {
            Log.d(TAG, "Copied bundled model from assets to internal storage")
            DebugLog.log(context, TAG, "ensureModelAvailable(): copied from assets and validated")
            return@withContext internalModelDir.absolutePath
        }

        Log.w(TAG, "Model not bundled in assets, attempting network download")
        DebugLog.log(context, TAG, "ensureModelAvailable(): no valid bundled model, attempting network download")
        return@withContext try {
            downloadModel(internalModelDir)
            if (!isValidModelDir(internalModelDir)) {
                throw Exception("Downloaded model failed validation (am/ or conf/ missing)")
            }
            DebugLog.log(context, TAG, "ensureModelAvailable(): download succeeded and validated")
            internalModelDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Network model download failed", e)
            DebugLog.log(context, TAG, "ensureModelAvailable(): download FAILED: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun isValidModelDir(dir: File): Boolean =
        dir.exists() && File(dir, "am").isDirectory && File(dir, "conf").isDirectory

    /** Recursively copies the assets model directory to the given destination directory. */
    private fun copyAssetModelToInternalStorage(destDir: File): Boolean {
        return try {
            val assetFiles = context.assets.list(MODEL_DIR)
            if (assetFiles.isNullOrEmpty()) return false

            destDir.mkdirs()
            copyAssetDirRecursive(MODEL_DIR, destDir)
            true
        } catch (e: Exception) {
            Log.w(TAG, "No bundled model found in assets: ${e.message}")
            false
        }
    }

    private fun copyAssetDirRecursive(assetPath: String, targetDir: File) {
        val entries = context.assets.list(assetPath) ?: return

        if (entries.isEmpty()) {
            // It's a file, not a directory
            targetDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetDir).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        targetDir.mkdirs()
        for (entry in entries) {
            val childAssetPath = "$assetPath/$entry"
            val childTarget = File(targetDir, entry)
            val childEntries = context.assets.list(childAssetPath)
            if (childEntries.isNullOrEmpty()) {
                context.assets.open(childAssetPath).use { input ->
                    FileOutputStream(childTarget).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                copyAssetDirRecursive(childAssetPath, childTarget)
            }
        }
    }

    private fun downloadModel(destDir: File) {
        _status.value = VoskStatus.LoadingModel
        Log.d(TAG, "Downloading Vosk model from network...")

        destDir.mkdirs()
        val zipFile = File(destDir.parentFile, ZIP_NAME)

        val url = "https://alphacephei.com/vosk/models/$ZIP_NAME"
        URL(url).openStream().use { input ->
            FileOutputStream(zipFile).use { output ->
                input.copyTo(output)
            }
        }

        Log.d(TAG, "Extracting Vosk model...")
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory) {
                    // Model zips from alphacephei nest everything under a
                    // top-level "<model-name>/" folder - strip that so
                    // am/, conf/, graph/ land directly under destDir.
                    val relativeName = entry.name.substringAfter('/')
                    if (relativeName.isNotBlank()) {
                        val targetFile = File(destDir, relativeName)
                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }

        zipFile.delete()
        Log.d(TAG, "Vosk model downloaded and extracted")
        _status.value = VoskStatus.Ready
    }
}
