package com.dti.kate.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipFile

class VoskManager(private val context: Context) {

    companion object {
        private const val TAG = "VoskManager"
        private const val MODEL_DIR = "vosk-model"
        private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val ZIP_NAME = "$MODEL_NAME.zip"
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null

    private val _status = MutableStateFlow<VoskStatus>(VoskStatus.NotInitialized)
    val status: StateFlow<VoskStatus> = _status

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

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
            Log.d(TAG, "Initializing Vosk...")

            val modelPath = ensureModelAvailable()
            if (modelPath == null) {
                _status.value = VoskStatus.Error
                callback(false)
                return@withContext
            }

            model = Model(modelPath)
            recognizer = Recognizer(model, 16000.0f)
            _status.value = VoskStatus.Ready
            Log.d(TAG, "Vosk initialized successfully")
            callback(true)

        } catch (e: Exception) {
            Log.e(TAG, "Vosk initialization failed", e)
            _status.value = VoskStatus.Error
            callback(false)
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
            return@withContext internalModelDir.absolutePath
        }

        val copiedFromAssets = copyAssetModelToInternalStorage(internalModelDir)
        if (copiedFromAssets) {
            Log.d(TAG, "Copied bundled model from assets to internal storage")
            return@withContext internalModelDir.absolutePath
        }

        Log.w(TAG, "Model not bundled in assets, attempting network download")
        return@withContext try {
            downloadModel(internalModelDir)
            internalModelDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Network model download failed", e)
            null
        }
    }

    /** Recursively copies assets/vosk-model/* to the given destination directory. */
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

    fun startListening(): Boolean {
        return try {
            if (recognizer == null || model == null) {
                Log.e(TAG, "Vosk not initialized")
                return false
            }
            recognizer?.reset()
            _isListening.value = true
            _transcription.value = ""
            Log.d(TAG, "Started listening")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            false
        }
    }

    fun feedAudio(audioData: ByteArray): String? {
        return try {
            if (!_isListening.value) return null

            if (recognizer?.acceptWaveForm(audioData, audioData.size) == true) {
                val result = recognizer?.result
                val transcription = parseVoskResult(result)
                _transcription.value = transcription
                transcription
            } else {
                val partial = recognizer?.partialResult
                val partialText = parseVoskPartial(partial)
                if (partialText.isNotEmpty()) {
                    _transcription.value = partialText
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process audio", e)
            null
        }
    }

    fun stopListening(): String? {
        return try {
            _isListening.value = false
            val finalResult = recognizer?.finalResult
            val finalText = parseVoskResult(finalResult)
            _transcription.value = finalText
            Log.d(TAG, "Stopped listening. Final: $finalText")
            finalText
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop listening", e)
            null
        }
    }

    private fun parseVoskResult(result: String?): String {
        if (result.isNullOrEmpty()) return ""
        return try {
            org.json.JSONObject(result).optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseVoskPartial(result: String?): String {
        if (result.isNullOrEmpty()) return ""
        return try {
            org.json.JSONObject(result).optString("partial", "")
        } catch (e: Exception) {
            ""
        }
    }

    fun reset() {
        recognizer?.reset()
        _transcription.value = ""
        Log.d(TAG, "Vosk reset")
    }

    fun shutdown() {
        recognizer?.close()
        model?.close()
        _isListening.value = false
        _status.value = VoskStatus.NotInitialized
        Log.d(TAG, "Vosk shutdown")
    }
}
