#include "situation_engine.h"
#include <time.h>
#include <string.h>

static int get_hour() {
    time_t now = time(NULL);
    struct tm *t = localtime(&now);
    return t->tm_hour;
}

const char* evaluate_situation(const char* intent, const char* entity) {

    static char response[128];
    int hour = get_hour();

    // ===== TIME CONTEXT =====
    if (strcmp(intent, "PLAY_MEDIA") == 0) {

        if (hour >= 6 && hour < 12) {
            strcpy(response, "PLAY_MORNING");
            return response;
        }

        if (hour >= 20 || hour < 6) {
            strcpy(response, "PLAY_RELAX");
            return response;
        }
    }

    // Default → return original intent
    strcpy(response, intent);
    return response;
}