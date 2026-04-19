#include "emotion_engine.h"
#include <string.h>
#include <ctype.h>

// ---------------- AUDIO + TEXT HYBRID EMOTION ----------------

EmotionState detect_emotion(const char* text, float audio_energy) {

    // ===== AUDIO SIGNAL RULES =====
    if (audio_energy > 0.08f) {
        return EMOTION_STRESSED;
    }

    // ===== TEXT SIGNAL RULES =====
    int exclaim = 0;
    int upper_count = 0;

    for (int i = 0; text[i] != '\0'; i++) {
        if (text[i] == '!') exclaim++;
        if (isupper(text[i])) upper_count++;
    }

    if (exclaim > 1 || upper_count > 5) {
        return EMOTION_URGENT;
    }

    // Keyword-based stress detection
    if (strstr(text, "quick") ||
        strstr(text, "urgent") ||
        strstr(text, "asap") ||
        strstr(text, "now")) {
        return EMOTION_URGENT;
    }

    if (strstr(text, "tired") ||
        strstr(text, "exhausted")) {
        return EMOTION_CALM;
    }

    return EMOTION_NEUTRAL;
}

// Convert enum → string for Kotlin
const char* emotion_to_string(EmotionState state) {
    switch (state) {
        case EMOTION_CALM: return "CALM";
        case EMOTION_NEUTRAL: return "NEUTRAL";
        case EMOTION_STRESSED: return "STRESSED";
        case EMOTION_URGENT: return "URGENT";
        default: return "NEUTRAL";
    }
}