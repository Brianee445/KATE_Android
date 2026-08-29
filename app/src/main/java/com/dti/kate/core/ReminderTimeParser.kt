package com.dti.kate.core

import java.util.Calendar

/**
 * Parses "remind me to X at/in Y" into a reminder body + trigger
 * timestamp. Deliberately scoped to the two phrasings people actually say
 * out loud - a relative duration ("in 20 minutes") or a clock time ("at
 * 5pm", "at 9") - not full calendar-date NLP ("next Tuesday", "in three
 * weeks"). That's a real gap, not an oversight: getting date parsing wrong
 * silently sets the wrong reminder, which is worse than the app admitting
 * it didn't understand (see KateAction.ReminderTimeUnclear).
 */
object ReminderTimeParser {

    data class Parsed(val text: String, val triggerAtMillis: Long)

    private val durationRegex = Regex("""\bin\s+(\d+)\s*(minute|min|hour|hr)s?\b""")
    private val clockTimeRegex = Regex("""\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""")

    /** @param lower already-lowercased transcript, expected to start with "remind me" (classify() checks that before calling this). */
    fun parse(lower: String, now: Long = System.currentTimeMillis()): Parsed? {
        var rest = lower.removePrefix("remind me").trim()
        if (rest.startsWith("to ")) rest = rest.removePrefix("to ").trim()

        val hasTomorrow = Regex("""\btomorrow\b""").containsMatchIn(rest)

        val durationMatch = durationRegex.find(rest)
        val clockMatch = clockTimeRegex.find(rest)

        val triggerAtMillis: Long
        val strippedMatchText: String

        when {
            durationMatch != null -> {
                val amount = durationMatch.groupValues[1].toIntOrNull() ?: return null
                val unit = durationMatch.groupValues[2]
                val millis = if (unit.startsWith("h")) amount * 3_600_000L else amount * 60_000L
                if (millis <= 0L) return null
                triggerAtMillis = now + millis
                strippedMatchText = durationMatch.value
            }
            clockMatch != null -> {
                val hourRaw = clockMatch.groupValues[1].toIntOrNull()?.takeIf { it in 0..12 } ?: return null
                val minute = clockMatch.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: 0
                val meridiem = clockMatch.groupValues[3]

                fun candidateMillis(isPm: Boolean): Long {
                    val hour24 = when {
                        isPm && hourRaw != 12 -> hourRaw + 12
                        !isPm && hourRaw == 12 -> 0
                        else -> hourRaw
                    }
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = now
                        set(Calendar.HOUR_OF_DAY, hour24)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (hasTomorrow) add(Calendar.DAY_OF_YEAR, 1)
                        else if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
                    }
                    return cal.timeInMillis
                }

                triggerAtMillis = when (meridiem) {
                    "am" -> candidateMillis(isPm = false)
                    "pm" -> candidateMillis(isPm = true)
                    // No am/pm spoken - genuinely ambiguous ("at 5" could be
                    // 5am or 5pm), so pick whichever interpretation is
                    // soonest from now rather than guessing one and being
                    // wrong half the time.
                    else -> minOf(candidateMillis(isPm = false), candidateMillis(isPm = true))
                }
                strippedMatchText = clockMatch.value
            }
            else -> return null
        }

        var text = rest.replace(strippedMatchText, " ")
            .replace(Regex("""\btomorrow\b"""), " ")
            .replace(Regex("""^\s*(to|that|i)\s+"""), "")
            .trim()
            .trim('.', ',', ' ')

        if (text.isBlank()) text = "your reminder"

        return Parsed(text, triggerAtMillis)
    }
}
