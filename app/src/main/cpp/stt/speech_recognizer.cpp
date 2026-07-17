// app/src/main/cpp/stt/speech_recognizer.cpp

#include "speech_recognizer.h"
#include <android/log.h>
#include <cstring>
#include <nlohmann/json.hpp>

#define LOG_TAG "SpeechRecognizer"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using json = nlohmann::json;

namespace kate {

SpeechRecognizer::SpeechRecognizer() {
    m_vosk = std::make_unique<VoskWrapper>();
}

SpeechRecognizer::~SpeechRecognizer() {
    shutdown();
}

bool SpeechRecognizer::initialize(const std::string& modelPath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_initialized) {
        return true;
    }
    
    // Initialize Vosk
    if (!m_vosk->initialize(modelPath)) {
        LOGE("Failed to initialize Vosk");
        return false;
    }
    
    // Set up callback
    m_vosk->setCallback([this](const std::string& result, bool isFinal) {
        this->processResult(result, isFinal);
    });
    
    m_initialized = true;
    LOGI("SpeechRecognizer initialized");
    return true;
}

void SpeechRecognizer::shutdown() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_running) {
        stop();
    }
    
    if (m_vosk) {
        m_vosk->shutdown();
        m_vosk.reset();
    }
    
    m_initialized = false;
    LOGI("SpeechRecognizer shutdown");
}

bool SpeechRecognizer::start() {
    if (!m_initialized) {
        LOGE("Cannot start - not initialized");
        return false;
    }
    
    if (m_running) {
        return true;
    }
    
    if (!m_vosk->startListening()) {
        LOGE("Failed to start Vosk listening");
        return false;
    }
    
    m_running = true;
    m_currentText.clear();
    m_confidence = 0.0f;
    
    LOGI("SpeechRecognizer started");
    return true;
}

void SpeechRecognizer::stop() {
    if (!m_running) return;
    
    m_vosk->stopListening();
    m_running = false;
    
    LOGI("SpeechRecognizer stopped");
}

void SpeechRecognizer::feedAudio(const int16_t* data, size_t samples) {
    if (!m_running || !m_vosk) return;
    
    m_vosk->feedAudio(data, samples);
}

void SpeechRecognizer::reset() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_vosk) {
        m_vosk->reset();
    }
    m_currentText.clear();
    m_confidence = 0.0f;
}

void SpeechRecognizer::processResult(const std::string& result, bool isFinal) {
    std::string text;
    float confidence = 0.0f;
    
    parseVoskResult(result, text, confidence);
    
    if (text.empty()) return;
    
    std::lock_guard<std::mutex> lock(m_mutex);
    m_currentText = text;
    m_confidence = confidence;
    
    if (m_callback) {
        m_callback(text, isFinal);
    }
}

void SpeechRecognizer::parseVoskResult(const std::string& jsonStr, std::string& text, float& confidence) {
    // Parse without exceptions: -fno-exceptions is set for this build,
    // so we use the non-throwing parse mode instead of try/catch.
    auto data = json::parse(jsonStr, nullptr, false); // false = don't throw, returns discarded value on failure

    if (data.is_discarded()) {
        // Fallback: try to extract text manually
        size_t start = jsonStr.find("\"text\"");
        if (start != std::string::npos) {
            start = jsonStr.find("\"", start + 7) + 1;
            size_t end = jsonStr.find("\"", start);
            if (end != std::string::npos) {
                text = jsonStr.substr(start, end - start);
            }
        }
        return;
    }

    // Check for text field
    if (data.contains("text")) {
        text = data["text"].get<std::string>();
    }

    // Check for partial field
    if (data.contains("partial")) {
        text = data["partial"].get<std::string>();
    }

    // Check for confidence
    if (data.contains("confidence")) {
        confidence = data["confidence"].get<float>();
    }
}

std::string SpeechRecognizer::getCurrentTranscription() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_currentText;
}

} // namespace kate
