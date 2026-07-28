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

namespace {

// Vosk's C API returns JSON strings like {"text" : "hello world"} for final
// results and {"partial" : "hello"} for partials - never plain text. This
// extracts a single string field from that flat JSON shape without pulling
// in a full JSON dependency, since Vosk's Android output never nests beyond
// one level for these two fields.
std::string extractJsonField(const std::string& json, const std::string& key) {
    const std::string needle = "\"" + key + "\"";
    size_t keyPos = json.find(needle);
    if (keyPos == std::string::npos) return "";

    size_t colonPos = json.find(':', keyPos + needle.size());
    if (colonPos == std::string::npos) return "";

    size_t firstQuote = json.find('"', colonPos + 1);
    if (firstQuote == std::string::npos) return "";

    std::string value;
    value.reserve(32);
    for (size_t i = firstQuote + 1; i < json.size(); ++i) {
        char c = json[i];
        if (c == '\\' && i + 1 < json.size()) {
            char next = json[i + 1];
            switch (next) {
                case '"':  value += '"';  break;
                case '\\': value += '\\'; break;
                case 'n':  value += '\n'; break;
                case 't':  value += '\t'; break;
                default:   value += next; break;
            }
            ++i;
        } else if (c == '"') {
            break;
        } else {
            value += c;
        }
    }
    return value;
}

} // namespace

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
        std::string text = extractJsonField(result, "text");
        if (!text.empty()) {
            m_callback(text, true);
        }
    }
    
    LOGI("Stopped listening");
}

void VoskWrapper::feedAudio(const int16_t* data, size_t samples) {
    if (!m_listening || !m_recognizer) return;

    std::lock_guard<std::mutex> lock(m_mutex);

    ++m_feedCount;
    const bool logThisCall = (m_feedCount == 1 || m_feedCount % 25 == 0);

    // Feed audio to Vosk
    int result = vosk_recognizer_accept_waveform_s(m_recognizer, data, static_cast<int>(samples));

    if (result == -1) {
        // Vosk hit an internal exception processing this chunk - previously
        // this was silently treated the same as "no result yet" (result 0),
        // which would hide a real decoding failure behind what looks like
        // ordinary silence.
        LOGE("vosk_recognizer_accept_waveform_s returned -1 (internal error) at feed #%zu", m_feedCount);
        if (m_callback) {
            m_callback("[DIAG] accept_waveform_s returned -1 (error) at feed #" + std::to_string(m_feedCount), false);
        }
        return;
    }

    if (result == 1) {
        // Final result available
        const char* json = vosk_recognizer_result(m_recognizer);
        if (json && m_callback) {
            std::string text = extractJsonField(json, "text");
            if (!text.empty()) {
                m_callback(text, true);
            } else if (logThisCall) {
                m_callback("[DIAG] final result but empty text, raw=" + std::string(json), false);
            }
        }
    } else {
        // Partial result
        const char* partial = vosk_recognizer_partial_result(m_recognizer);
        if (partial && m_callback) {
            std::string text = extractJsonField(partial, "partial");
            if (!text.empty()) {
                m_callback(text, false);
            } else if (logThisCall) {
                m_callback("[DIAG] partial empty, raw=" + std::string(partial), false);
            }
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
