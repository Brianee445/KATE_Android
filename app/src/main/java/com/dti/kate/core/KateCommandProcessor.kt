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
                    "I need accessibility access to type for you - please turn it on for Kate."
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
                        deviceControl.sendSms(contact.phoneNumber, body)
                        responseGenerator.speechForMessage(contact.name, tone)
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
                val answer = webSearchService.getInstantAnswer(action.query)
                if (answer != null) responseGenerator.speechForSearchAnswer(answer, tone)
                else responseGenerator.speechForSearchNoAnswer(tone)
            }
            KateAction.Help -> responseGenerator.speechForHelp(tone)
            KateAction.Unknown -> responseGenerator.speechForUnknown(tone)
        }

        return Result(action, speech)
    }

    /** Direct fuzzy-match only - see class doc's KNOWN SIMPLIFICATION for what this deliberately doesn't do. */
    private suspend fun resolveContactFastPath(spokenName: String): Contact? {
        val contacts = contactsHelper.getAllContacts()
        if (contacts.isEmpty()) return null
        return contactsHelper.findBestMatch(spokenName, contacts)
    }
}
