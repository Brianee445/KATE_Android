package com.dti.kate.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipFile

/**
 * Speech-to-text manager.
 *
 * Backed by Kate's own native engine (kate_engine.so via [NativeBridge]) —
 * NOT the third-party org.vosk / JNA Java bindings that this class used to
 * wrap.
 *
 * Why the switch: org.vosk.LibVosk binds its *entire* declared native API
 * surface atomically the first time org.vosk.Model/Recognizer is touched
 * (JNA's Native.register() resolves every method in one shot in a static
 * initializer). If even one declared symbol - e.g.
 * vosk_recognizer_set_endpointer_delays - is missing from whatever
 * libvosk.so the vosk-android AAR happens to bundle, construction throws
 * UnsatisfiedLinkError every single time, regardless of whether we ever
 * call that function. This reproduced identically across the vosk-android
 * 0.3.70 and 0.3.75 releases, so it isn't a one-off publishing glitch we
 * can dodge with a version pin - it's inherent to depending on the AAR's
 * all-or-nothing JNA binding at all.
 *
 * Our own NativeBridge (app/src/main/cpp/jni/native_bridge.cpp ->
 * kate_engine.so -> vosk_wrapper.cpp) only declares and links against the
 * handful of Vosk C functions we actually use, so it can't fail this way -
 * and it's already the JNI bridge the rest of the app (KateResponseGenerator)
 * is built around, so this also removes a redundant, duplicate STT stack.
 */
class VoskManager(private val context: Context) {

    companion object {
        private const val TAG = "VoskManager"
        private const val MODEL_DIR = "vosk-model"
        private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val ZIP_NAME = "$MODEL_NAME.zip"
    }

    private val bridge = NativeBridge()

    private val _status = MutableStateFlow<VoskStatus>(VoskStatus.NotInitialized)
    val status: StateFlow<VoskStatus> = _status

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    // Set synchronously by the native callback (same call stack as feedAudio/
    // stopListening, since the native engine calls back before returning -
    // see vosk_wrapper.cpp's feedAudio, which invokes the result callback
    // inline rather than off a separate thread). Consumed and cleared by
    // whichever Kotlin call triggered it.
    @Volatile
    private var pendingFinalText: String? = null

    sealed class VoskStatus {
        object NotInitialized : VoskStatus()
        object Initializing : VoskStatus()
        object Ready : VoskStatus()
        object Error : VoskStatus()
        object LoadingModel : VoskStatus()
    }

    private val nativeCallbacks = object : NativeBridge.Callbacks {
        override fun onTranscription(text: String, isFinal: Boolean) {
            DebugLog.log(context, TAG, "onTranscription(isFinal=$isFinal, len=${text.length}): \"$text\"")
            _transcription.value = text
            if (isFinal) {
                pendingFinalText = text
            }
        }

        override fun onResponse(response: String) {
            // Not used for plain STT - Kate's response generation is driven
            // from Kotlin (KateResponseGenerator) once a final transcript
            // comes back from here, not from the native engine's own
            // response pipeline.
        }

        override fun onError(error: String) {
            Log.e(TAG, "Native engine error: $error")
            DebugLog.log(context, TAG, "onError: $error")
        }

        override fun onStateChange(state: Int) {
            DebugLog.log(context, TAG, "onStateChange: $state")
        }
    }

