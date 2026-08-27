package com.dti.kate.utils

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

class DeviceControlManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceControlManager"
    }
    
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    private var torchState = false
    private var currentCameraId: String? = null
    
    // ========================================================================
    // 1. TORCH / FLASHLIGHT
    // ========================================================================
    
    fun toggleTorch(): Boolean {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return false
            currentCameraId = cameraId
            
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val torchAvailable = characteristics.get(
                android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) ?: false
            
            if (!torchAvailable) {
                Log.w(TAG, "Torch not available on this device")
                return false
            }
            
            torchState = !torchState
            cameraManager.setTorchMode(cameraId, torchState)
            
            Log.i(TAG, "Torch ${if (torchState) "ON" else "OFF"}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle torch: ${e.message}")
            false
        }
    }
    
    fun setTorch(on: Boolean): Boolean {
        if (torchState == on) return true
        return toggleTorch()
    }
    
    fun isTorchOn(): Boolean = torchState
    
    // ========================================================================
    // 2. MAKE PHONE CALLS
    // ========================================================================
    
    fun makeCall(phoneNumber: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            
            if (cleanNumber.isEmpty()) {
                Log.w(TAG, "Invalid phone number")
                return false
            }
            
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!telephonyManager.isVoiceCapable) {
                    Log.w(TAG, "Device not capable of making calls")
                    return false
                }
            }
            
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$cleanNumber")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                context.startActivity(intent)
                Log.i(TAG, "Calling $cleanNumber")
                true
            } else {
                Log.w(TAG, "CALL_PHONE permission not granted, opening dialer")
                val dialIntent = Intent(Intent.ACTION_DIAL)
                dialIntent.data = Uri.parse("tel:$cleanNumber")
                dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(dialIntent)
                true
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call: ${e.message}")
            false
        }
    }

    /** Sends an SMS directly if SEND_SMS is granted, else opens the messaging app pre-filled. */
    fun sendSms(phoneNumber: String, body: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            if (cleanNumber.isEmpty()) {
                Log.w(TAG, "Invalid phone number for SMS")
                return false
            }

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
                    ?: android.telephony.SmsManager.getDefault()
                smsManager.sendTextMessage(cleanNumber, null, body, null, null)
                Log.i(TAG, "SMS sent to $cleanNumber")
                true
            } else {
                Log.w(TAG, "SEND_SMS permission not granted, opening messaging app")
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$cleanNumber")
                    putExtra("sms_body", body)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS: ${e.message}")
            false
        }
    }

    /** Checks if the app is exempt from battery optimization (needed for reliable background wake-gesture detection). */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Launches the system dialog to request battery optimization exemption. No-op if already exempt. */
    fun requestIgnoreBatteryOptimizations() {
        if (isIgnoringBatteryOptimizations()) return
        try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request battery optimization exemption: ${e.message}")
        }
    }

    /**
     * Stock ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is necessary but NOT
     * sufficient on Transsion's HiOS/XOS skin (Tecno/Infinix/itel) - it layers
     * its own "Autostart"/"Protected Apps" permission on top of stock
     * Doze, and a foreground service + sensor listener still gets killed
     * without it even when battery-optimization-exempt. There's no public
     * AOSP API for this - only vendor-specific settings screens, reached by
     * deep-linking their package/activity directly. This is the same
     * "Doze throttling of the wake-gesture sensor listener" behavior noted
     * in KateForegroundService.onTaskRemoved.
     *
     * Best-effort: tries each known Transsion-family autostart screen in
     * turn, falls back to the app's own battery-usage detail settings page
     * (where the user can usually find an equivalent toggle manually) if
     * none resolve. Returns true if a screen was actually launched.
     */
    fun requestAutostartPermission(): Boolean {
        val candidates = listOf(
            Intent().setClassName("com.transsion.phonemanager", "com.transsion.phonemanager.MainActivity"),
            Intent().setClassName("com.itel.autobootmanager", "com.itel.autobootmanager.activity.AutoBootMainActivity"),
            Intent().setClassName("com.transsion.batterylab", "com.transsion.batterylab.ui.activity.SmartLimitActivity"),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
        )

        for (intent in candidates) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "Launched autostart settings: ${intent.component}")
                    return true
                }
            } catch (e: Exception) {
                // Try the next candidate - these are unofficial vendor
                // screens that can legitimately not exist on a given
                // firmware build.
            }
        }

        Log.w(TAG, "No known autostart settings screen resolved, falling back to app details")
        return try {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details fallback: ${e.message}")
            false
        }
    }

    /** True on Transsion-family devices (Tecno/Infinix/itel - HiOS/XOS) where requestAutostartPermission is relevant. Other OEMs' equivalents (MIUI etc.) are attempted opportunistically above but not gated behind this. */
    fun isTranssionDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return listOf("transsion", "tecno", "infinix", "itel").any { it in manufacturer || it in brand }
    }

    // ========================================================================
    // 3. BLUETOOTH CONTROL
    // ========================================================================
    
    fun toggleBluetooth(): Boolean {
        return try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: return false
            
            if (bluetoothAdapter.isEnabled) {
                bluetoothAdapter.disable()
                Log.i(TAG, "Bluetooth OFF")
            } else {
                bluetoothAdapter.enable()
                Log.i(TAG, "Bluetooth ON")
            }
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle Bluetooth: ${e.message}")
            false
        }
    }
    
    fun setBluetooth(on: Boolean): Boolean {
        return try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: return false
            
            if (on && !bluetoothAdapter.isEnabled) {
                bluetoothAdapter.enable()
            } else if (!on && bluetoothAdapter.isEnabled) {
                bluetoothAdapter.disable()
            }
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Bluetooth: ${e.message}")
            false
        }
    }
    
    fun isBluetoothOn(): Boolean {
        return try {
            BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    // ========================================================================
    // 4. WI-FI CONTROL
    // ========================================================================
    
    fun toggleWifi(): Boolean {
        return try {
            wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
            Log.i(TAG, "Wi-Fi ${if (wifiManager.isWifiEnabled) "ON" else "OFF"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle Wi-Fi: ${e.message}")
            false
        }
    }
    
    fun setWifi(on: Boolean): Boolean {
        return try {
            wifiManager.isWifiEnabled = on
            Log.i(TAG, "Wi-Fi ${if (on) "ON" else "OFF"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Wi-Fi: ${e.message}")
            false
        }
    }
    
    fun isWifiOn(): Boolean = wifiManager.isWifiEnabled
    
    // ========================================================================
    // 5. VOLUME CONTROL
    // ========================================================================
    
    fun setVolume(level: Int, streamType: Int = AudioManager.STREAM_MUSIC): Boolean {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(streamType)
            val clampedLevel = level.coerceIn(0, maxVolume)
            audioManager.setStreamVolume(streamType, clampedLevel, AudioManager.FLAG_SHOW_UI)
            Log.i(TAG, "Volume set to $clampedLevel/$maxVolume")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume: ${e.message}")
            false
        }
    }
    
    fun increaseVolume(amount: Int = 1): Boolean {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                amount,
                AudioManager.FLAG_SHOW_UI
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to increase volume: ${e.message}")
            false
        }
    }
    
    fun decreaseVolume(amount: Int = 1): Boolean {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                -amount,
                AudioManager.FLAG_SHOW_UI
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrease volume: ${e.message}")
            false
        }
    }
    
    fun muteVolume(streamType: Int = AudioManager.STREAM_MUSIC): Boolean {
        return try {
            audioManager.setStreamMute(streamType, true)
            Log.i(TAG, "Volume muted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mute volume: ${e.message}")
            false
        }
    }
    
    fun unmuteVolume(streamType: Int = AudioManager.STREAM_MUSIC): Boolean {
        return try {
            audioManager.setStreamMute(streamType, false)
            Log.i(TAG, "Volume unmuted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unmute volume: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 6. SCREEN BRIGHTNESS
    // ========================================================================
    
    fun setBrightness(level: Int): Boolean {
        return try {
            val clampedLevel = level.coerceIn(0, 255)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(context)) {
                    Log.w(TAG, "Cannot write system settings - need permission")
                    return false
                }
            }
            
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clampedLevel
            )
            
            Log.i(TAG, "Brightness set to $clampedLevel/255")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness: ${e.message}")
            false
        }
    }
    
    fun getBrightness(): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
        } catch (e: Exception) {
            128
        }
    }
    
    // ========================================================================
    // 7. AIRPLANE MODE
    // ========================================================================
    
    fun toggleAirplaneMode(): Boolean {
        return try {
            val isEnabled = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
            
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (isEnabled) 0 else 1
            )
            
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            intent.putExtra("state", !isEnabled)
            context.sendBroadcast(intent)
            
            Log.i(TAG, "Airplane mode ${if (isEnabled) "OFF" else "ON"}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle airplane mode: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 8. DO NOT DISTURB MODE
    // ========================================================================
    
    fun setDoNotDisturb(enabled: Boolean): Boolean {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
                as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (enabled) {
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    )
                } else {
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
                Log.i(TAG, "Do Not Disturb ${if (enabled) "ON" else "OFF"}")
                true
            } else {
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set DND: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 9. INTENT EXECUTOR (for Intent-based actions)
    // ========================================================================
    
    fun executeIntent(action: String, data: String? = null): Boolean {
        return try {
            val intent = Intent(action)
            data?.let {
                intent.data = Uri.parse(it)
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.i(TAG, "Intent executed: $action")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute intent: ${e.message}")
            false
        }
    }

    // ========================================================================
    // 10. GLOBAL DEVICE ACTIONS (home / back / recents / lock / screenshot)
    // ========================================================================
    // All of these require the accessibility service to be running - there's
    // no non-accessibility way to do a screen-independent "go home" or
    // "lock the screen" from a regular app. If the user hasn't granted the
    // accessibility permission yet, every one of these returns false rather
    // than throwing, same failure shape as the rest of this class, so
    // KateCommandProcessor can give a single consistent "I need accessibility
    // permission for that" response (see speechForAccessibilityRequired).

    private fun accessibilityServiceOrNull() = com.dti.kate.service.KateAccessibilityService.instance

    fun goHome(): Boolean = accessibilityServiceOrNull()?.goHome() ?: run {
        Log.w(TAG, "goHome failed - accessibility service not connected")
        false
    }

    fun goBack(): Boolean = accessibilityServiceOrNull()?.goBack() ?: run {
        Log.w(TAG, "goBack failed - accessibility service not connected")
        false
    }

    fun showRecentApps(): Boolean = accessibilityServiceOrNull()?.showRecents() ?: run {
        Log.w(TAG, "showRecentApps failed - accessibility service not connected")
        false
    }

    /** Android 9+ (API 28) - the underlying performGlobalAction() call
     * itself returns false below that, so no extra SDK_INT check needed. */
    fun lockScreen(): Boolean = accessibilityServiceOrNull()?.lockScreen() ?: run {
        Log.w(TAG, "lockScreen failed - accessibility service not connected")
        false
    }

    /** Android 9+ (API 28), same as lockScreen(). Also needs
     * canTakeScreenshot="true" in accessibility_config.xml (already set). */
    fun takeScreenshot(): Boolean = accessibilityServiceOrNull()?.takeScreenshot() ?: run {
        Log.w(TAG, "takeScreenshot failed - accessibility service not connected")
        false
    }

    /** Whether the accessibility service is actually running right now -
     * distinct from KateAccessibilityService.isEnabled(context), which only
     * checks the system setting. A user can have it enabled in Settings but
     * the service process not yet be connected (cold start race) - this
     * checks the live instance, which is what actually matters for whether
     * goHome() etc. will work this instant. */
    fun isAccessibilityServiceRunning(): Boolean = accessibilityServiceOrNull() != null

    // ========================================================================
    // 11. TARGETED MESSAGING (WhatsApp / Messenger via accessibility)
    // ========================================================================
    /** See MessagingAppAutomator's doc comment for the reliability caveat -
     * this is meaningfully more fragile than every other method in this
     * class, since it drives a third-party app's UI rather than calling a
     * stable Android API. Returns false (never throws) on any failure,
     * same contract as the rest of this class, so KateCommandProcessor's
     * SMS-fallback logic (see that file) can treat "app-targeted send
     * failed" uniformly with every other failure case here. */
    suspend fun sendViaMessagingApp(
        app: com.dti.kate.core.MessagingApp,
        contactName: String,
        message: String,
    ): Boolean = accessibilityServiceOrNull()?.sendViaMessagingApp(app, contactName, message) ?: run {
        Log.w(TAG, "sendViaMessagingApp failed - accessibility service not connected")
        false
    }

    // ========================================================================
    // 12. IN-CALL VOICE ACTIONS (answer / decline)
    // ========================================================================
    // Answer uses TelecomManager.acceptRingingCall() - a real public API
    // (API 28+) gated by ANSWER_PHONE_CALLS, so this is as solid as
    // lockScreen()/takeScreenshot() above.
    //
    // Decline has NO equivalent public API - Android intentionally does
    // not let a regular app end/reject a call unless it IS the active
    // default dialer (a much bigger commitment: becoming the system dialer
    // changes how every call on the device is handled, not something to
    // take on for one voice command). The old ITelephony-internal-API
    // reflection hack some tutorials use reaches into non-public APIs and
    // is exactly the kind of thing Play review flags/rejects, so it's not
    // used here. Decline is instead attempted via accessibility, finding
    // and tapping the on-screen decline button - same fragility profile as
    // MessagingAppAutomator (couples to the phone/dialer app's UI, which
    // varies by OEM/dialer and can change between updates). If it fails,
    // callers get an honest "couldn't do that" rather than a fake success.

    fun answerCall(): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "answerCall failed - ANSWER_PHONE_CALLS not granted")
                return false
            }
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                ?: return false
            telecomManager.acceptRingingCall()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to answer call: ${e.message}")
            false
        }
    }

    /** See class doc above - this is accessibility-driven UI automation,
     * not a stable platform API, because none exists for this action. */
    fun declineCall(): Boolean = accessibilityServiceOrNull()?.declineCall() ?: run {
        Log.w(TAG, "declineCall failed - accessibility service not connected")
        false
    }
}
