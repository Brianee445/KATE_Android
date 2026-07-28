package com.dti.kate.core

import kotlin.random.Random

enum class KateTone { PROFESSIONAL, BALANCED, SASSY }

fun toneFromSlider(value: Float): KateTone = when {
    value < 0.34f -> KateTone.PROFESSIONAL
    value < 0.67f -> KateTone.BALANCED
    else -> KateTone.SASSY
}

sealed class KateAction {
    data class OpenApp(val appName: String) : KateAction()
    data class TypeText(val text: String) : KateAction()
    data class ToggleTorch(val turnOn: Boolean?) : KateAction()
    data class MakeCall(val number: String) : KateAction()
    data class ToggleBluetooth(val turnOn: Boolean?) : KateAction()
    data class ToggleWifi(val turnOn: Boolean?) : KateAction()
    data class SetVolume(val level: Int?, val increase: Boolean?) : KateAction()
    object Help : KateAction()
    object Weather : KateAction()
    data class WebSearch(val query: String, val openBrowser: Boolean) : KateAction()
    object Unknown : KateAction()
}

data class KateReply(val action: KateAction, val speech: String)

/**
 * Rule-based intent classifier + tone-varied response generator.
 *
 * This intentionally lives in Kotlin rather than the native C++ engine -
 * it needs live network calls (weather, search) and Android system access
 * that the native engine doesn't have, and keeping it in Kotlin means fast
 * iteration without native rebuilds. The native engine (NativeBridge/
 * kate_engine) remains available for pure offline cached responses later.
 */
class KateResponseGenerator {

    private val recentPhraseIndices = mutableMapOf<String, Int>()

    /** Picks a phrase, avoiding immediate repeats of the last one used for this key. */
    private fun pick(key: String, phrases: List<String>): String {
        if (phrases.size == 1) return phrases[0]
        var index = Random.nextInt(phrases.size)
        val last = recentPhraseIndices[key]
        if (index == last) {
            index = (index + 1) % phrases.size
        }
        recentPhraseIndices[key] = index
        return phrases[index]
    }

    fun classify(text: String): KateAction {
        val lower = text.lowercase().trim()

        return when {
            lower.contains("weather") || lower.contains("temperature") || lower.contains("forecast") ->
                KateAction.Weather

            lower.contains("torch") || lower.contains("flashlight") || lower.contains("flash") ->
                KateAction.ToggleTorch(turnOn = detectOnOff(lower))

            lower.contains("bluetooth") ->
                KateAction.ToggleBluetooth(turnOn = detectOnOff(lower))

            lower.contains("wifi") || lower.contains("wi-fi") ->
                KateAction.ToggleWifi(turnOn = detectOnOff(lower))

            lower.contains("volume") -> {
                val number = Regex("""\d+""").find(lower)?.value?.toIntOrNull()
                val increase = when {
                    number != null -> null
                    lower.contains("up") || lower.contains("increase") -> true
                    lower.contains("down") || lower.contains("decrease") -> false
                    else -> null
                }
                KateAction.SetVolume(level = number, increase = increase)
            }

            lower.contains("call") || lower.contains("dial") -> {
                val number = Regex("""\d{10,14}""").find(lower)?.value
                if (number != null) KateAction.MakeCall(number) else KateAction.Unknown
            }

            lower.startsWith("open ") || lower.contains(" open ") ||
                lower.startsWith("launch ") || lower.startsWith("start ") -> {
                val app = extractAfterTrigger(lower, listOf("open ", "launch ", "start ", "run "))
                if (app.isNotBlank()) KateAction.OpenApp(app) else KateAction.Unknown
            }

            lower.startsWith("type ") || lower.startsWith("write ") -> {
                val content = extractAfterTrigger(lower, listOf("type ", "write ", "enter "))
                if (content.isNotBlank()) KateAction.TypeText(content) else KateAction.Unknown
            }

            lower.contains("help") || lower.contains("what can you do") ->
                KateAction.Help

            lower.startsWith("search ") || lower.startsWith("find ") ||
                lower.startsWith("look up ") || lower.startsWith("google ") ||
                lower.startsWith("who ") || lower.startsWith("what ") ||
                lower.startsWith("when ") || lower.startsWith("where ") ||
                lower.startsWith("how ") -> {
                val wantsBrowser = lower.contains("open") && (lower.contains("browser") || lower.contains("google"))
                val query = extractAfterTrigger(
                    lower, listOf("search ", "find ", "look up ", "google ")
                ).ifBlank { text }
                KateAction.WebSearch(query, openBrowser = wantsBrowser)
            }

            else -> KateAction.Unknown
        }
    }

