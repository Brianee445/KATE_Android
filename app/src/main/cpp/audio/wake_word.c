#include "wake_word.h"
#include "vad.h"
#include "../kate_core.h"

#define TRIGGER_FRAMES 25

static int speech_frames = 0;
static int triggered = 0;

void process_audio_frame(float *data, int32_t numFrames) {

    if (!is_voice_active(data, numFrames)) {
        speech_frames = 0;
        triggered = 0;
        return;
    }

    speech_frames++;

    if (speech_frames > TRIGGER_FRAMES && !triggered) {
        triggered = 1;
        notify_wake_word_detected();
    }
}