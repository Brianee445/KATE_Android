package com.dti.kate.service

import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.dti.kate.core.MessagingApp
import kotlinx.coroutines.delay

/**
 * Sends a message through WhatsApp or Messenger by driving their UI via
 * accessibility, since neither app exposes a public "send this exact
 * message to this exact contact" intent the way plain SMS does (that's
 * why DeviceControlManager.sendSms can just fire an Intent and be done).
 *
 * HONESTY ABOUT RELIABILITY: this is fundamentally more fragile than every
 * other accessibility action in this app. GoHome/lockScreen/etc. use
 * performGlobalAction(), a stable OS-level API. This instead walks each
 * app's live view hierarchy looking for specific view IDs and text - if
 * WhatsApp or Messenger ship a UI update that renames/restructures those
 * views, this breaks silently until the selectors below are updated to
 * match. There is no way to "fix this properly" within accessibility
 * automation - it's inherently coupled to UI internals of apps this
 * project doesn't control. Treat failures here as expected/routine, not
 * bugs to chase - the fallback to plain SMS (see KateCommandProcessor)
 * exists specifically because of this.
 *
 * View IDs below were captured against WhatsApp/Messenger builds current
 * as of this writing and WILL drift over time - if send rates drop, the
 * first thing to check is whether these IDs still exist by inspecting the
 * live app with Android Studio's Layout Inspector.
 */
class MessagingAppAutomator(private val service: KateAccessibilityService) {

    companion object {
        private const val TAG = "MessagingAutomator"
        private const val STEP_TIMEOUT_MS = 4000L
        private const val POLL_INTERVAL_MS = 150L
    }

    suspend fun send(app: MessagingApp, contactName: String, message: String): Boolean {
        return try {
            when (app) {
                MessagingApp.WHATSAPP -> sendViaWhatsApp(contactName, message)
                MessagingApp.MESSENGER -> sendViaMessenger(contactName, message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "send() failed for ${app.displayName}: ${e.message}")
            false
        }
    }

    // ========================================================================
    // WHATSAPP
    // ========================================================================
    // Flow: open app -> tap search icon -> type contact name -> tap first
    // result -> type message into compose field -> tap send button.
    private suspend fun sendViaWhatsApp(contactName: String, message: String): Boolean {
        if (!openApp(MessagingApp.WHATSAPP.packageName)) return false

        // WhatsApp's main screen search icon.
        val searchNode = waitForNodeById("com.whatsapp:id/menuitem_search")
            ?: waitForNodeByDescription("Search")
            ?: return false.also { Log.w(TAG, "WhatsApp: search icon not found") }
        searchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val searchInput = waitForNodeById("com.whatsapp:id/search_src_text")
            ?: return false.also { Log.w(TAG, "WhatsApp: search input not found") }
        setText(searchInput, contactName)
        delay(600) // let the results list actually filter before reading it

        // First result row in the filtered contact/chat list.
        val resultRow = waitForNodeById("com.whatsapp:id/contactpicker_row_name")
            ?: return false.also { Log.w(TAG, "WhatsApp: no matching contact row found") }
        clickNearestClickableAncestor(resultRow)

        val composeField = waitForNodeById("com.whatsapp:id/entry")
            ?: return false.also { Log.w(TAG, "WhatsApp: compose field not found") }
        setText(composeField, message)
        delay(300)

        val sendButton = waitForNodeById("com.whatsapp:id/send")
            ?: return false.also { Log.w(TAG, "WhatsApp: send button not found") }
        sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return true
    }

    // ========================================================================
    // MESSENGER
    // ========================================================================
    // Flow mirrors WhatsApp's - Messenger's view IDs are its own package,
    // structurally similar (search -> pick thread -> compose -> send).
    private suspend fun sendViaMessenger(contactName: String, message: String): Boolean {
        if (!openApp(MessagingApp.MESSENGER.packageName)) return false

        val searchNode = waitForNodeById("com.facebook.orca:id/search_src_text")
            ?: waitForNodeByDescription("Search Messenger")
            ?: return false.also { Log.w(TAG, "Messenger: search field not found") }
        searchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        setText(searchNode, contactName)
        delay(600)

        val resultRow = waitForNodeByDescription(contactName)
            ?: return false.also { Log.w(TAG, "Messenger: no matching contact row found") }
        clickNearestClickableAncestor(resultRow)

        val composeField = waitForNodeById("com.facebook.orca:id/message_input_text")
            ?: return false.also { Log.w(TAG, "Messenger: compose field not found") }
        setText(composeField, message)
        delay(300)

        val sendButton = waitForNodeById("com.facebook.orca:id/send_button")
            ?: return false.also { Log.w(TAG, "Messenger: send button not found") }
        sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return true
    }

    // ========================================================================
    // SHARED HELPERS
    // ========================================================================

    private fun openApp(packageName: String): Boolean {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        service.startActivity(intent)
        return true
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /** performAction(ACTION_CLICK) often needs to run on a parent view, not
     * the exact TextView node found by ID/description - list rows are
     * commonly a non-clickable label inside a clickable container. Walks up
     * the tree until it finds a node that reports itself clickable. */
    private fun clickNearestClickableAncestor(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 6) {
            if (current.isClickable) {
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            current = current.parent
            hops++
        }
        // Fell through without finding a clickable ancestor - last resort,
        // click the original node anyway in case it silently works despite
        // isClickable reporting false (some custom views misreport this).
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Polls the accessibility tree for a node matching [viewId], up to
     * STEP_TIMEOUT_MS - the UI these selectors target takes a moment to
     * render after each navigation/search step, so a single immediate
     * lookup would frequently miss nodes that appear 200-800ms later. */
    private suspend fun waitForNodeById(viewId: String): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val root = service.rootInActiveWindow
            val match = root?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
            if (match != null) return match
            delay(POLL_INTERVAL_MS)
        }
        return null
    }

    /** Same polling approach, matched by content description/text instead
     * of view ID - used where a stable view ID isn't known (search-icon
     * fallback) or where the match target is dynamic (a specific contact's
     * name in a results list). */
    private suspend fun waitForNodeByDescription(text: String): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val root = service.rootInActiveWindow
            val match = root?.let { findNodeByText(it, text) }
            if (match != null) return match
            delay(POLL_INTERVAL_MS)
        }
        return null
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val byText = root.findAccessibilityNodeInfosByText(text)
        return byText.firstOrNull {
            it.contentDescription?.contains(text, ignoreCase = true) == true ||
                it.text?.contains(text, ignoreCase = true) == true
        } ?: byText.firstOrNull()
    }
}
