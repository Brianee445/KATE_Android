#include "decision_engine.h"
#include "habit_engine.h"
#include <string.h>

// Simple priority-based fusion (fast + expandable)

const char* decide_intent(
    const char* rule_intent,
    const char* ml_intent,
    const char* entity,
    const char* emotion
) {

    // 1. RULE PRIORITY
    if (strcmp(rule_intent, "CREATE_REMINDER") == 0) {
        return rule_intent;
    }

    // 2. ML FALLBACK
    const char* base_intent = rule_intent;

    if (strcmp(rule_intent, "UNKNOWN") == 0 &&
        strcmp(ml_intent, "UNKNOWN") != 0) {
        base_intent = ml_intent;
    }

    // 3. PERSONALIZATION (ENTITY INJECTION)
    if (strlen(entity) == 0) {

        const char* preferred = get_preferred_entity(base_intent);

        if (preferred != NULL) {
            return base_intent; // entity will be injected later
        }
    }

    return base_intent;
}