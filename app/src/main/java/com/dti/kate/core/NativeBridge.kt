package com.dti.kate.core

import android.util.Log

class NativeBridge {
    
    companion object {
        private const val TAG = "NativeBridge"
        
        init {
            System.loadLibrary("kate_engine")
        }
    }
    
    // Callback interface for Kotlin
    interface Callbacks {
        fun onTranscription(text: String, isFinal: Boolean)
        fun onResponse(response: String)
        fun onError(error: String)
        fun onStateChange(state: Int)
    }
    
    private var callbacks: Callbacks? = null
    
    // Native methods
    external fun initializeEngine(modelPath: String, configPath: String): Boolean
    external fun shutdownEngine()
    external fun startListening(): Boolean
    external fun stopListening()
    external fun isListening(): Boolean
    external fun feedAudio(audioData: ByteArray)
    external fun processTranscription(text: String): String
    external fun synthesizeSpeech(text: String, tone: Float): String
    external fun getCachedResponse(query: String): String
    external fun setCallbacks(callbackObject: Any)
    external fun clearCallbacks()
    
    fun setCallbacks(callbacks: Callbacks) {
        this.callbacks = callbacks
        setCallbacks(this)
    }
    
    // These methods are called from native code via JNI
    @Suppress("unused")
    fun onTranscription(text: String, isFinal: Boolean) {
        Log.d(TAG, "onTranscription: $text, isFinal: $isFinal")
        callbacks?.onTranscription(text, isFinal)
    }
    
    @Suppress("unused")
    fun onResponse(response: String) {
        Log.d(TAG, "onResponse: $response")
        callbacks?.onResponse(response)
    }
    
    @Suppress("unused")
    fun onError(error: String) {
        Log.e(TAG, "onError: $error")
        callbacks?.onError(error)
    }
    
    @Suppress("unused")
    fun onStateChange(state: Int) {
        Log.d(TAG, "onStateChange: $state")
        callbacks?.onStateChange(state)
    }
}
