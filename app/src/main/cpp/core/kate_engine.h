// app/src/main/cpp/core/kate_engine.h

#ifndef KATE_ENGINE_H
#define KATE_ENGINE_H

#include <string>
#include <vector>
#include <memory>
#include <atomic>
#include <functional>
#include <mutex>
#include <thread>

#include "audio_pipeline.h"
#include "../stt/speech_recognizer.h"
#include "../intent/intent_engine.h"  // This defines IntentResult
#include "../tts/tts_synthesizer.h"
#include "../cache/conversation_cache.h"
#include "../cache/conversation_record.h"

namespace kate {

// Engine state
enum class EngineState {
    IDLE,
    INITIALIZING,
    READY,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
};

// IntentResult is defined in intent_engine.h - don't redefine it here
// Just use the one from intent_engine.h

// Callback types
using OnTranscriptionCallback = std::function<void(const std::string&, bool)>;
using OnResponseCallback = std::function<void(const std::string&)>;
using OnErrorCallback = std::function<void(const std::string&)>;
using OnStateChangeCallback = std::function<void(EngineState)>;

class KateEngine {
public:
    static KateEngine& getInstance();
    
    // Lifecycle
    bool initialize(const std::string& modelPath, const std::string& configPath);
    void shutdown();
    bool isInitialized() const { return m_initialized; }
    
    // Audio
    bool startListening();
    void stopListening();
    bool isListening() const { return m_state == EngineState::LISTENING; }
    void feedAudio(const int16_t* data, size_t samples);
    
    // Processing
    std::string processTranscription(const std::string& text);
    IntentResult classifyIntent(const std::string& text);
    std::string synthesizeSpeech(const std::string& text, float tone = 0.5f);
    
    // Cache
    bool cacheConversation(const ConversationRecord& record);
    std::string getCachedResponse(const std::string& query);
    
    // Callbacks
    void setOnTranscription(OnTranscriptionCallback callback) { m_onTranscription = callback; }
    void setOnResponse(OnResponseCallback callback) { m_onResponse = callback; }
    void setOnError(OnErrorCallback callback) { m_onError = callback; }
    void setOnStateChange(OnStateChangeCallback callback) { m_onStateChange = callback; }
    
    // State
    EngineState getState() const { return m_state; }
    void setState(EngineState state);
    
    // Config
    void setVADThreshold(float threshold) { m_vadThreshold = threshold; }
    void setSilenceTimeout(int ms) { m_silenceTimeoutMs = ms; }
    void setMaxListeningTime(int ms) { m_maxListeningMs = ms; }
    
private:
    KateEngine() = default;
    ~KateEngine() = default;
    KateEngine(const KateEngine&) = delete;
    KateEngine& operator=(const KateEngine&) = delete;
    
    void processAudioCallback(const int16_t* data, size_t samples);
    void onTranscription(const std::string& text, bool isFinal);
    void onIntentResult(const IntentResult& result);
    
    // State
    std::atomic<EngineState> m_state{EngineState::IDLE};
    std::atomic<bool> m_initialized{false};
    std::atomic<bool> m_isSpeaking{false};
    
    // Components
    std::unique_ptr<AudioPipeline> m_audioPipeline;
    std::unique_ptr<SpeechRecognizer> m_speechRecognizer;
    std::unique_ptr<IntentEngine> m_intentEngine;
    std::unique_ptr<TTSSynthesizer> m_ttsSynthesizer;
    std::unique_ptr<ConversationCache> m_cache;
    
    // Callbacks
    OnTranscriptionCallback m_onTranscription;
    OnResponseCallback m_onResponse;
    OnErrorCallback m_onError;
    OnStateChangeCallback m_onStateChange;
    
    // Config
    float m_vadThreshold = 0.5f;
    int m_silenceTimeoutMs = 3000;
    int m_maxListeningMs = 30000;
    
    // Threading
    std::mutex m_mutex;
    std::thread m_processingThread;
    std::atomic<bool> m_running{false};
    
    // Stats
    std::vector<ConversationRecord> m_conversationHistory;
    std::mutex m_historyMutex;
};

} // namespace kate

#endif // KATE_ENGINE_H
