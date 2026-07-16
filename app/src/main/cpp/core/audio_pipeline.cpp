// app/src/main/cpp/core/audio_pipeline.cpp

#include "audio_pipeline.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "AudioPipeline"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace kate {

AudioPipeline::AudioPipeline() = default;

AudioPipeline::~AudioPipeline() {
    stop();
}

bool AudioPipeline::initialize(int sampleRate, int channels) {
    m_sampleRate = sampleRate;
    m_channels = channels;
    m_bufferSize = (sampleRate * m_bufferMs) / 1000;
    
    m_initialized = true;
    LOGD("Audio pipeline initialized: %d Hz, %d channels, buffer %d samples", 
         sampleRate, channels, m_bufferSize);
    return true;
}

bool AudioPipeline::start() {
    if (!m_initialized) {
        LOGE("Pipeline not initialized");
        return false;
    }
    
    if (m_running) {
        return true;
    }
    
    m_running = true;
    m_audioThread = std::thread(&AudioPipeline::audioThreadLoop, this);
    
    LOGD("Audio pipeline started");
    return true;
}

void AudioPipeline::stop() {
    if (!m_running) return;
    
    m_running = false;
    if (m_audioThread.joinable()) {
        m_audioThread.join();
    }
    
    // Clear buffer
    std::lock_guard<std::mutex> lock(m_queueMutex);
    while (!m_bufferQueue.empty()) {
        m_bufferQueue.pop();
    }
    
    LOGD("Audio pipeline stopped");
}

void AudioPipeline::audioThreadLoop() {
    // This thread would normally read from AudioRecord
    // For now, it's a placeholder - actual audio comes from Java
    
    std::vector<int16_t> buffer(m_bufferSize);
    
    while (m_running) {
        // Wait for audio data to be fed
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
        
        // Process any queued data
        std::lock_guard<std::mutex> lock(m_queueMutex);
        while (!m_bufferQueue.empty() && m_running) {
            auto data = std::move(m_bufferQueue.front());
            m_bufferQueue.pop();
            processBuffer(data.data(), data.size());
        }
    }
}

void AudioPipeline::feedAudio(const int16_t* data, size_t samples) {
    if (!m_running || !m_callback) return;
    
    std::lock_guard<std::mutex> lock(m_queueMutex);
    std::vector<int16_t> buffer(data, data + samples);
    m_bufferQueue.push(std::move(buffer));
}

void AudioPipeline::processBuffer(const int16_t* data, size_t samples) {
    if (m_callback) {
        m_callback(data, samples);
    }
}

} // namespace kate
