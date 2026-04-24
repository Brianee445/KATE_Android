#ifndef WAKE_WORD_H
#define WAKE_WORD_H

#include <stdint.h>

void process_audio_frame(float *data, int32_t numFrames);
void notify_wake_word_detected(void);

#endif
