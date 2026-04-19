#ifndef KATE_CORE_H
#define KATE_CORE_H

#include <stdbool.h>
#include <stdint.h>

#define KATE_VERSION  "1.0.0"
#define KATE_SAMPLE_RATE  16000
#define KATE_FRAME_SIZE   512

// ── Event types ──────────────────────────────────────────────
#define EVT_WAKE_WORD   "WAKE_WORD"
#define EVT_INTENT      "INTENT"
#define EVT_HABIT       "HABIT_UPDATE"
#define EVT_SUGGESTION  "SUGGESTION"
#define EVT_ERROR       "ERROR"

// ── Shared event emitter (implemented in bridge.c) ───────────
void send_event(const char* type, const char* payload);

#endif // KATE_CORE_H
