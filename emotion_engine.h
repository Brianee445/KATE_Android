#ifndef EMOTION_ENGINE_H
#define EMOTION_ENGINE_H

typedef enum {
    EMOTION_CALM,
    EMOTION_NEUTRAL,
    EMOTION_STRESSED,
    EMOTION_URGENT
} EmotionState;

EmotionState detect_emotion(const char* text, float audio_energy);

const char* emotion_to_string(EmotionState state);

#endif