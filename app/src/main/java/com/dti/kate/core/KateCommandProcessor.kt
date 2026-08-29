package com.dti.kate.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.dti.kate.service.KateAccessibilityService
import com.dti.kate.utils.DeviceControlManager
import kotlinx.coroutines.CompletableDeferred

/**
 * Single source of truth for turning a transcript into an action + spoken
 * reply. Extracted from HomeScreen.handleQuery so KateOverlayService can run
 * the exact same command logic without opening KateActivity - previously
 * the overlay had no command execution at all, which is why gesture/wake
 * triggers fell back to just launching the app.
 *
 * Framework-agnostic: takes a [PermissionBridge] instead of Compose
 * permission state directly, so HomeScreen (which has a real Activity to
 * launch a permission dialog from) and the overlay Service (which does not,
 * and falls back to opening the app only for that one case) can each supply
 * their own strategy.
 *
 * KNOWN SIMPLIFICATION: resolveContact's "no fuzzy match found -> ask which
 * contact and re-listen with a name grammar" sub-flow (see HomeScreen's
 * original resolveContact) is NOT reproduced here for the no-match case -
 * that's an interactive multi-turn flow that needs its own re-listen loop
 * per caller. Both MakeCall/SendMessage below use the fast path (direct
 * fuzzy match) and fall back to a spoken "I'm not sure who you mean" rather
 * than opening a follow-up listening session. Worth building out as a
 * follow-up if voice-calling by ambiguous name from the overlay turns out
 * to matter in practice.
 */
