// app/src/main/cpp/stt/vosk_wrapper.cpp

#include "vosk_wrapper.h"
#include <vosk_api.h>
#include <android/log.h>
#include <cstring>
#include <fstream>

#define LOG_TAG "VoskWrapper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace kate {

VoskWrapper::VoskWrapper() = default;

VoskWrapper::~VoskWrapper() {
    shutdown();
}

bool VoskWrapper::initialize(const std::string& modelPath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_initialized) {
        return true;
    }
    
    m_modelPath = modelPath;
    
    // Initialize Vosk - vosk_set_log_level may not exist, use vosk_set_log_level_callback instead
    // Just skip it if not available
    
    m_model = vosk_model_new(modelPath.c_str());
    if (!m_model) {
        LOGE("Failed to load Vosk model from: %s", modelPath.c_str());
        return false;
    }
    
    LOGI("Vosk model loaded successfully from: %s", modelPath.c_str());
    
    m_recognizer = vosk_recognizer_new(m_model, m_sampleRate);
    if (!m_recognizer) {
        LOGE("Failed to create Vosk recognizer");
        vosk_model_free(m_model);
        m_model = nullptr;
        return false;
    }
    
    m_initialized = true;
    LOGI("Vosk wrapper initialized");
    return true;
}

void VoskWrapper::shutdown() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_recognizer) {
        vosk_recognizer_free(m_recognizer);
        m_recognizer = nullptr;
    }
    
    if (m_model) {
        vosk_model_free(m_model);
        m_model = nullptr;
    }
    
    m_initialized = false;
    m_listening = false;
    LOGI("Vosk wrapper shutdown");
}

bool VoskWrapper::startListening() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (!m_initialized || !m_recognizer) {
        LOGE("Cannot start listening - not initialized");
        return false;
    }
    
    reset();
    m_listening = true;
    LOGI("Started listening");
    return true;
}

void VoskWrapper::stopListening() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (!m_listening) return;
    
    m_listening = false;
    
    // Get final result - use vosk_recognizer_result, not final_result
    const char* result = vosk_recognizer_result(m_recognizer);
    if (result && m_callback) {
        std::string text = result;
        m_callback(text, true);
    }
    
    LOGI("Stopped listening");
}

void VoskWrapper::feedAudio(const int16_t* data, size_t samples) {
    if (!m_listening || !m_recognizer) return;
    
    std::lock_guard<std::mutex> lock(m_mutex);
    
    // Feed audio to Vosk
    int result = vosk_recognizer_accept_waveform(m_recognizer, data, samples);
    
    if (result == 1) {
        // Final result available
        const char* json = vosk_recognizer_result(m_recognizer);
        if (json && m_callback) {
            m_callback(json, true);
        }
    } else {
        // Partial result
        const char* partial = vosk_recognizer_partial_result(m_recognizer);
        if (partial && m_callback) {
            m_callback(partial, false);
        }
    }
}

void VoskWrapper::reset() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_recognizer) {
        vosk_recognizer_reset(m_recognizer);
    }
}

std::string VoskWrapper::getPartialResult() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (!m_recognizer) return "";
    
    const char* partial = vosk_recognizer_partial_result(m_recognizer);
    return partial ? std::string(partial) : "";
}

} // namespace kate
