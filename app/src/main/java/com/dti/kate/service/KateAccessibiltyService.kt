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
}
