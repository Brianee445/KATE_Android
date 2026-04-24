#include <stdio.h>
#include "../kate_core.h"
#include "habit_engine.h"
#include <string.h>

#define MAX_RECORDS 100

typedef struct {
    char intent[32];
    char entity[64];
    int count;
} HabitRecord;

static HabitRecord records[MAX_RECORDS];
static int record_count = 0;

// ================= RECORD =================
void record_habit(const char* intent, const char* entity) {

    if (strlen(entity) == 0) return;

    // Update count first
    for (int i = 0; i < record_count; i++) {
        if (strcmp(records[i].intent, intent) == 0 &&
            strcmp(records[i].entity, entity) == 0) {

            records[i].count++;

            // Notify persistence layer
            char habit_payload[128];
            snprintf(habit_payload, sizeof(habit_payload),
                     "%s|%s|%d",
                     intent,
                     entity,
                     records[i].count);
            send_event("HABIT_UPDATE", habit_payload);

            return;
        }
    }

    if (record_count < MAX_RECORDS) {
        strcpy(records[record_count].intent, intent);
        strcpy(records[record_count].entity, entity);
        records[record_count].count = 1;
        record_count++;

        // Notify persistence layer
        char habit_payload[128];
        snprintf(habit_payload, sizeof(habit_payload),
                 "%s|%s|1",
                 intent,
                 entity);
        send_event("HABIT_UPDATE", habit_payload);
    }
}
void load_habit(const char* intent, const char* entity, int count) {

    for (int i = 0; i < record_count; i++) {
        if (strcmp(records[i].intent, intent) == 0 &&
            strcmp(records[i].entity, entity) == 0) {

            records[i].count = count;
            return;
        }
    }

    if (record_count < MAX_RECORDS) {
        strcpy(records[record_count].intent, intent);
        strcpy(records[record_count].entity, entity);
        records[record_count].count = count;
        record_count++;
    }
}

// ================= PREFERENCE =================

const char* get_preferred_entity(const char* intent) {

    int best_index = -1;
    int best_score = 0;

    for (int i = 0; i < record_count; i++) {
        if (strcmp(records[i].intent, intent) == 0) {

            if (records[i].count > best_score) {
                best_score = records[i].count;
                best_index = i;
            }
        }
    }

    if (best_index >= 0) {
        return records[best_index].entity;
    }

    return NULL;
}
