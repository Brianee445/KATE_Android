#include "intent_parser.h"
#include "tokenizer.h"
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

extern char app_list[][128];
extern int  app_count;

static int contains(TokenList *tokens, const char *word) {
    if (!tokens || !word) return 0;
    for (int i = 0; i < tokens->count; i++) {
        if (strcmp(tokens->tokens[i], word) == 0) return 1;
    }
    return 0;
}

static void extract_app_name(const char *entry, char *name_out, size_t name_out_len) {
    if (!entry || !name_out || name_out_len == 0) return;

    size_t i = 0;
    while (entry[i] != '|' && entry[i] != '\0' && i < name_out_len - 1) {
        name_out[i] = entry[i];
        i++;
    }
    name_out[i] = '\0';
}

static const char* extract_package(const char *entry) {
    if (!entry) return NULL;
    const char *pipe = strchr(entry, '|');
    return (pipe && *(pipe + 1) != '\0') ? pipe + 1 : NULL;
}

static const char* find_app(TokenList *tokens) {
    if (!tokens || app_count <= 0) return NULL;

    char app_name[64];
    for (int i = 0; i < tokens->count; i++) {
        for (int j = 0; j < app_count; j++) {
            extract_app_name(app_list[j], app_name, sizeof(app_name));
            if (strcmp(tokens->tokens[i], app_name) == 0 ||
                strstr(app_name, tokens->tokens[i]) != NULL) {
                const char *pkg = extract_package(app_list[j]);
                if (pkg) return pkg;
            }
        }
    }
    return NULL;
}

static int extract_delay_ms(TokenList *tokens) {
    if (!tokens) return -1;

    for (int i = 0; i < tokens->count; i++) {
        int value = atoi(tokens->tokens[i]);
        if (value > 0 && i + 1 < tokens->count) {
            if (strcmp(tokens->tokens[i+1], "minute")  == 0 ||
                strcmp(tokens->tokens[i+1], "minutes") == 0)
                return value * 60 * 1000;
            if (strcmp(tokens->tokens[i+1], "hour")    == 0 ||
                strcmp(tokens->tokens[i+1], "hours")   == 0)
                return value * 3600 * 1000;
        }
    }
    return -1;
}

static void build_task_text(TokenList *tokens, char *out, int max_len) {
    if (!tokens || !out || max_len <= 0) return;

    out[0] = '\0';
    int written = 0;

    for (int i = 0; i < tokens->count; i++) {
        const char *tok = tokens->tokens[i];

        if (strcmp(tok, "remind") == 0 ||
            strcmp(tok, "me")     == 0 ||
            strcmp(tok, "to")     == 0 ||
            strcmp(tok, "in")     == 0) continue;

        if (strcmp(tok, "minute")  == 0 ||
            strcmp(tok, "minutes") == 0 ||
            strcmp(tok, "hour")    == 0 ||
            strcmp(tok, "hours")   == 0) break;

        int tok_len = (int)strlen(tok);
        if (written + tok_len + 2 < max_len) {
            if (written > 0) {
                out[written++] = ' ';
                out[written] = '\0';
            }
            strcat(out, tok);
            written += tok_len;
        } else {
            break;
        }
    }

    // Trim trailing space if any
    if (written > 0 && out[written - 1] == ' ') {
        out[written - 1] = '\0';
    }
}

void parse_intent(const char *text, IntentResult *result) {
    if (!text || !result) return;

    TokenList tokens;
    tokens.count = 0;
    tokenize(text, &tokens);

    strcpy(result->intent, "UNKNOWN");
    strcpy(result->entity, "");

    // REMINDER
    if (contains(&tokens, "remind") || contains(&tokens, "reminder")) {
        int delay = extract_delay_ms(&tokens);
        if (delay > 0) {
            char task[64];
            build_task_text(&tokens, task, sizeof(task));
            strcpy(result->intent, "REMINDER");
            snprintf(result->entity, sizeof(result->entity), "%s|%d", task, delay);
            return;
        }
    }

    // OPEN APP
    if (contains(&tokens, "open") || contains(&tokens, "launch")) {
        const char *pkg = find_app(&tokens);
        if (pkg) {
            strcpy(result->intent, "OPEN_APP");
            strncpy(result->entity, pkg, sizeof(result->entity) - 1);
            result->entity[sizeof(result->entity) - 1] = '\0';
            return;
        }
    }

    // MEDIA
    if (contains(&tokens, "play")   || contains(&tokens, "pause") ||
        contains(&tokens, "next")   || contains(&tokens, "resume")) {
        strcpy(result->intent, "MEDIA_CONTROL");
        return;
    }

    // COMMUNICATION
    if (contains(&tokens, "call")    || contains(&tokens, "dial") ||
        contains(&tokens, "text")    || contains(&tokens, "message") ||
        contains(&tokens, "sms")) {
        strcpy(result->intent, "COMMUNICATION");
        return;
    }

    // SYSTEM
    if (contains(&tokens, "volume")      || contains(&tokens, "brightness") ||
        contains(&tokens, "wifi")        || contains(&tokens, "bluetooth")   ||
        contains(&tokens, "torch")       || contains(&tokens, "flashlight")) {
        strcpy(result->intent, "SYSTEM_CONTROL");
        return;
    }
}
