// app/src/main/cpp/stt/speech_recognizer.h

#ifndef SPEECH_RECOGNIZER_H
#define SPEECH_RECOGNIZER_H

#include <string>
#include <functional>
#include <memory>
#include <atomic>
#include <mutex>
#include <vector>

#include "vosk_wrapper.h"

namespace kate {

class SpeechRecognizer {
public:
    using Callback = std::function<void(const std::string&, bool)>;
    
    SpeechRecognizer();
    ~SpeechRecognizer();
    
    bool initialize(const std::string& modelPath);
    void shutdown();
    bool isInitialized() const { return m_initialized; }
    
    bool start();
    void stop();
    bool isRunning() const { return m_running; }
    
    void feedAudio(const int16_t* data, size_t samples);
    void reset();
    
    void setCallback(Callback callback) { m_callback = callback; }
    void setSampleRate(int rate) { m_sampleRate = rate; }
    
    std::string getCurrentTranscription() const;
    float getConfidence() const { return m_confidence; }
    
private:
    void processResult(const std::string& result, bool isFinal);
    void parseVoskResult(const std::string& json, std::string& text, float& confidence);
    
    std::unique_ptr<VoskWrapper> m_vosk;
    
    std::atomic<bool> m_initialized{false};
    std::atomic<bool> m_running{false};
    
    int m_sampleRate = 16000;
    std::string m_currentText;
    float m_confidence = 0.0f;
    
    Callback m_callback;
    mutable std::mutex m_mutex;
};

} // namespace kate

#endif // SPEECH_RECOGNIZER_H
