#ifndef HABIT_ENGINE_H
#define HABIT_ENGINE_H

void record_habit(const char* intent, const char* entity);
const char* get_preferred_entity(const char* intent);
const char* get_suggestion(int hour);

// NEW
void load_habit(const char* intent, const char* entity, int count);

#endif
