// app/src/main/cpp/tts/tts_synthesizer.h

#ifndef TTS_SYNTHESIZER_H
#define TTS_SYNTHESIZER_H

#include <string>
#include <vector>
#include <memory>
#include <atomic>
#include <mutex>
#include <unordered_map>

namespace kate {

class TTSSynthesizer {
public:
    TTSSynthesizer();
    ~TTSSynthesizer();
    
    bool initialize();
    void shutdown();
    bool isInitialized() const { return m_initialized; }
    
    std::string synthesize(const std::string& text, float tone = 0.5f);
    
    // Audio generation (returns PCM data as string)
    std::vector<int16_t> synthesizeToAudio(const std::string& text, float tone = 0.5f);
    
    // Voice personality
    void setPitch(float pitch) { m_pitch = pitch; }
    void setSpeed(float speed) { m_speed = speed; }
    void setVoiceType(const std::string& voice) { m_voiceType = voice; }
    
    // Precomputed responses
    void precomputeCommonResponses();
    std::string getPrecomputedResponse(const std::string& text);
    
private:
    // Simple concatenative synthesis (fallback)
    std::vector<int16_t> generateSimpleTTS(const std::string& text, float tone);
    
    // Phoneme mapping (simplified)
    std::vector<std::string> textToPhonemes(const std::string& text);
    std::vector<int16_t> phonemesToAudio(const std::vector<std::string>& phonemes, float tone);
    
    // Precomputed cache
    std::unordered_map<std::string, std::vector<int16_t>> m_cache;
    std::unordered_map<std::string, std::string> m_precomputedResponses;
    
    std::atomic<bool> m_initialized{false};
    
    float m_pitch = 1.0f;
    float m_speed = 1.0f;
    std::string m_voiceType = "default";
    
    // Sample rate
    int m_sampleRate = 16000;
    
    mutable std::mutex m_mutex;
};

} // namespace kate

#endif // TTS_SYNTHESIZER_H
