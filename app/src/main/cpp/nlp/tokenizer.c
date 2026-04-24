#include <ctype.h>
#include <string.h>

void tokenize(const char *text, TokenList *out) {
    if (!text || !out) return;

    out->count = 0;
    int j = 0;
    char buffer[MAX_TOKEN_LEN];

    while (*text && out->count < MAX_TOKENS) {
        if (isalnum(*text)) {
            if (j < MAX_TOKEN_LEN - 1) {
                buffer[j++] = tolower((unsigned char)*text);
            }
        } else {
            if (j > 0) {
                buffer[j] = '\0';
                strncpy(out->tokens[out->count], buffer, MAX_TOKEN_LEN - 1);
                out->tokens[out->count][MAX_TOKEN_LEN - 1] = '\0';
                out->count++;
                j = 0;
            }
        }
        text++;
    }

    if (j > 0 && out->count < MAX_TOKENS) {
        if (j >= MAX_TOKEN_LEN) {
            j = MAX_TOKEN_LEN - 1;
        }
        buffer[j] = '\0';
        strncpy(out->tokens[out->count], buffer, MAX_TOKEN_LEN - 1);
        out->tokens[out->count][MAX_TOKEN_LEN - 1] = '\0';
        out->count++;
    }
}
