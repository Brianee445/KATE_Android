package com.dti.kate.ui

import android.app.Activity
import android.os.Bundle
import com.dti.kate.ui.overlay.KateOverlayService

/**
 * Catches Android's system assist gesture (long-press home, corner-swipe
 * on gesture nav, or whatever a given OEM maps to "assistant") once Kate
 * is selected as the device's Digital Assistant app in system settings.
 *
 * Per Android's own ROLE_ASSISTANT qualification rules, an app becomes
 * eligible for that role by EITHER implementing a full VoiceInteractionService
 * (the heavyweight framework real assistants like Google Assistant use -
 * its own hotword/session-UI system) OR simply having an activity that
 * handles ACTION_ASSIST. Kate already has a complete listen -> process ->
 * speak cycle via the overlay, so there's no reason to build a second,
 * parallel assistant framework - this activity is purely a trampoline: it
 * exists ONLY to satisfy the ACTION_ASSIST qualification path and hand off
 * immediately to the overlay that already does everything else.
 *
 * Theme.NoDisplay (set in the manifest) requires finish() to be called
 * before onResume() completes, or the system throws a
 * WindowManager$BadTokenException - activating the overlay is a simple
 * synchronous static call, so finishing immediately after is safe.
 *
 * Being selected as the system assistant does NOT bypass this - Android
 * requires the user to explicitly pick Kate via the RoleManager role
 * request flow (see SettingsScreen's "Set Kate as your Assistant" action)
 * or manually via Settings > Apps > Default apps > Digital assistant app.
 * Nothing here can or should skip that explicit user step.
 */
class KateAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KateOverlayService.activate(applicationContext)
        finish()
    }
}
