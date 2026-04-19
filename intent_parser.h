#ifndef INTENT_PARSER_H
#define INTENT_PARSER_H

#include "tokenizer.h"

typedef struct {
    char intent[32];
    char entity[64];
} IntentResult;

void parse_intent(const char *text, IntentResult *result);

#endif