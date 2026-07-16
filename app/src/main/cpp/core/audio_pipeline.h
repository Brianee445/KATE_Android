// app/src/main/cpp/core/audio_pipeline.h

#ifndef AUDIO_PIPELINE_H
#define AUDIO_PIPELINE_H

#include <atomic>
#include <thread>
#include <functional>
#include <queue>
#include <mutex>
#include <cstdint>

namespace kate {

class AudioPipeline {
public:
    using AudioCallback = std::function<void(const int16_t*, size_t)>;
    
    AudioPipeline();
    ~AudioPipeline();
    
    bool initialize(int sampleRate, int channels);
    bool start();
    void stop();
    bool isRunning() const { return m_running; }
    
    void setCallback(AudioCallback callback) { m_callback = callback; }
    void setBufferSizeMs(int ms) { m_bufferMs = ms; }
    
    // For testing or fallback
    void feedAudio(const int16_t* data, size_t samples);
    
private:
    void audioThreadLoop();
    void processBuffer(const int16_t* data, size_t samples);
    
    // Audio parameters
    int m_sampleRate = 16000;
    int m_channels = 1;
    int m_bufferMs = 100;
    int m_bufferSize = 1600; // 100ms @ 16kHz
    
    // State
    std::atomic<bool> m_running{false};
    std::atomic<bool> m_initialized{false};
    
    // Callback
    AudioCallback m_callback;
    
    // Threading
    std::thread m_audioThread;
    std::mutex m_mutex;
    
    // Buffer (Android AudioRecord will feed directly)
    std::queue<std::vector<int16_t>> m_bufferQueue;
    std::mutex m_queueMutex;
};

} // namespace kate

#endif // AUDIO_PIPELINE_H