    private fun detectOnOff(lower: String): Boolean? = when {
        lower.contains(" on") || lower.endsWith("on") -> true
        lower.contains(" off") || lower.endsWith("off") -> false
        else -> null
    }

    private fun extractAfterTrigger(lower: String, triggers: List<String>): String {
        for (trigger in triggers) {
            val idx = lower.indexOf(trigger)
            if (idx != -1) {
                return lower.substring(idx + trigger.length).trim()
            }
        }
        return ""
    }

    // ==================== RESPONSE PHRASING ====================

    fun speechForOpenApp(appName: String, tone: KateTone): String = pick("open_app.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf(
            "Opening $appName now.",
            "Launching $appName for you.",
            "Sure, opening $appName.",
        )
        KateTone.BALANCED -> listOf(
            "Opening $appName.",
            "Got it, launching $appName.",
            "On it - opening $appName now.",
        )
        KateTone.SASSY -> listOf(
            "Fine, opening $appName. Happy now?",
            "Opening $appName. You're welcome.",
            "$appName, coming right up. Try not to break anything.",
        )
    })

    fun speechForTorch(turnedOn: Boolean, tone: KateTone): String = pick("torch.$tone.$turnedOn", when (tone) {
        KateTone.PROFESSIONAL -> if (turnedOn) listOf("Torch is on.", "Flashlight enabled.") else listOf("Torch is off.", "Flashlight disabled.")
        KateTone.BALANCED -> if (turnedOn) listOf("Torch on!", "Let there be light.") else listOf("Torch off.", "Lights out.")
        KateTone.SASSY -> if (turnedOn) listOf("Torch on. Don't blind yourself.", "Flashlight's on, genius.") else listOf("Torch off. Back to the dark ages.", "Lights out, drama queen.")
    })

    fun speechForBluetooth(turnedOn: Boolean?, tone: KateTone): String = pick("bt.$tone.$turnedOn", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Bluetooth toggled.", "Bluetooth setting updated.")
        KateTone.BALANCED -> listOf("Bluetooth's been toggled.", "Done - Bluetooth updated.")
        KateTone.SASSY -> listOf("Bluetooth toggled. Riveting stuff.", "Done. Try not to lose your earbuds this time.")
    })

    fun speechForWifi(turnedOn: Boolean?, tone: KateTone): String = pick("wifi.$tone.$turnedOn", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Wi-Fi setting updated.", "Wi-Fi toggled.")
        KateTone.BALANCED -> listOf("Wi-Fi's been toggled.", "Got it - Wi-Fi updated.")
        KateTone.SASSY -> listOf("Wi-Fi toggled. Try not to hog the bandwidth.", "Done. Netflix awaits.")
    })

    fun speechForVolume(tone: KateTone): String = pick("volume.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Volume adjusted.", "Volume level updated.")
        KateTone.BALANCED -> listOf("Volume's set.", "Done - volume adjusted.")
        KateTone.SASSY -> listOf("Volume adjusted. Your neighbors are welcome.", "Done. Try not to blow your speakers.")
    })

    fun speechForCall(number: String, tone: KateTone): String = pick("call.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Calling $number.", "Dialing $number now.")
        KateTone.BALANCED -> listOf("Calling $number now.", "Dialing $number.")
        KateTone.SASSY -> listOf("Calling $number. Try to be nice.", "Dialing $number - don't overthink it.")
    })

    fun speechForHelp(tone: KateTone): String = pick("help.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf(
            "I can open apps, type text, search the web, check the weather, and control your torch, Bluetooth, Wi-Fi, and volume.",
        )
        KateTone.BALANCED -> listOf(
            "I can open apps, search the web, check weather, and toggle torch, Bluetooth, Wi-Fi, or volume for you.",
        )
        KateTone.SASSY -> listOf(
            "I open apps, search stuff, check the weather, and flip your torch, Bluetooth, and Wi-Fi. Basically I do everything - you're welcome.",
        )
    })

    fun speechForWeather(result: WeatherResult?, tone: KateTone): String {
        if (result == null) {
            return pick("weather_fail.$tone", when (tone) {
                KateTone.PROFESSIONAL -> listOf("I wasn't able to fetch the weather right now.", "Weather data is unavailable at the moment.")
                KateTone.BALANCED -> listOf("Couldn't grab the weather just now, sorry.", "Weather's not loading right now.")
                KateTone.SASSY -> listOf("The weather's hiding from me right now, apparently.", "Even I have off days - couldn't get the weather.")
            })
        }
        val temp = result.temperatureC.toInt()
        return pick("weather_ok.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf(
                "It's currently $temp degrees Celsius with ${result.description}.",
                "Current conditions: $temp degrees, ${result.description}.",
            )
            KateTone.BALANCED -> listOf(
                "It's $temp degrees out there and ${result.description}.",
                "Looks like $temp degrees and ${result.description} right now.",
            )
            KateTone.SASSY -> listOf(
                "It's $temp degrees and ${result.description}. Dress accordingly, genius.",
                "$temp degrees, ${result.description}. You're welcome for the free weather report.",
            )
        })
    }

    fun speechForSearchAnswer(answer: String, tone: KateTone): String = pick("search_ok.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Here's what I found: $answer", "According to my search: $answer")
        KateTone.BALANCED -> listOf("Here's what I found: $answer", "I looked it up - $answer")
        KateTone.SASSY -> listOf("Look what I found: $answer", "Did the work for you: $answer")
    })

    fun speechForSearchNoAnswer(tone: KateTone): String = pick("search_fail.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf(
            "I couldn't find a direct answer to that. Would you like me to open a browser search?",
        )
        KateTone.BALANCED -> listOf(
            "Couldn't find a clean answer for that one. Want me to open it in a browser?",
        )
        KateTone.SASSY -> listOf(
            "That one's above my pay grade. Want me to open a real search engine?",
        )
    })

    fun speechForUnknown(tone: KateTone): String = pick("unknown.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("I'm not sure how to help with that.", "I didn't quite catch what you need.")
        KateTone.BALANCED -> listOf("Not sure what you mean there.", "Hmm, I didn't get that one.")
        KateTone.SASSY -> listOf("I have no idea what that means, but go off.", "That one lost me completely.")
    })

    // Used when listening ended with no recognized speech at all (silence,
    // mic didn't pick anything up) - distinct from speechForUnknown, which
    // is for speech that WAS heard but didn't match any known command.
    fun speechForNoSpeech(tone: KateTone): String = pick("no_speech.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("I didn't hear anything. Please try again.", "No speech detected - go ahead and try again.")
        KateTone.BALANCED -> listOf("Didn't catch that - I didn't hear anything.", "I didn't hear you there, try again?")
        KateTone.SASSY -> listOf("...silence. Try actually saying something.", "Cricket noises over here. Try again?")
    })

    // ==================== CHARGING EVENTS (used in Batch 4) ====================

    fun speechForChargerConnected(tone: KateTone): String = pick("charge_in.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Power connected. Charging now.", "Charger detected - now charging.")
        KateTone.BALANCED -> listOf("Charging started.", "Plugged in - charging now.")
        KateTone.SASSY -> listOf("Finally, some juice. Charging now.", "About time you plugged me- I mean, your phone in.")
    })

    fun speechForChargerDisconnected(tone: KateTone): String = pick("charge_out.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Charger disconnected.", "Power disconnected.")
        KateTone.BALANCED -> listOf("Unplugged. You're on battery now.", "Charger's out - running on battery.")
        KateTone.SASSY -> listOf("Unplugged already? Living dangerously.", "Off the leash. Good luck with that battery.")
    })
}
