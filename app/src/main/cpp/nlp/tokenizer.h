#ifndef TOKENIZER_H
#define TOKENIZER_H

#define MAX_TOKENS 20
#define MAX_TOKEN_LEN 32

typedef struct {
    char tokens[MAX_TOKENS][MAX_TOKEN_LEN];
    int count;
} TokenList;

void tokenize(const char *text, TokenList *out);

#endif