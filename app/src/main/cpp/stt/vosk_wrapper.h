// app/src/main/cpp/stt/vosk_wrapper.h

#ifndef VOSK_WRAPPER_H
#define VOSK_WRAPPER_H

#include <string>
#include <functional>
#include <memory>
#include <atomic>

// Forward declare Vosk types
struct VoskModel;
struct VoskRecognizer;

namespace kate {

class VoskWrapper {
public:
    using TranscriptionCallback = std::function<void(const std::string&, bool)>;
    
    VoskWrapper();
    ~VoskWrapper();
    
    bool initialize(const std::string& modelPath);
    void shutdown();
    bool isInitialized() const { return m_initialized; }
    
    bool startListening();
    void stopListening();
    bool isListening() const { return m_listening; }
    
    void feedAudio(const int16_t* data, size_t samples);
    void reset();
    
    void setCallback(TranscriptionCallback callback) { m_callback = callback; }
    void setSampleRate(int rate) { m_sampleRate = rate; }
    
    // Get partial result
    std::string getPartialResult() const;
    
private:
    void processResult(bool isFinal);
    
    // Vosk objects
    VoskModel* m_model = nullptr;
    VoskRecognizer* m_recognizer = nullptr;
    
    // State
    std::atomic<bool> m_initialized{false};
    std::atomic<bool> m_listening{false};
    
    // Config
    int m_sampleRate = 16000;
    std::string m_modelPath;
    
    // Callbacks
    TranscriptionCallback m_callback;
    
    // Thread safety
    mutable std::mutex m_mutex;
};

} // namespace kate

#endif // VOSK_WRAPPER_H
