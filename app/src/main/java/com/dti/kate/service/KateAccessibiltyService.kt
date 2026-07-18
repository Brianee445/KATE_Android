package com.dti.kate.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent

class KateAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "KateAccessibility"
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
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
