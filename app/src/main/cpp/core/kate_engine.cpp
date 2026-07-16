// app/src/main/cpp/core/kate_engine.cpp

#include "kate_engine.h"
#include <android/log.h>
#include <chrono>
#include <thread>
#include <cstring>

#define LOG_TAG "KateEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace kate {

KateEngine& KateEngine::getInstance() {
    static KateEngine instance;
    return instance;
}

bool KateEngine::initialize(const std::string& modelPath, const std::string& configPath) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_initialized) {
        LOGI("Engine already initialized");
        return true;
    }
    
    setState(EngineState::INITIALIZING);
    LOGI("Initializing Kate Engine...");
    
    try {
        // Initialize audio pipeline
        m_audioPipeline = std::make_unique<AudioPipeline>();
        if (!m_audioPipeline->initialize(16000, 1)) {
            LOGE("Failed to initialize audio pipeline");
            setState(EngineState::ERROR);
            return false;
        }
        
        // Initialize speech recognizer (Vosk)
        m_speechRecognizer = std::make_unique<SpeechRecognizer>();
        if (!m_speechRecognizer->initialize(modelPath + "/vosk-model")) {
            LOGE("Failed to initialize speech recognizer");
            setState(EngineState::ERROR);
            return false;
        }
        
        // Initialize intent engine (TFLite)
        m_intentEngine = std::make_unique<IntentEngine>();
        if (!m_intentEngine->initialize(modelPath + "/intent_model.tflite")) {
            LOGE("Failed to initialize intent engine");
            // Continue anyway - fallback to rule-based
        }
        
        // Initialize TTS
        m_ttsSynthesizer = std::make_unique<TTSSynthesizer>();
        if (!m_ttsSynthesizer->initialize()) {
            LOGE("Failed to initialize TTS");
            // Continue anyway - use Android TTS fallback
        }
        
        // Initialize conversation cache
        m_cache = std::make_unique<ConversationCache>();
        if (!m_cache->initialize(configPath + "/cache.db")) {
            LOGE("Failed to initialize cache");
            // Continue anyway - cache will be memory-only
        }
        
        // Set up audio callback
        m_audioPipeline->setCallback([this](const int16_t* data, size_t samples) {
            this->processAudioCallback(data, samples);
        });
        
        // Set up speech recognizer callback
        m_speechRecognizer->setCallback([this](const std::string& text, bool isFinal) {
            this->onTranscription(text, isFinal);
        });
        
        m_initialized = true;
        setState(EngineState::READY);
        LOGI("Kate Engine initialized successfully");
        return true;
        
    } catch (const std::exception& e) {
        LOGE("Initialization error: %s", e.what());
        setState(EngineState::ERROR);
        return false;
    }
}

void KateEngine::shutdown() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (!m_initialized) return;
    
    LOGI("Shutting down Kate Engine...");
    
    m_running = false;
    if (m_processingThread.joinable()) {
        m_processingThread.join();
    }
    
    if (m_audioPipeline) {
        m_audioPipeline->stop();
        m_audioPipeline.reset();
    }
    
    if (m_speechRecognizer) {
        m_speechRecognizer->shutdown();
        m_speechRecognizer.reset();
    }
    
    if (m_intentEngine) {
        m_intentEngine.reset();
    }
    
    if (m_ttsSynthesizer) {
        m_ttsSynthesizer.reset();
    }
    
    if (m_cache) {
        m_cache.reset();
    }
    
    m_initialized = false;
    setState(EngineState::IDLE);
    LOGI("Kate Engine shutdown complete");
}

bool KateEngine::startListening() {
    if (!m_initialized) {
        LOGE("Engine not initialized");
        return false;
    }
    
    if (m_state == EngineState::LISTENING) {
        return true;
    }
    
    setState(EngineState::LISTENING);
    m_speechRecognizer->reset();
    
    if (!m_audioPipeline->start()) {
        LOGE("Failed to start audio pipeline");
        setState(EngineState::ERROR);
        return false;
    }
    
    LOGI("Started listening");
    return true;
}

void KateEngine::stopListening() {
    if (m_state != EngineState::LISTENING) return;
    
    m_audioPipeline->stop();
    m_speechRecognizer->stop();
    setState(EngineState::PROCESSING);
    LOGI("Stopped listening");
}

void KateEngine::feedAudio(const int16_t* data, size_t samples) {
    if (m_state != EngineState::LISTENING) return;
    m_speechRecognizer->feedAudio(data, samples);
}

