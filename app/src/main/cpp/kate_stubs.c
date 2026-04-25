#include "kate_core.h"
#include "audio/audio_stream.h"
#include "audio/wake_word.h"
#include "audio/vad.h"
#include "brain/habit_engine.h"
#include "brain/emotion_engine.h"
#include "brain/situation_engine.h"
#include "brain/decision_engine.h"
#include "nlp/intent_parser.h"
#include "nlp/tokenizer.h"
#include <string.h>

// ── Safe fallbacks if any engine is incomplete ───────────────

__attribute__((weak)) void start_audio_stream(void) {}
__attribute__((weak)) void stop_audio_stream(void)  {}

__attribute__((weak)) void load_habit(const char* i, const char* e, int c) {
    (void)i; (void)e; (void)c;
}
__attribute__((weak)) void record_habit(const char* i, const char* e) {
    (void)i; (void)e;
}
__attribute__((weak)) const char* get_preferred_entity(const char* i) {
    (void)i; return NULL;
}
__attribute__((weak)) const char* get_suggestion(int h) {
    (void)h; return NULL;
}

__attribute__((weak)) EmotionState detect_emotion(const char* t, float e) {
    (void)t; (void)e; return 0;
}
__attribute__((weak)) const char* emotion_to_string(EmotionState s) {
    (void)s; return "NEUTRAL";
}

__attribute__((weak)) const char* decide_intent(
    const char* a, const char* b, const char* c, const char* d) {
    (void)b; (void)c; (void)d; return a;
}

__attribute__((weak)) const char* evaluate_situation(
    const char* intent, const char* entity) {
    (void)entity; return intent;
}

__attribute__((weak)) void parse_intent(const char* t, IntentResult* r) {
    (void)t;
    if (r) {
        strncpy(r->intent, "UNKNOWN", sizeof(r->intent) - 1);
        strncpy(r->entity, "", sizeof(r->entity) - 1);
    }
}

__attribute__((weak)) void tokenize(const char* t, TokenList* out) {
    (void)t;
    if (out) out->count = 0;
}
