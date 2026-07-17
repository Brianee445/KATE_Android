// app/src/main/cpp/stt/speech_recognizer.cpp

#include "speech_recognizer.h"
#include <android/log.h>
#include <cstring>
#include <cstdlib>

#define LOG_TAG "SpeechRecognizer"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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
    // Manual lightweight parsing (no JSON library dependency for now -
    // will be revisited when the new model ships its own JSON format).
    // Vosk result strings look like: {"text": "hello world"} or
    // {"partial": "hel"} or {"text": "...", "confidence": 0.87}

    auto extractStringField = [&](const std::string& fieldName) -> std::string {
        std::string needle = "\"" + fieldName + "\"";
        size_t start = jsonStr.find(needle);
        if (start == std::string::npos) return "";

        start = jsonStr.find("\"", start + needle.length());
        if (start == std::string::npos) return "";
        start += 1;

        size_t end = jsonStr.find("\"", start);
        if (end == std::string::npos) return "";

        return jsonStr.substr(start, end - start);
    };

    std::string textField = extractStringField("text");
    if (!textField.empty()) {
        text = textField;
    }

    std::string partialField = extractStringField("partial");
    if (!partialField.empty()) {
        text = partialField;
    }

    size_t confPos = jsonStr.find("\"confidence\"");
    if (confPos != std::string::npos) {
        size_t colonPos = jsonStr.find(":", confPos);
        if (colonPos != std::string::npos) {
            const char* start = jsonStr.c_str() + colonPos + 1;
            char* endPtr = nullptr;
            float parsed = std::strtof(start, &endPtr);
            if (endPtr != start) {
                confidence = parsed;
            }
        }
    }
}

std::string SpeechRecognizer::getCurrentTranscription() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_currentText;
}

} // namespace kate