void KateEngine::processAudioCallback(const int16_t* data, size_t samples) {
    // This is called from the audio pipeline thread
    // Forward to speech recognizer
    if (m_state == EngineState::LISTENING) {
        m_speechRecognizer->feedAudio(data, samples);
    }
}

void KateEngine::onTranscription(const std::string& text, bool isFinal) {
    if (text.empty()) return;
    
    // Notify via callback
    if (m_onTranscription) {
        m_onTranscription(text, isFinal);
    }
    
    if (isFinal) {
        // Process the final transcription
        std::string response = processTranscription(text);
        
        // Cache the conversation
        ConversationRecord record;
        record.query = text;
        record.response = response;
        record.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()
        ).count();
        cacheConversation(record);
        
        // Notify response
        if (m_onResponse) {
            m_onResponse(response);
        }
        
        setState(EngineState::READY);
    }
}

std::string KateEngine::processTranscription(const std::string& text) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    setState(EngineState::PROCESSING);
    
    // 1. Check cache first
    std::string cachedResponse = m_cache->getResponse(text);
    if (!cachedResponse.empty()) {
        LOGI("Cache hit for: %s", text.c_str());
        return cachedResponse;
    }
    
    // 2. Classify intent
    IntentResult result = classifyIntent(text);
    
    // 3. Generate response
    std::string response = result.response;
    if (response.empty()) {
        response = "I'm not sure how to help with that. Try asking differently.";
    }
    
    // 4. Cache for future
    m_cache->store(text, response, result.intent, result.confidence);
    
    return response;
}

IntentResult KateEngine::classifyIntent(const std::string& text) {
    IntentResult result;
    result.intent = "unknown";
    result.confidence = 0.0f;
    
    if (m_intentEngine) {
        result = m_intentEngine->classify(text);
    }
    
    // Fallback to rule-based if ML fails or confidence is low
    if (result.confidence < 0.6f) {
        // Rule-based matching
        std::string lower = text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        if (lower.find("open") != std::string::npos) {
            result.intent = "open_app";
            result.confidence = 0.7f;
            
            // Extract app name
            size_t pos = lower.find("open");
            if (pos != std::string::npos) {
                std::string app = lower.substr(pos + 5);
                if (!app.empty()) {
                    result.entities.push_back({"app", app});
                    result.target = app;
                }
            }
            result.action = "open_app";
            result.response = "Opening " + (result.target.empty() ? "the app" : result.target);
            
        } else if (lower.find("type") != std::string::npos || 
                   lower.find("write") != std::string::npos) {
            result.intent = "type_text";
            result.confidence = 0.7f;
            result.action = "type_text";
            
            // Extract text to type
            for (const auto& trigger : {"type", "write", "enter", "input"}) {
                size_t pos = lower.find(trigger);
                if (pos != std::string::npos) {
                    std::string textToType = text.substr(pos + strlen(trigger));
                    if (!textToType.empty()) {
                        result.entities.push_back({"text", textToType});
                        result.target = textToType;
                    }
                    break;
                }
            }
            result.response = "Typing...";
            
        } else if (lower.find("search") != std::string::npos || 
                   lower.find("find") != std::string::npos) {
            result.intent = "search";
            result.confidence = 0.6f;
            result.action = "search";
            result.response = "Searching...";
            
        } else if (lower.find("help") != std::string::npos || 
                   lower.find("what can you do") != std::string::npos) {
            result.intent = "help";
            result.confidence = 0.8f;
            result.action = "help";
            result.response = "I can open apps, type text, search, and more. Just ask!";
        }
    }
    
    return result;
}

std::string KateEngine::synthesizeSpeech(const std::string& text, float tone) {
    if (m_ttsSynthesizer) {
        return m_ttsSynthesizer->synthesize(text, tone);
    }
    return text; // Fallback to text-only
}

bool KateEngine::cacheConversation(const ConversationRecord& record) {
    std::lock_guard<std::mutex> lock(m_historyMutex);
    m_conversationHistory.push_back(record);
    
    // Trim history if too large
    if (m_conversationHistory.size() > 1000) {
        m_conversationHistory.erase(m_conversationHistory.begin());
    }
    
    return m_cache ? m_cache->storeConversation(record) : false;
}

std::string KateEngine::getCachedResponse(const std::string& query) {
    return m_cache ? m_cache->getResponse(query) : "";
}

void KateEngine::setState(EngineState state) {
    m_state = state;
    if (m_onStateChange) {
        m_onStateChange(state);
    }
}

} // namespace kate
