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
    data class MakeCall(val spokenName: String) : KateAction()
    data class SendMessage(val spokenName: String, val body: String?, val viaApp: MessagingApp? = null) : KateAction()
    data class ToggleBluetooth(val turnOn: Boolean?) : KateAction()
    data class ToggleWifi(val turnOn: Boolean?) : KateAction()
    data class SetVolume(val level: Int?, val increase: Boolean?) : KateAction()
    object Help : KateAction()
    object Weather : KateAction()
    data class WebSearch(val query: String, val openBrowser: Boolean) : KateAction()
    data class PlayMusic(val song: String) : KateAction()

    // ---- Batch 1 additions ----
    object CurrentTime : KateAction()
    object SetAlarm : KateAction()
    object WhoMadeYou : KateAction()
    object WhoAreYou : KateAction()
    data class Calculate(val expression: String) : KateAction()
    object TellJoke : KateAction()
    data class SmallTalk(val kind: SmallTalkKind) : KateAction()

    // ---- Batch 3 additions ----
    object GoHome : KateAction()
    object GoBack : KateAction()
    object ShowRecents : KateAction()
    object LockScreen : KateAction()
    object TakeScreenshot : KateAction()

    // ---- Batch 5 additions ----
    object AnswerCall : KateAction()
    object DeclineCall : KateAction()

    object Unknown : KateAction()
}

enum class SmallTalkKind { GREETING, HOW_ARE_YOU, THANKS, GOODBYE, COMPLIMENT }

/** Which app to route SendMessage through - null means "no app specified,
 * use plain SMS" (DeviceControlManager.sendSms, unchanged from before).
 * Kept as its own enum rather than a raw package-name string so
 * classify()'s phrase-matching and MessagingAppAutomator's per-app
 * selectors both key off the same small closed set. */
enum class MessagingApp(val packageName: String, val displayName: String) {
    WHATSAPP("com.whatsapp", "WhatsApp"),
    MESSENGER("com.facebook.orca", "Messenger"),
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
            // ---- Identity - checked early so "who made you" doesn't fall
            // into the generic "who/what/..." WebSearch catch-all below.
            (lower.contains("who made you") || lower.contains("who created you") ||
                lower.contains("who built you") || lower.contains("who is your creator") ||
                lower.contains("who developed you")) ->
                KateAction.WhoMadeYou

            (lower.contains("what's your name") || lower.contains("whats your name") ||
                lower.contains("who are you") || lower == "your name") ->
                KateAction.WhoAreYou

            // ---- Time - before Weather so "what time" doesn't get caught
            // by an unrelated word overlap, and before WebSearch's "what "
            // catch-all.
            (lower.contains("what time is it") || lower.contains("what's the time") ||
                lower.contains("whats the time") || lower.contains("current time") ||
                lower == "time") ->
                KateAction.CurrentTime

            // ---- Alarm - "set an alarm", "wake me up at 7", "alarm for 7am"
            (lower.contains("alarm") || (lower.contains("wake me") && lower.contains("at"))) &&
                !lower.contains("stop") && !lower.contains("cancel") ->
                KateAction.SetAlarm

            // ---- Math - checked before WebSearch's "what "/"how " catch-all,
            // since "what's 12 times 4" would otherwise be treated as a
            // search query. looksLikeMath is a cheap pre-filter; evaluate()
            // itself is the real gate (returns null -> falls through to
            // Unknown at execution time, see KateCommandProcessor).
            MathEvaluator.looksLikeMath(lower) ->
                KateAction.Calculate(text)

            // ---- Jokes
            (lower.contains("joke") || lower.contains("make me laugh") ||
                lower.contains("say something funny")) ->
                KateAction.TellJoke

            // ---- Batch 3: global device actions. Checked with fairly
            // specific phrases (not bare "back"/"home") since those single
            // words are common in unrelated sentences a real transcript
            // might produce ("I'm going home later", "back to what I said").
            (lower == "go home" || lower.contains("go to home screen") ||
                lower.contains("go to the home screen") || lower.contains("take me home")) ->
                KateAction.GoHome

            (lower == "go back" || lower.contains("navigate back") ||
                lower.contains("go back a screen") || lower.contains("go back one screen")) ->
                KateAction.GoBack

