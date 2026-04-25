#include "audio_stream.h"
#include <android/log.h>

#define LOG_TAG "KateAudio"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// AAudio temporarily replaced with stub
// to isolate Android 14 Go installation issue

void start_audio_stream() {
    LOGD("Audio stream stub — AAudio disabled for compatibility test");
}

void stop_audio_stream() {
    LOGD("Audio stream stopped (stub)");
}
