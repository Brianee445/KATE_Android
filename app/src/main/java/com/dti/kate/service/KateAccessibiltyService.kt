package com.dti.kate.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent

class KateAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KateAccessibility"

        // Set on connect/destroy so other components (Compose screens) can
        // reach the running service without binding to it directly.
        var instance: KateAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val expectedComponent = "${context.packageName}/${KateAccessibilityService::class.java.name}"
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            return splitter.any { it.equals(expectedComponent, ignoreCase = true) }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    // ========================================================================
    // APP OPENING
    // ========================================================================

    fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                Log.i(TAG, "Opened app: $packageName")
                true
            } else {
                Log.w(TAG, "App not found: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: ${e.message}")
            false
        }
    }

    // ========================================================================
    // TYPING
    // ========================================================================

    fun typeText(text: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val focusable = root.findAccessibilityNodeInfosByViewId(
                "android:id/input"
            ).firstOrNull() ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

            if (focusable != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                focusable.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    arguments
                )
                Log.i(TAG, "Typed: $text")
                true
            } else {
                Log.w(TAG, "No input field found")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to type: ${e.message}")
            false
        }
    }

    // ========================================================================
    // GLOBAL ACTIONS (home / back / recents / lock / screenshot)
    // ========================================================================
    // These use performGlobalAction(), which works regardless of which app
    // is in the foreground - no target-app UI lookup needed, unlike
    // typeText() above. That also means they can't fail the way typeText
    // can ("no input field found") - performGlobalAction() itself returns
    // a plain boolean for "did the system accept this action", which is
    // what each wrapper below returns directly.

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun showRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** Android 9+ (API 28). On older devices performGlobalAction() itself
     * returns false for this action code, so callers get an honest failure
     * rather than a crash - no separate SDK_INT check needed here. */
    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    /** Android 9+ (API 28), same story as lockScreen(). Also requires
     * android:canTakeScreenshot="true" in accessibility_config.xml - see
     * that file's comment. */
    fun takeScreenshot(): Boolean = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    // ========================================================================
    // TARGETED MESSAGING (WhatsApp / Messenger)
    // ========================================================================
    private val messagingAutomator by lazy { MessagingAppAutomator(this) }

    suspend fun sendViaMessagingApp(
        app: com.dti.kate.core.MessagingApp,
        contactName: String,
        message: String,
    ): Boolean = messagingAutomator.send(app, contactName, message)

    // ========================================================================
    // DECLINE INCOMING CALL
    // ========================================================================
    // No stable resource ID works across dialers (AOSP Phone, Pixel Dialer,
    // Samsung's own dialer all use different package names/IDs for this
    // button), so this matches by content description/text instead, tried
    // against several common labels. This is the least reliable action in
    // the whole app - see DeviceControlManager.declineCall's doc comment
    // for why there's no better option. A miss here (button not found)
    // returns false rather than tapping something wrong.
    fun declineCall(): Boolean {
        val root = rootInActiveWindow ?: return false
        val labels = listOf("Decline", "Reject", "Dismiss", "End call", "Hang up")
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            val match = nodes.firstOrNull {
                it.isClickable || it.parent?.isClickable == true
            }
            if (match != null) {
                val target = if (match.isClickable) match else match.parent
                target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Declined call via label: $label")
                return true
            }
        }
        Log.w(TAG, "declineCall: no matching button found on screen")
        return false
    }
}
