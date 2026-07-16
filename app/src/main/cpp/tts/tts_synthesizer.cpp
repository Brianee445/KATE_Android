// app/src/main/cpp/tts/tts_synthesizer.cpp

#include "tts_synthesizer.h"
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <sstream>

#define LOG_TAG "TTSSynthesizer"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace kate {

// Simple phoneme mapping (extremely simplified)
static const std::unordered_map<std::string, std::string> PHONEME_MAP = {
    {"a", "ah"}, {"e", "eh"}, {"i", "ee"}, {"o", "oh"}, {"u", "uh"},
    {"b", "b"}, {"c", "k"}, {"d", "d"}, {"f", "f"}, {"g", "g"},
    {"h", "h"}, {"j", "j"}, {"k", "k"}, {"l", "l"}, {"m", "m"},
    {"n", "n"}, {"p", "p"}, {"q", "k"}, {"r", "r"}, {"s", "s"},
    {"t", "t"}, {"v", "v"}, {"w", "w"}, {"x", "ks"}, {"y", "y"}, {"z", "z"},
    {"th", "th"}, {"sh", "sh"}, {"ch", "ch"}, {"ng", "ng"},
};

TTSSynthesizer::TTSSynthesizer() {
    precomputeCommonResponses();
}

TTSSynthesizer::~TTSSynthesizer() {
    shutdown();
}

bool TTSSynthesizer::initialize() {
    if (m_initialized) {
        return true;
    }
    
    m_initialized = true;
    LOGI("TTSSynthesizer initialized");
    return true;
}

void TTSSynthesizer::shutdown() {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_cache.clear();
    m_initialized = false;
    LOGI("TTSSynthesizer shutdown");
}

std::string TTSSynthesizer::synthesize(const std::string& text, float tone) {
    if (!m_initialized) {
        return text;
    }
    
    // Check if we have a precomputed response
    std::string precomputed = getPrecomputedResponse(text);
    if (!precomputed.empty()) {
        return precomputed;
    }
    
    // For now, return text (the Android TTS engine will handle actual speech)
    // In a real implementation, this would return the path to a generated audio file
    return text;
}

std::vector<int16_t> TTSSynthesizer::synthesizeToAudio(const std::string& text, float tone) {
    std::vector<int16_t> audio;
    
    if (!m_initialized || text.empty()) {
        return audio;
    }
    
    // Check cache
    std::string cacheKey = text + "_" + std::to_string(tone);
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        auto it = m_cache.find(cacheKey);
        if (it != m_cache.end()) {
            return it->second;
        }
    }
    
    // Generate audio
    audio = generateSimpleTTS(text, tone);
    
    // Cache
    if (!audio.empty()) {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_cache[cacheKey] = audio;
    }
    
    return audio;
}

std::vector<int16_t> TTSSynthesizer::generateSimpleTTS(const std::string& text, float tone) {
    std::vector<int16_t> audio;
    
    // This is a very simplified TTS implementation
    // In production, you'd use a proper TTS engine like:
    // - Coqui TTS
    // - Google TTS (via JNI)
    // - eSpeak
    // - Piper TTS
    
    // For now, generate a simple sine wave with varying frequency
    // based on the text (this is just a placeholder)
    
    if (text.empty()) {
        return audio;
    }
    
    int durationMs = std::max(500, static_cast<int>(text.length() * 200));
    int sampleCount = (m_sampleRate * durationMs) / 1000;
    
    audio.reserve(sampleCount);
    
    float baseFrequency = 220.0f; // A3
    float frequency = baseFrequency * (0.8f + tone * 0.4f);
    
    // Use text hash to vary frequency
    size_t hash = std::hash<std::string>{}(text);
    frequency += (hash % 50) * 2.0f;
    
    for (int i = 0; i < sampleCount; i++) {
        float t = static_cast<float>(i) / m_sampleRate;
        float sample = 0.3f * sinf(2.0f * 3.14159f * frequency * t);
        // Add some harmonics for richer sound
        sample += 0.1f * sinf(2.0f * 3.14159f * frequency * 2.0f * t);
        sample += 0.05f * sinf(2.0f * 3.14159f * frequency * 3.0f * t);
        
        // Envelope (attack/decay)
        float envelope = 1.0f;
        if (i < 100) {
            envelope = static_cast<float>(i) / 100.0f;
        } else if (i > sampleCount - 200) {
            envelope = static_cast<float>(sampleCount - i) / 200.0f;
        }
        sample *= envelope;
        
        // Convert to int16
        int16_t value = static_cast<int16_t>(sample * 32767.0f);
        audio.push_back(value);
    }
    
    return audio;
}

std::vector<std::string> TTSSynthesizer::textToPhonemes(const std::string& text) {
    std::vector<std::string> phonemes;
    std::string lower = text;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
    
    // Simple character-to-phoneme mapping
    for (size_t i = 0; i < lower.length(); i++) {
        char c = lower[i];
        std::string key(1, c);
        
        // Check for digraphs
        if (i + 1 < lower.length()) {
            std::string digraph = lower.substr(i, 2);
            if (digraph == "th" || digraph == "sh" || digraph == "ch" || digraph == "ng") {
                auto it = PHONEME_MAP.find(digraph);
                if (it != PHONEME_MAP.end()) {
                    phonemes.push_back(it->second);
                    i++; // Skip next char
                    continue;
                }
            }
        }
        
        auto it = PHONEME_MAP.find(key);
        if (it != PHONEME_MAP.end()) {
            phonemes.push_back(it->second);
        } else {
            // Unknown phoneme
            phonemes.push_back("_");
        }
    }
    
    return phonemes;
}

std::vector<int16_t> TTSSynthesizer::phonemesToAudio(const std::vector<std::string>& phonemes, float tone) {
    std::vector<int16_t> audio;
    
    // This would map phonemes to audio samples
    // For now, return empty (placeholder)
    return audio;
}

void TTSSynthesizer::precomputeCommonResponses() {
    m_precomputedResponses = {
        {"Hello", "Hello! How can I help you?"},
        {"Hi", "Hi there!"},
        {"Hey", "Hey! What can I do for you?"},
        {"Thank you", "You're welcome!"},
        {"Thanks", "Anytime!"},
        {"Goodbye", "Goodbye! Have a great day!"},
        {"Bye", "Bye! See you later!"},
        {"What can you do", "I can open apps, type text, search, and more!"},
        {"Help", "I can open apps, type text, search, and more. Just ask!"},
        {"Who are you", "I'm Kate, your AI voice assistant!"},
        {"What is your name", "My name is Kate!"},
    };
}

std::string TTSSynthesizer::getPrecomputedResponse(const std::string& text) {
    std::string lower = text;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
    
    for (const auto& pair : m_precomputedResponses) {
        std::string key = pair.first;
        std::transform(key.begin(), key.end(), key.begin(), ::tolower);
        if (lower.find(key) != std::string::npos) {
            return pair.second;
        }
    }
    
    return "";
}

} // namespace kate
