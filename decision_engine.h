#ifndef DECISION_ENGINE_H
#define DECISION_ENGINE_H

const char* decide_intent(
    const char* rule_intent,
    const char* ml_intent,
    const char* entity,
    const char* emotion
);

#endif