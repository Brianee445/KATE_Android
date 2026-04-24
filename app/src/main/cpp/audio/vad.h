#ifndef VAD_H
#define VAD_H

#include <stdint.h>

int is_voice_active(float *data, int32_t frames);

#endif
