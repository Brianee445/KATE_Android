#include "vad.h"

#define VAD_THRESHOLD 0.015f

int is_voice_active(float *data, int32_t frames) {
    float energy = 0.0f;

    for (int i = 0; i < frames; i++) {
        energy += data[i] * data[i];
    }

    energy /= frames;

    return energy > VAD_THRESHOLD;
}