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
import java.io.InputStream
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
            
            val modelPath = getModelPath()
            if (modelPath == null) {
                Log.e(TAG, "Model not found. Downloading...")
                downloadModel()
                val newPath = getModelPath()
                if (newPath == null) {
                    _status.value = VoskStatus.Error
                    callback(false)
                    return@withContext
                }
                model = Model(newPath)
            } else {
                model = Model(modelPath)
            }
            
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
    
    private fun getModelPath(): String? {
        val assetsDir = File(context.filesDir, MODEL_DIR)
        
        // Check if model exists in assets (extracted during build)
        val assetModelDir = File(context.filesDir, MODEL_DIR)
        if (assetModelDir.exists()) {
            val modelFile = File(assetModelDir, "am/final.mdl")
            if (modelFile.exists()) {
                Log.d(TAG, "Model found in assets: ${assetModelDir.absolutePath}")
                return assetModelDir.absolutePath
            }
        }
        
        // Check if model exists in internal storage (downloaded)
        val internalModelDir = File(context.filesDir, MODEL_DIR)
        if (internalModelDir.exists()) {
            val modelFile = File(internalModelDir, "am/final.mdl")
            if (modelFile.exists()) {
                Log.d(TAG, "Model found in internal storage: ${internalModelDir.absolutePath}")
                return internalModelDir.absolutePath
            }
        }
        
        // Check if model exists in cache (temporary)
        val cacheModelDir = File(context.cacheDir, MODEL_DIR)
        if (cacheModelDir.exists()) {
            val modelFile = File(cacheModelDir, "am/final.mdl")
            if (modelFile.exists()) {
                Log.d(TAG, "Model found in cache: ${cacheModelDir.absolutePath}")
                return cacheModelDir.absolutePath
            }
        }
        
        return null
    }
    
    private suspend fun downloadModel() = withContext(Dispatchers.IO) {
        try {
            _status.value = VoskStatus.LoadingModel
            Log.d(TAG, "Downloading Vosk model...")
            
            val destDir = File(context.filesDir, MODEL_DIR)
            destDir.mkdirs()
            
            val zipFile = File(destDir, ZIP_NAME)
            
            // Download from assets (bundled) or fallback to network
            val inputStream: InputStream? = try {
                // First try to get from assets (bundled in APK)
                context.assets.open("$MODEL_DIR/$ZIP_NAME")
            } catch (e: Exception) {
                Log.w(TAG, "Model not in assets, downloading from network")
                null
            }
            
            if (inputStream != null) {
                // Copy from assets
                FileOutputStream(zipFile).use { output ->
                    inputStream.copyTo(output)
                }
            } else {
                // Download from network
                val url = "https://alphacephei.com/vosk/models/$ZIP_NAME"
                URL(url).openStream().use { input ->
                    FileOutputStream(zipFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            // Extract
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
            
            // Verify
            val modelFile = File(destDir, "am/final.mdl")
            if (modelFile.exists()) {
                Log.d(TAG, "✅ Vosk model downloaded successfully")
            } else {
                throw Exception("Model file not found after extraction")
            }
            
            _status.value = VoskStatus.Ready
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download Vosk model", e)
            _status.value = VoskStatus.Error
            throw e
        }
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
        try {
            val json = org.json.JSONObject(result)
            return json.optString("text", "")
        } catch (e: Exception) {
            return ""
        }
    }
    
    private fun parseVoskPartial(result: String?): String {
        if (result.isNullOrEmpty()) return ""
        try {
            val json = org.json.JSONObject(result)
            return json.optString("partial", "")
        } catch (e: Exception) {
            return ""
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