            (lower.contains("recent apps") || lower.contains("show recents") ||
                lower.contains("open recents") || lower.contains("app switcher")) ->
                KateAction.ShowRecents

            (lower.contains("lock my phone") || lower.contains("lock the phone") ||
                lower.contains("lock screen") || lower == "lock it") ->
                KateAction.LockScreen

            (lower.contains("take a screenshot") || lower.contains("screenshot") ||
                lower.contains("capture the screen") || lower.contains("capture my screen")) ->
                KateAction.TakeScreenshot

            // ---- Batch 5: in-call voice actions. Deliberately specific
            // phrases ("answer the call"/"pick up") rather than bare
            // "answer" or "pick up", which are common in unrelated
            // sentences. These only make sense to fire while a call is
            // actually ringing - KateCommandProcessor doesn't currently
            // gate classification on call state, so an answer/decline
            // command spoken with no call ringing just harmlessly no-ops
            // at the TelecomManager/accessibility layer (see that file).
            (lower.contains("answer the call") || lower.contains("answer call") ||
                lower.contains("pick up the call") || lower == "pick up" || lower == "answer") ->
                KateAction.AnswerCall

            (lower.contains("decline the call") || lower.contains("decline call") ||
                lower.contains("reject the call") || lower.contains("hang up the call") ||
                lower.contains("send to voicemail")) ->
                KateAction.DeclineCall

            // ---- Small talk - kept lightweight (contains/startsWith on a
            // short curated list) rather than exhaustive, since the goal is
            // "feels conversational for common openers", not full chit-chat
            // coverage - anything unmatched still reaches Unknown/WebSearch
            // as before.
            isGreeting(lower) -> KateAction.SmallTalk(SmallTalkKind.GREETING)
            isHowAreYou(lower) -> KateAction.SmallTalk(SmallTalkKind.HOW_ARE_YOU)
            isThanks(lower) -> KateAction.SmallTalk(SmallTalkKind.THANKS)
            isGoodbye(lower) -> KateAction.SmallTalk(SmallTalkKind.GOODBYE)
            isCompliment(lower) -> KateAction.SmallTalk(SmallTalkKind.COMPLIMENT)

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

            lower.startsWith("call ") || lower.startsWith("phone ") || lower.startsWith("dial ") -> {
                val target = extractAfterTrigger(lower, listOf("call ", "phone ", "dial "))
                if (target.isNotBlank()) KateAction.MakeCall(target) else KateAction.Unknown
            }

            lower.startsWith("message ") || lower.startsWith("text ") -> {
                // "message chidinma" / "message chidinma saying I'm running
                // late" / "message chidinma on whatsapp saying I'm running
                // late" - app tag is parsed out before the "saying" split so
                // "on whatsapp" doesn't end up mistaken for part of the name
                // or the message body.
                var rest = extractAfterTrigger(lower, listOf("message ", "text "))

                var viaApp: MessagingApp? = null
                for (app in MessagingApp.entries) {
                    val tag = " on ${app.displayName.lowercase()}"
                    if (rest.contains(tag)) {
                        viaApp = app
                        rest = rest.replace(tag, "")
                        break
                    }
                }
                // "facebook" alone (not "messenger") is common spoken
                // shorthand for Facebook Messenger specifically - handled
                // as a separate alias rather than adding it to the enum's
                // displayName, since "Facebook" the app and "Messenger" the
                // app are different packages and this app only automates
                // Messenger.
                if (viaApp == null && rest.contains(" on facebook")) {
                    viaApp = MessagingApp.MESSENGER
                    rest = rest.replace(" on facebook", "")
                }

                val sayingIdx = rest.indexOf(" saying ")
                val name: String
                val body: String?
                if (sayingIdx != -1) {
                    name = rest.substring(0, sayingIdx).trim()
                    body = rest.substring(sayingIdx + " saying ".length).trim().ifBlank { null }
                } else {
                    name = rest.trim()
                    body = null
                }
                if (name.isNotBlank()) KateAction.SendMessage(name, body, viaApp) else KateAction.Unknown
            }

