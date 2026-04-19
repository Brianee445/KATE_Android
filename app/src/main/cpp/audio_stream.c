#include "audio_stream.h"
#include "wake_word.h"
#include <aaudio/AAudio.h>
#include <android/log.h>

#define LOG_TAG "KateAudio"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static AAudioStream *stream = NULL;
static int is_running = 0;

// Audio callback (called repeatedly)
static aaudio_data_callback_result_t audio_callback(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames
) {
    if (!is_running) return AAUDIO_CALLBACK_RESULT_STOP;

    float *data = (float *) audioData;

    // Send audio chunk to wake word detector
    process_audio_frame(data, numFrames);

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void start_audio_stream() {
    if (is_running) return;

    AAudioStreamBuilder *builder;
    AAudio_createStreamBuilder(&builder);

    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    AAudioStreamBuilder_setSampleRate(builder, 16000);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setDataCallback(builder, audio_callback, NULL);

    AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStream_requestStart(stream);

    is_running = 1;
    LOGD("Audio stream started");

    AAudioStreamBuilder_delete(builder);
}

void stop_audio_stream() {
    if (!is_running) return;

    AAudioStream_requestStop(stream);
    AAudioStream_close(stream);

    stream = NULL;
    is_running = 0;

    LOGD("Audio stream stopped");
}