    suspend fun initialize(callback: (Boolean) -> Unit) = withContext(Dispatchers.IO) {
        try {
            _status.value = VoskStatus.Initializing
            Log.d(TAG, "Initializing native speech engine...")
            DebugLog.log(context, TAG, "initialize() start")

            val modelDirPath = ensureModelAvailable()
            if (modelDirPath == null) {
                _status.value = VoskStatus.Error
                DebugLog.log(context, TAG, "initialize() FAILED: no model available (assets copy and network download both failed)")
                callback(false)
                return@withContext
            }

            // KateEngine::initialize() appends "/vosk-model" to whatever root
            // path it's given, so we hand it the *parent* of the model
            // directory ensureModelAvailable() just prepared.
            val modelRootPath = File(modelDirPath).parentFile?.absolutePath
                ?: context.filesDir.absolutePath

            val configDir = File(context.filesDir, "config").apply { mkdirs() }
            DebugLog.log(context, TAG, "modelRootPath=$modelRootPath configDir=${configDir.absolutePath}")

            bridge.setCallbacks(nativeCallbacks)
            val ok = bridge.initializeEngine(modelRootPath, configDir.absolutePath)

            _status.value = if (ok) VoskStatus.Ready else VoskStatus.Error
            Log.d(TAG, if (ok) "Native engine initialized successfully" else "Native engine failed to initialize")
            DebugLog.log(context, TAG, "bridge.initializeEngine() returned $ok")
            callback(ok)

        } catch (e: Exception) {
            Log.e(TAG, "Native engine initialization failed", e)
            DebugLog.log(context, TAG, "initialize() EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            _status.value = VoskStatus.Error
            callback(false)
        }
    }

    private var chunkCount = 0

    fun startListening(): Boolean {
        pendingFinalText = null
        _transcription.value = ""
        chunkCount = 0
        val started = bridge.startListening()
        _isListening.value = started
        DebugLog.log(context, TAG, "startListening() -> $started")
        if (!started) {
            Log.e(TAG, "Native engine refused to start listening (not initialized?)")
        }
        return started
    }

    /** Feeds a chunk of PCM16 mono audio. Returns the final transcript if this chunk completed one, else null. */
    fun feedAudio(audioData: ByteArray): String? {
        if (!_isListening.value) return null
        return try {
            chunkCount++
            if (chunkCount == 1 || chunkCount % 25 == 0) {
                DebugLog.log(context, TAG, "feedAudio() chunk #$chunkCount, ${audioData.size} bytes")
            }
            bridge.feedAudio(audioData)
            val final = pendingFinalText
            if (final != null) pendingFinalText = null
            final
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process audio", e)
            DebugLog.log(context, TAG, "feedAudio() EXCEPTION at chunk #$chunkCount: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun stopListening(): String? {
        return try {
            bridge.stopListening()
            _isListening.value = false
            // Prefer a final result the callback already delivered; fall back
            // to whatever partial transcript is on screen so the user's
            // words aren't silently dropped if Vosk never finalized in time.
            val result = pendingFinalText ?: _transcription.value.takeIf { it.isNotBlank() }
            pendingFinalText = null
            Log.d(TAG, "Stopped listening. Final: $result")
            DebugLog.log(context, TAG, "stopListening() -> chunks fed=$chunkCount, result=${result?.let { "\"$it\"" } ?: "null"}")
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
     */
    private suspend fun ensureModelAvailable(): String? = withContext(Dispatchers.IO) {
        val internalModelDir = File(context.filesDir, MODEL_DIR)
        val internalModelFile = File(internalModelDir, "am/final.mdl")
        if (internalModelFile.exists()) {
            Log.d(TAG, "Model already present in internal storage")
            DebugLog.log(context, TAG, "ensureModelAvailable(): cached model found, am/final.mdl=${internalModelFile.length()} bytes")
            return@withContext internalModelDir.absolutePath
        }

        val copiedFromAssets = copyAssetModelToInternalStorage(internalModelDir)
        if (copiedFromAssets) {
            Log.d(TAG, "Copied bundled model from assets to internal storage")
            DebugLog.log(context, TAG, "ensureModelAvailable(): copied from assets, am/final.mdl=${internalModelFile.length()} bytes")
            return@withContext internalModelDir.absolutePath
        }

        Log.w(TAG, "Model not bundled in assets, attempting network download")
        DebugLog.log(context, TAG, "ensureModelAvailable(): no bundled model in assets, attempting network download")
        return@withContext try {
            downloadModel(internalModelDir)
            DebugLog.log(context, TAG, "ensureModelAvailable(): download succeeded, am/final.mdl=${internalModelFile.length()} bytes")
            internalModelDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Network model download failed", e)
            DebugLog.log(context, TAG, "ensureModelAvailable(): download FAILED: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Recursively copies the assets model directory to the given destination directory. */
    private fun copyAssetModelToInternalStorage(destDir: File): Boolean {
        return try {
            val assetFiles = context.assets.list(MODEL_DIR)
            if (assetFiles.isNullOrEmpty()) return false

            destDir.mkdirs()
            copyAssetDirRecursive(MODEL_DIR, destDir)

            File(destDir, "am/final.mdl").exists()
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
                    val targetFile = File(destDir, entry.name)
                    targetFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        zipFile.delete()

        val modelFile = File(destDir, "am/final.mdl")
        if (!modelFile.exists()) {
            throw Exception("Model file not found after extraction")
        }
        Log.d(TAG, "✅ Vosk model downloaded successfully")
        _status.value = VoskStatus.Ready
    }
}
