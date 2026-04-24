#include "tokenizer.h"
#include <ctype.h>
#include <string.h>

void tokenize(const char *text, TokenList *out) {
    out->count = 0;

    int j = 0;
    char buffer[MAX_TOKEN_LEN];

    while (*text && out->count < MAX_TOKENS) {

        if (isalnum(*text)) {
            buffer[j++] = tolower(*text);
        } else {
            if (j > 0) {
                buffer[j] = '\0';
                strcpy(out->tokens[out->count++], buffer);
                j = 0;
            }
        }

        text++;
    }

    if (j > 0 && out->count < MAX_TOKENS) {
        buffer[j] = '\0';
        strcpy(out->tokens[out->count++], buffer);
    }
