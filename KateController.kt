package com.kate.assistant.features.device

// Convenience facade combining hardware + device control
class KateController(context: android.content.Context) {
    val hardware = KateHardwareController(context)
    val device   = KateDeviceController(context)
}
