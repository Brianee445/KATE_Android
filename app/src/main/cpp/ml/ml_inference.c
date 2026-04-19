#include "ml_inference.h"
#include "tflite_runner.h"
#include <string.h>

static int initialized = 0;

void ml_init(const char* model_path) {
    if (!initialized) {
        tflite_init(model_path);
        initialized = 1;
    }
}

// Matches labels.json exactly:
// {"0":"COMMUNICATION","1":"MEDIA_CONTROL","2":"OPEN_APP","3":"REMINDER","4":"SYSTEM_CONTROL","5":"UNKNOWN"}
static const char* map_label(int index) {
    switch (index) {
        case 0: return "COMMUNICATION";
        case 1: return "MEDIA_CONTROL";
        case 2: return "OPEN_APP";
        case 3: return "REMINDER";
        case 4: return "SYSTEM_CONTROL";
        case 5: return "UNKNOWN";
        default: return "UNKNOWN";
    }
}

const char* ml_predict_intent(const char* text) {
    int idx = tflite_predict(text);
    if (idx < 0) return "UNKNOWN";
    return map_label(idx);
}
