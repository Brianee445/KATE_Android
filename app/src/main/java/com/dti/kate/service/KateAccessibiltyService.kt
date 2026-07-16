// app/src/main/java/com/dti/kate/service/KateAccessibilityService.kt

package com.dti.kate.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.view.accessibility.AccessibilityEvent
import com.dti.kate.core.Logger
import com.dti.kate.utils.DeviceControlManager

class KateAccessibilityService : AccessibilityService() {
    
    private lateinit var deviceControl: DeviceControlManager
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        deviceControl = DeviceControlManager(this)
        Logger.i(TAG, "Accessibility service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
    }
    
    override fun onInterrupt() {
        Logger.w(TAG, "Accessibility service interrupted")
    }
    
    // ========================================================================
    // DEVICE CONTROL METHODS (called from Intent Engine)
    // ========================================================================
    
    fun toggleTorch(): Boolean = deviceControl.toggleTorch()
    fun makeCall(phoneNumber: String): Boolean = deviceControl.makeCall(phoneNumber)
    fun toggleBluetooth(): Boolean = deviceControl.toggleBluetooth()
    fun toggleWifi(): Boolean = deviceControl.toggleWifi()
    fun setVolume(level: Int): Boolean = deviceControl.setVolume(level)
    fun toggleAirplaneMode(): Boolean = deviceControl.toggleAirplaneMode()
    fun setDoNotDisturb(enabled: Boolean): Boolean = deviceControl.setDoNotDisturb(enabled)
    
    // ========================================================================
    // APP OPENING (existing)
    // ========================================================================
    
    fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                Logger.i(TAG, "Opened app: $packageName")
                true
            } else {
                Logger.w(TAG, "App not found: $packageName")
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to open app: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // TYPING (existing)
    // ========================================================================
    
    fun typeText(text: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val focusable = root.findAccessibilityNodeInfosByViewId(
                "android:id/input"
            ).firstOrNull() ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            
            if (focusable != null) {
                // Clear existing text
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                focusable.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    arguments
                )
                Logger.i(TAG, "Typed: $text")
                true
            } else {
                Logger.w(TAG, "No input field found")
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to type: ${e.message}")
            false
        }
    }
    
    companion object {
        private const val TAG = "KateAccessibility"
    }
}