class KateCommandProcessor(
    private val context: Context,
    private val responseGenerator: KateResponseGenerator,
    private val deviceControl: DeviceControlManager,
    private val weatherService: WeatherService,
    private val webSearchService: WebSearchService,
    private val appLauncher: AppLauncher,
    private val musicLauncher: MusicLauncher,
    private val contactsHelper: ContactsHelper,
    private val locationHelper: LocationHelper,
    private val permissionBridge: PermissionBridge,
    // Batch 1/2 additions. Defaulted so existing call sites (HomeScreen,
    // KateOverlayService) keep compiling without touching every
    // constructor call - each real call site is updated to pass its own
    // instances below, but the defaults keep this change non-breaking for
    // anything still under construction elsewhere.
    private val conversationMemory: ConversationMemory = ConversationMemory(context),
    private val jokeService: JokeService = JokeService(),
    private val settings: LocalSettingsStore = LocalSettingsStore(context),
    private val wikipediaService: WikipediaService = WikipediaService(),
    private val agentSearchService: AgentSearchService = AgentSearchService(context),
    private val entitlements: com.dti.kate.billing.EntitlementStore = com.dti.kate.billing.EntitlementStore(context),
) {
    interface PermissionBridge {
        fun hasContacts(): Boolean
        fun hasLocation(): Boolean
        /** Called when a permission is missing. Implementations should either launch a request (if they have an Activity) or open the app to the right screen (if they don't) - either way, returns immediately; the current command just fails gracefully this time around. */
        fun requestContacts()
        fun requestLocation()
    }

    data class Result(val action: KateAction, val speech: String)

    suspend fun process(query: String, tone: KateTone): Result {
        if (query.isBlank()) {
            return Result(KateAction.Unknown, responseGenerator.speechForNoSpeech(tone))
        }

        val action = responseGenerator.classify(query)
        val speech = when (action) {
            is KateAction.OpenApp -> {
                val opened = appLauncher.openAppByName(action.appName)
                if (opened) responseGenerator.speechForOpenApp(action.appName, tone)
                else "I couldn't find an app called ${action.appName} on this device."
            }
            is KateAction.PlayMusic -> {
                val musicApp = musicLauncher.playSong(action.song)
                responseGenerator.speechForPlayMusic(action.song, musicApp, tone)
            }
            is KateAction.TypeText -> {
                if (!KateAccessibilityService.isEnabled(context)) {
                    KateAccessibilityService.openAccessibilitySettings(context)
                    "I need accessibility access to type for you. If the toggle looks greyed out, " +
                        "check the Permissions section in Kate's own Settings for how to unlock it."
                } else if (accessibilityNeedsReconnect()) {
                    responseGenerator.speechForAccessibilityReconnectNeeded(tone)
                } else {
                    val typed = KateAccessibilityService.instance?.typeText(action.text) ?: false
                    if (typed) "Typed it." else "I couldn't find a text field to type into."
                }
            }
            is KateAction.ToggleTorch -> {
                val turningOn = action.turnOn ?: !deviceControl.isTorchOn()
                deviceControl.setTorch(turningOn)
                responseGenerator.speechForTorch(turningOn, tone)
            }
            is KateAction.ToggleBluetooth -> {
                when (action.turnOn) {
                    true -> deviceControl.setBluetooth(true)
                    false -> deviceControl.setBluetooth(false)
                    null -> deviceControl.toggleBluetooth()
                }
                responseGenerator.speechForBluetooth(action.turnOn, tone)
            }
            is KateAction.ToggleWifi -> {
                when (action.turnOn) {
                    true -> deviceControl.setWifi(true)
                    false -> deviceControl.setWifi(false)
                    null -> deviceControl.toggleWifi()
                }
                responseGenerator.speechForWifi(action.turnOn, tone)
            }
            is KateAction.SetVolume -> {
                when {
                    action.level != null -> deviceControl.setVolume(action.level)
                    action.increase == true -> deviceControl.increaseVolume()
                    action.increase == false -> deviceControl.decreaseVolume()
                }
                responseGenerator.speechForVolume(tone)
            }
            is KateAction.MakeCall -> {
                if (!permissionBridge.hasContacts()) {
                    permissionBridge.requestContacts()
                    responseGenerator.speechForNoContactsPermission(tone)
                } else {
                    val contact = resolveContactFastPath(action.spokenName)
                    if (contact != null) {
                        deviceControl.makeCall(contact.phoneNumber)
                        responseGenerator.speechForCall(contact.name, tone)
                    } else {
                        responseGenerator.speechForContactNotFound(action.spokenName, tone)
                    }
                }
            }
            is KateAction.SendMessage -> {
                if (!permissionBridge.hasContacts()) {
                    permissionBridge.requestContacts()
                    responseGenerator.speechForNoContactsPermission(tone)
                } else {
                    val contact = resolveContactFastPath(action.spokenName)
                    if (contact != null) {
                        val body = action.body ?: "Hi"
                        if (action.viaApp != null) {
                            // App-targeted send (WhatsApp/Messenger) - see
                            // MessagingAppAutomator's doc comment on why
                            // this is meaningfully more fragile than plain
                            // SMS. On failure (app not installed, UI
                            // selectors didn't match this build, etc.) we
                            // deliberately do NOT silently fall back to SMS:
                            // the user asked for a specific app, and SMS'ing
                            // a person who doesn't check texts defeats the
                            // point of naming the app at all. Instead we
                            // tell them plainly it didn't work and let them
                            // decide.
                            val sent = deviceControl.sendViaMessagingApp(action.viaApp, contact.name, body)
                            if (sent) responseGenerator.speechForMessageViaApp(contact.name, action.viaApp, tone)
                            else responseGenerator.speechForMessageViaAppFailed(contact.name, action.viaApp, tone)
                        } else {
                            deviceControl.sendSms(contact.phoneNumber, body)
                            responseGenerator.speechForMessage(contact.name, tone)
                        }
                    } else {
                        responseGenerator.speechForContactNotFound(action.spokenName, tone)
                    }
                }
            }
            is KateAction.Weather -> {
                if (!permissionBridge.hasLocation()) {
                    permissionBridge.requestLocation()
                    "I need location access to check the weather - please grant it and try again."
                } else {
                    val coords = locationHelper.getLastKnownLocation()
                    if (coords == null) responseGenerator.speechForWeather(null, tone)
                    else {
                        val result = weatherService.getCurrentWeather(coords.first, coords.second)
                        responseGenerator.speechForWeather(result, tone)
                    }
                }
            }
            is KateAction.WebSearch -> {
                // DuckDuckGo first (fast, free, keyless - good for
                // definitions/facts), then Wikipedia summary (also free -
                // covers named entities DDG misses), then the LLM-routed
                // backend endpoint as a last resort for everything else -
                // general knowledge, grammar questions, current officeholders,
                // anything without a clean instant-answer or article match.
                // Only this last step costs an API call, and the backend
                // caches by query so repeats are free too - only then do we
                // admit defeat and offer a browser search, same as before.
                val answer = webSearchService.getInstantAnswer(action.query)
                    ?: wikipediaService.getSummary(action.query)
                    ?: agentSearchService.ask(action.query)
                if (answer != null) responseGenerator.speechForSearchAnswer(answer, tone)
                else responseGenerator.speechForSearchNoAnswer(tone)
            }
            KateAction.Help -> responseGenerator.speechForHelp(tone)

            // ---- Batch 1 additions ----
            KateAction.CurrentTime -> {
                val formatted = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())
                responseGenerator.speechForCurrentTime(formatted, tone)
            }
            KateAction.SetAlarm -> {
                // No dangerous permission needed: ACTION_SET_ALARM is a
                // public implicit intent handled by whatever clock app is
                // installed, which opens pre-filled for the user to confirm.
                // We deliberately don't try to parse "7am" -> hour/minute
                // extras here yet - the clock app's own UI handles that
                // confirmation step, and a misparsed hour that silently
                // sets the wrong alarm is worse than one extra tap.
                try {
                    val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    responseGenerator.speechForSetAlarmHandoff(tone)
                } catch (e: Exception) {
                    responseGenerator.speechForUnknown(tone)
                }
            }
            KateAction.WhoMadeYou -> responseGenerator.speechForWhoMadeYou(tone)
            KateAction.WhoAreYou -> responseGenerator.speechForWhoAreYou(tone, settings.getUserName())
            is KateAction.SetReminder -> {
                val dao = com.dti.kate.data.db.KateDatabase.getInstance(context).reminderDao()
                val id = dao.insert(
                    com.dti.kate.data.db.Reminder(
                        text = action.text,
                        triggerAtMillis = action.triggerAtMillis,
                        createdAtMillis = System.currentTimeMillis(),
                    )
                )
                ReminderScheduler.schedule(context, id, action.text, action.triggerAtMillis)
                responseGenerator.speechForReminderSet(action.text, action.triggerAtMillis, tone)
            }
            KateAction.ReminderTimeUnclear -> responseGenerator.speechForReminderTimeUnclear(tone)
            is KateAction.Calculate -> {
                val result = MathEvaluator.evaluate(action.expression)
                if (result != null) responseGenerator.speechForCalculationResult(action.expression, result, tone)
                else responseGenerator.speechForCalculationFailed(tone)
            }
            KateAction.TellJoke -> {
                // Premium+ feature - see billing.FeatureGate. Free-tier
                // users get a clear upsell instead of the joke silently
                // not working, so the gate is visible rather than feeling
                // like a bug.
                if (!entitlements.isUnlocked(com.dti.kate.billing.GatedFeature.JOKES)) {
                    responseGenerator.speechForFeatureLocked(tone)
                } else {
                    val joke = jokeService.getJoke(tone)
                    if (joke != null) responseGenerator.speechForJoke(joke, tone)
                    else responseGenerator.speechForJokeFailed(tone)
                }
            }
            is KateAction.SmallTalk ->
                responseGenerator.speechForSmallTalk(action.kind, tone, settings.getUserName())

            // ---- Batch 3 additions ----
            KateAction.GoHome ->
                if (accessibilityNeedsReconnect()) responseGenerator.speechForAccessibilityReconnectNeeded(tone)
                else responseGenerator.speechForGoHome(deviceControl.goHome(), tone)
            KateAction.GoBack ->
                if (accessibilityNeedsReconnect()) responseGenerator.speechForAccessibilityReconnectNeeded(tone)
                else responseGenerator.speechForGoBack(deviceControl.goBack(), tone)
            KateAction.ShowRecents ->
                if (accessibilityNeedsReconnect()) responseGenerator.speechForAccessibilityReconnectNeeded(tone)
                else responseGenerator.speechForShowRecents(deviceControl.showRecentApps(), tone)
            KateAction.LockScreen ->
                if (accessibilityNeedsReconnect()) responseGenerator.speechForAccessibilityReconnectNeeded(tone)
                else responseGenerator.speechForLockScreen(deviceControl.lockScreen(), tone)
            KateAction.TakeScreenshot ->
                if (accessibilityNeedsReconnect()) responseGenerator.speechForAccessibilityReconnectNeeded(tone)
                else responseGenerator.speechForScreenshot(deviceControl.takeScreenshot(), tone)

            // ---- Batch 5 additions ----
            KateAction.AnswerCall -> {
                val success = deviceControl.answerCall()
                if (success) responseGenerator.speechForCallAnswered(tone)
                else responseGenerator.speechForCallActionFailed(tone)
            }
            KateAction.DeclineCall -> {
                val success = deviceControl.declineCall()
                if (success) responseGenerator.speechForCallDeclined(tone)
                else responseGenerator.speechForCallActionFailed(tone)
            }

            KateAction.Unknown -> responseGenerator.speechForUnknown(tone)
        }

        conversationMemory.record(userText = query, kateReply = speech, topic = topicLabel(action))
        return Result(action, speech)
    }

    /** Coarse label stored alongside each turn - see ConversationTurn's doc
     * comment for why this stays a simple string rather than reusing
     * KateAction itself (which isn't Room-storable without a type converter
     * we don't otherwise need yet). */
    /** True only in the "malfunctioning" state reported by users - the
     * permission IS granted (isEnabled), but the live service connection
     * dropped (see DeviceControlManager.isAccessibilityServiceRunning()
     * doc comment). Callers should check this before attempting a global
     * action so the failure message accurately says "reconnect" rather
     * than "turn on a permission that's already on". */
    private fun accessibilityNeedsReconnect(): Boolean =
        KateAccessibilityService.isEnabled(context) && !deviceControl.isAccessibilityServiceRunning()

    private fun topicLabel(action: KateAction): String = when (action) {
        is KateAction.OpenApp -> "open_app"
        is KateAction.PlayMusic -> "music"
        is KateAction.TypeText -> "type_text"
        is KateAction.ToggleTorch -> "torch"
        is KateAction.ToggleBluetooth -> "bluetooth"
        is KateAction.ToggleWifi -> "wifi"
        is KateAction.SetVolume -> "volume"
        is KateAction.MakeCall -> "call"
        is KateAction.SendMessage -> "message"
        KateAction.Weather -> "weather"
        is KateAction.WebSearch -> "search"
        KateAction.CurrentTime -> "time"
        KateAction.SetAlarm -> "alarm"
        is KateAction.SetReminder, KateAction.ReminderTimeUnclear -> "reminder"
        KateAction.WhoMadeYou, KateAction.WhoAreYou -> "identity"
        is KateAction.Calculate -> "math"
        KateAction.TellJoke -> "joke"
        is KateAction.SmallTalk -> "smalltalk"
        KateAction.GoHome -> "go_home"
        KateAction.GoBack -> "go_back"
        KateAction.ShowRecents -> "recents"
        KateAction.LockScreen -> "lock_screen"
        KateAction.TakeScreenshot -> "screenshot"
        KateAction.AnswerCall -> "answer_call"
        KateAction.DeclineCall -> "decline_call"
        KateAction.Help -> "help"
        KateAction.Unknown -> "unknown"
    }

    /** Direct fuzzy-match only - see class doc's KNOWN SIMPLIFICATION for what this deliberately doesn't do. */
    private suspend fun resolveContactFastPath(spokenName: String): Contact? {
        val contacts = contactsHelper.getAllContacts()
        if (contacts.isEmpty()) return null
        return contactsHelper.findBestMatch(spokenName, contacts)
    }
}