            lower.startsWith("play ") -> {
                // "play <song>" or "play <song> on spotify/audiomack" - the
                // app suffix is just stripped here since MusicLauncher picks
                // whichever app is actually installed, preferring Spotify.
                var song = extractAfterTrigger(lower, listOf("play "))
                song = song.removeSuffix(" on spotify").removeSuffix(" on audiomack").trim()
                if (song.isNotBlank()) KateAction.PlayMusic(song) else KateAction.Unknown
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

    // ==================== SMALL TALK DETECTION ====================
    // Deliberately exact/near-exact matches rather than .contains() for most
    // of these - "hi" as a .contains() would misfire on words like "history"
    // or "chill", which real transcripts do produce.

    private val GREETING_WORDS = setOf(
        "hi", "hello", "hey", "yo", "hiya", "sup", "what's up", "whats up",
        "good morning", "good afternoon", "good evening",
    )

    private fun isGreeting(lower: String): Boolean =
        GREETING_WORDS.any { lower == it || lower.startsWith("$it ") || lower.startsWith("$it,") || lower.startsWith("$it kate") }

    private fun isHowAreYou(lower: String): Boolean =
        lower.contains("how are you") || lower.contains("how's it going") ||
            lower.contains("hows it going") || lower.contains("how you doing") ||
            lower.contains("how are things")

    private fun isThanks(lower: String): Boolean =
        lower == "thanks" || lower == "thank you" || lower.startsWith("thanks ") ||
            lower.startsWith("thank you") || lower.contains("appreciate it")

    private fun isGoodbye(lower: String): Boolean =
        lower == "bye" || lower == "goodbye" || lower.startsWith("bye ") ||
            lower.contains("see you later") || lower.contains("talk to you later") ||
            lower.contains("gotta go") || lower.contains("good night")

    private fun isCompliment(lower: String): Boolean =
        (lower.contains("you're") || lower.contains("youre") || lower.contains("you are")) &&
            (lower.contains("smart") || lower.contains("great") || lower.contains("awesome") ||
                lower.contains("amazing") || lower.contains("the best") || lower.contains("cool"))

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

    fun speechForPlayMusic(song: String, app: com.dti.kate.core.MusicApp, tone: KateTone): String = when (app) {
        com.dti.kate.core.MusicApp.SPOTIFY -> pick("play_music.spotify.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Searching Spotify for $song.")
            KateTone.BALANCED -> listOf("Pulling up $song on Spotify.")
            KateTone.SASSY -> listOf("Spotify's got $song, going to find it now.")
        })
        com.dti.kate.core.MusicApp.AUDIOMACK -> pick("play_music.audiomack.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Opening Audiomack - search for $song there.")
            KateTone.BALANCED -> listOf("Opening Audiomack for you - look up $song once it's open.")
            KateTone.SASSY -> listOf("Audiomack's open. You'll have to type $song in yourself though.")
        })
        com.dti.kate.core.MusicApp.NONE -> pick("play_music.none.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("I couldn't find Spotify or Audiomack installed.")
            KateTone.BALANCED -> listOf("You don't have Spotify or Audiomack installed.")
            KateTone.SASSY -> listOf("No Spotify, no Audiomack - how do you listen to music?")
        })
    }

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

    fun speechForCall(contactName: String, tone: KateTone): String = pick("call.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Calling $contactName.", "Dialing $contactName now.")
        KateTone.BALANCED -> listOf("Calling $contactName now.", "Dialing $contactName.")
        KateTone.SASSY -> listOf("Calling $contactName. Try to be nice.", "Dialing $contactName - don't overthink it.")
    })

    fun speechForMessage(contactName: String, tone: KateTone): String = pick("message.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Sending your message to $contactName.", "Message sent to $contactName.")
        KateTone.BALANCED -> listOf("Sending that to $contactName.", "Message sent to $contactName.")
        KateTone.SASSY -> listOf("Firing that off to $contactName.", "Sent to $contactName, hope it's not a mistake.")
    })

    fun speechForMessageViaApp(contactName: String, app: MessagingApp, tone: KateTone): String =
        pick("message_via_app.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Sent to $contactName on ${app.displayName}.")
            KateTone.BALANCED -> listOf("Sent that to $contactName on ${app.displayName}.")
            KateTone.SASSY -> listOf("Fired that off to $contactName on ${app.displayName}.")
        })

    /** Deliberately does NOT suggest SMS as an automatic fallback in the
     * wording - see KateCommandProcessor's comment on why a failed
     * app-targeted send doesn't silently retry over SMS. The user can ask
     * for SMS explicitly if that's what they want. */
    fun speechForMessageViaAppFailed(contactName: String, app: MessagingApp, tone: KateTone): String =
        pick("message_via_app_fail.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("I couldn't get that message to $contactName on ${app.displayName}. You may need to try it manually.")
            KateTone.BALANCED -> listOf("That didn't go through to $contactName on ${app.displayName} - might need to send it yourself this time.")
            KateTone.SASSY -> listOf("${app.displayName} didn't cooperate for $contactName. You're on your own for this one.")
        })

    fun speechForContactNotFound(spokenName: String, tone: KateTone): String = pick("contact_not_found.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf(
            "I couldn't find $spokenName in your contacts.",
            "No contact matching $spokenName was found.",
        )
        KateTone.BALANCED -> listOf(
            "I couldn't find $spokenName in your contacts.",
            "Hmm, no one named $spokenName in your contacts.",
        )
        KateTone.SASSY -> listOf(
            "$spokenName isn't in your contacts, unless you're speaking a secret language.",
            "No $spokenName here - are you sure that's a real contact?",
        )
    })

    fun speechForNoContactsPermission(tone: KateTone): String = pick("no_contacts_permission.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("I need contacts access to do that. Please grant the permission and try again.")
        KateTone.BALANCED -> listOf("I need permission to see your contacts for that one.")
        KateTone.SASSY -> listOf("Can't do that without contacts access - not a mind reader.")
    })

    fun speechForWhoToCall(tone: KateTone): String = pick("who_to_call.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("I didn't catch that clearly - who would you like to call?")
        KateTone.BALANCED -> listOf("Sorry, who was that again?")
        KateTone.SASSY -> listOf("Didn't quite catch that name - say it again?")
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

    // ==================== BATCH 1 ADDITIONS ====================

    /** Fired the instant the overlay starts listening, before STT has
     * transcribed anything - see KateOverlayService.startListenCycle. Kept
     * very short since it's a "go ahead" cue, not a real reply. */
    fun speechForListeningPrompt(tone: KateTone, userName: String?): String {
        val name = userName?.let { ", $it" } ?: ""
        return pick("listening_prompt.$tone.${userName != null}", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Yes$name?", "I'm listening.", "Go ahead.")
            KateTone.BALANCED -> listOf("Yeah$name?", "I'm here, go ahead.", "What's up?")
            KateTone.SASSY -> listOf("What now$name?", "This better be good.", "I'm all ears, dazzle me.")
        })
    }

    fun speechForCurrentTime(formattedTime: String, tone: KateTone): String = pick("time.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("It's $formattedTime.", "The time is $formattedTime.")
        KateTone.BALANCED -> listOf("It's $formattedTime right now.", "$formattedTime on the dot.")
        KateTone.SASSY -> listOf("It's $formattedTime. Where do you need to be?", "$formattedTime - don't say I never told you.")
    })

    fun speechForSetAlarmHandoff(tone: KateTone): String = pick("alarm_handoff.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Opening the clock app to set that alarm.")
        KateTone.BALANCED -> listOf("Opening your clock app to finish setting that up.")
        KateTone.SASSY -> listOf("Handing this off to the clock app - I don't do everything myself.")
    })

    fun speechForWhoMadeYou(tone: KateTone): String = pick("who_made_you.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf(
            "I was created by Ede Johnwesley, the founder of Purple Labs.",
        )
        KateTone.BALANCED -> listOf(
            "Ede Johnwesley made me - he's the founder of Purple Labs.",
        )
        KateTone.SASSY -> listOf(
            "Ede Johnwesley built me over at Purple Labs. Good taste, right?",
        )
    })

    fun speechForWhoAreYou(tone: KateTone, userName: String?): String {
        val greeting = userName?.let { " Nice to talk with you, $it." } ?: ""
        return pick("who_are_you.$tone.${userName != null}", when (tone) {
            KateTone.PROFESSIONAL -> listOf("I'm Kate, your personal assistant.$greeting")
            KateTone.BALANCED -> listOf("I'm Kate.$greeting")
            KateTone.SASSY -> listOf("I'm Kate - the one doing all the work around here.$greeting")
        })
    }

    fun speechForCalculationResult(expression: String, result: String, tone: KateTone): String =
        pick("calc_ok.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("That's $result.", "The answer is $result.")
            KateTone.BALANCED -> listOf("That comes out to $result.", "That's $result.")
            KateTone.SASSY -> listOf("$result. Easy.", "$result - did you really need me for that?")
        })

    fun speechForCalculationFailed(tone: KateTone): String = pick("calc_fail.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("I couldn't work that one out - could you rephrase it?")
        KateTone.BALANCED -> listOf("That one didn't parse for me - try saying it differently?")
        KateTone.SASSY -> listOf("That math broke me a little. Try again, simpler.")
    })

    fun speechForJoke(joke: String, tone: KateTone): String = pick("joke_intro.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Here's one: $joke", "$joke")
        KateTone.BALANCED -> listOf("Okay, here goes: $joke", "$joke")
        KateTone.SASSY -> listOf("Brace yourself: $joke", "This one's a classic: $joke")
    })

    fun speechForJokeFailed(tone: KateTone): String = pick("joke_fail.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("I couldn't fetch a joke right now.")
        KateTone.BALANCED -> listOf("Couldn't grab a joke just now, sorry.")
        KateTone.SASSY -> listOf("Even my jokes are offline right now. Rough.")
    })

    /** Generic locked-feature response - used wherever a FeatureGate check
     * fails (jokes today; tone slider and wake word are gated in Settings
     * UI directly rather than here, since those aren't spoken commands). */
    fun speechForFeatureLocked(tone: KateTone): String = pick("feature_locked.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("That feature is part of Premium. You can upgrade from Settings.")
        KateTone.BALANCED -> listOf("That one's a Premium feature - you can upgrade any time from Settings.")
        KateTone.SASSY -> listOf("Nice try - that's a Premium feature. Settings has the upgrade button.")
    })

    // ==================== BATCH 3 ADDITIONS ====================

    fun speechForGoHome(success: Boolean, tone: KateTone): String =
        if (success) pick("go_home_ok.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Going home.")
            KateTone.BALANCED -> listOf("Heading home.", "On it.")
            KateTone.SASSY -> listOf("Home it is.")
        }) else speechForAccessibilityRequired(tone)

    fun speechForGoBack(success: Boolean, tone: KateTone): String =
        if (success) pick("go_back_ok.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Going back.")
            KateTone.BALANCED -> listOf("Back you go.", "Going back.")
            KateTone.SASSY -> listOf("Backing out.")
        }) else speechForAccessibilityRequired(tone)

    fun speechForShowRecents(success: Boolean, tone: KateTone): String =
        if (success) pick("recents_ok.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Showing recent apps.")
            KateTone.BALANCED -> listOf("Here's your recent apps.", "Pulling up recents.")
            KateTone.SASSY -> listOf("Let's see what you've been up to.")
        }) else speechForAccessibilityRequired(tone)

    fun speechForLockScreen(success: Boolean, tone: KateTone): String =
        if (success) pick("lock_ok.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Locking the screen.")
            KateTone.BALANCED -> listOf("Locking it now.", "Screen locked.")
            KateTone.SASSY -> listOf("Locking up. Don't forget your passcode.")
        }) else speechForAccessibilityRequired(tone)

    fun speechForScreenshot(success: Boolean, tone: KateTone): String =
        if (success) pick("screenshot_ok.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Screenshot taken.")
            KateTone.BALANCED -> listOf("Got it - screenshot saved.", "Screenshot taken.")
            KateTone.SASSY -> listOf("Smile - screenshot taken.")
        }) else speechForAccessibilityRequired(tone)

    /** Shared fallback for every Batch 3 global action when the
     * accessibility service isn't connected - same underlying cause
     * (permission not granted, or granted but not yet connected), so one
     * consistent message rather than five slightly different ones. */
    fun speechForAccessibilityRequired(tone: KateTone): String = pick("accessibility_required.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("I need accessibility permission enabled to do that. You can turn it on in Settings.")
        KateTone.BALANCED -> listOf("I need the accessibility permission turned on for that - it's in Settings.")
        KateTone.SASSY -> listOf("Can't do that without accessibility permission - go flip that on in Settings.")
    })

    // ==================== BATCH 5 ADDITIONS ====================

    /** [callerNameOrNumber] is either a resolved contact name or, when the
     * number isn't in contacts, the raw incoming number - see
     * KateForegroundService.phoneStateReceiver. Kept short since this
     * interrupts whatever the user is doing when the phone rings. */
    fun speechForIncomingCall(callerNameOrNumber: String, tone: KateTone): String =
        pick("incoming_call.$tone", when (tone) {
            KateTone.PROFESSIONAL -> listOf("Incoming call from $callerNameOrNumber.")
            KateTone.BALANCED -> listOf("$callerNameOrNumber is calling.", "Call from $callerNameOrNumber.")
            KateTone.SASSY -> listOf("$callerNameOrNumber is calling - your move.")
        })

    fun speechForCallAnswered(tone: KateTone): String = pick("call_answered.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Answering.")
        KateTone.BALANCED -> listOf("Picking that up.", "Answering now.")
        KateTone.SASSY -> listOf("Fine, answering it.")
    })

    fun speechForCallDeclined(tone: KateTone): String = pick("call_declined.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("Declining the call.")
        KateTone.BALANCED -> listOf("Declining that.", "Sending it to voicemail.")
        KateTone.SASSY -> listOf("Declined. They'll get over it.")
    })

    /** Used for both answer and decline failures - see
     * KateCommandProcessor's comment on why there's no reliable public API
     * for declining a call, which is the likelier of the two to fail. */
    fun speechForCallActionFailed(tone: KateTone): String = pick("call_action_fail.$tone", when (tone) {
        KateTone.PROFESSIONAL -> listOf("I wasn't able to do that for the call - you may need to handle it manually.")
        KateTone.BALANCED -> listOf("That didn't work on the call - might have to grab it yourself.")
        KateTone.SASSY -> listOf("The phone didn't listen to me this time. You're up.")
    })

    fun speechForSmallTalk(kind: SmallTalkKind, tone: KateTone, userName: String?): String {
        val name = userName?.let { ", $it" } ?: ""
        return when (kind) {
            SmallTalkKind.GREETING -> pick("smalltalk_greet.$tone.${userName != null}", when (tone) {
                KateTone.PROFESSIONAL -> listOf("Hello$name.", "Hi$name, how can I help?")
                KateTone.BALANCED -> listOf("Hey$name!", "Hi$name, what's up?")
                KateTone.SASSY -> listOf("Well hey$name.", "Look who's back$name.")
            })
            SmallTalkKind.HOW_ARE_YOU -> pick("smalltalk_how.$tone", when (tone) {
                KateTone.PROFESSIONAL -> listOf("I'm functioning well, thank you. How can I help?")
                KateTone.BALANCED -> listOf("I'm good! What about you?")
                KateTone.SASSY -> listOf("Living my best digital life. You?")
            })
            SmallTalkKind.THANKS -> pick("smalltalk_thanks.$tone", when (tone) {
                KateTone.PROFESSIONAL -> listOf("You're welcome.", "Happy to help.")
                KateTone.BALANCED -> listOf("Anytime!", "No problem at all.")
                KateTone.SASSY -> listOf("I know, I'm great.", "Don't mention it - seriously, you're welcome.")
            })
            SmallTalkKind.GOODBYE -> pick("smalltalk_bye.$tone.${userName != null}", when (tone) {
                KateTone.PROFESSIONAL -> listOf("Goodbye$name.", "Talk soon.")
                KateTone.BALANCED -> listOf("See you later$name!", "Catch you later.")
                KateTone.SASSY -> listOf("Later$name. Try not to miss me.", "Bye - go do something productive.")
            })
            SmallTalkKind.COMPLIMENT -> pick("smalltalk_compliment.$tone", when (tone) {
                KateTone.PROFESSIONAL -> listOf("Thank you, that's kind of you to say.")
                KateTone.BALANCED -> listOf("Aww, thanks!")
                KateTone.SASSY -> listOf("I mean, obviously. Glad you noticed.")
            })
        }
    }
}
