#include "kate_core.h"
#include <time.h>

const char* get_suggestion(int hour) {
    if (hour >= 6 && hour < 9)
        return "Good morning! Time for your morning routine.";
    if (hour >= 12 && hour < 14)
        return "Lunch time! Don't forget to eat.";
    if (hour >= 17 && hour < 20)
        return "Evening! Time to wind down.";
    if (hour >= 22 || hour < 6)
        return "It's late. Consider going to bed.";
    return NULL;
}
