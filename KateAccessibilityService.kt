package com.kate.assistant.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.kate.assistant.bridge.KateEventBus
import com.kate.assistant.bridge.KateEvent

class KateAccessibilityService : AccessibilityService() {

    private var lastPackage: String? = null
    private var lastEventTime: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Ignore system noise
        if (pkg == "com.android.systemui" ||
            pkg == "com.google.android.permissioncontroller") {
            return
        }

        val now = System.currentTimeMillis()

        //  Debounce duplicate triggers
        if (pkg == lastPackage && (now - lastEventTime) < 1500) {
            return
        }

        lastPackage = pkg
        lastEventTime = now

        Log.d("KateAccessibility", "App opened: $pkg")

        //  Emit clean event
        KateEventBus.emit(
            KateEvent.AppOpened(pkg)
        )
    }

    override fun onInterrupt() {
        Log.e("KateAccessibility", "Interrupted")
    }
}