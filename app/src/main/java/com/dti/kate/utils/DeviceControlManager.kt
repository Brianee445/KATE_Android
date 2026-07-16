package com.dti.kate.utils

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.dti.kate.core.Logger
import java.lang.reflect.Method

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
            // Get camera ID (usually "0" for back camera)
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return false
            
            currentCameraId = cameraId
            
            // Check if torch is supported
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val torchAvailable = characteristics.get(
                android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) ?: false
            
            if (!torchAvailable) {
                Logger.w(TAG, "Torch not available on this device")
                return false
            }
            
            // Toggle torch
            torchState = !torchState
            cameraManager.setTorchMode(cameraId, torchState)
            
            Logger.i(TAG, "Torch ${if (torchState) "ON" else "OFF"}")
            true
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to toggle torch: ${e.message}")
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
            // Clean phone number
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            
            if (cleanNumber.isEmpty()) {
                Logger.w(TAG, "Invalid phone number")
                return false
            }
            
            // Check if device can make calls
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!telephonyManager.isVoiceCapable) {
                    Logger.w(TAG, "Device not capable of making calls")
                    return false
                }
            }
            
            // Start call intent
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = android.net.Uri.parse("tel:$cleanNumber")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            // Check permission
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CALL_PHONE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                context.startActivity(intent)
                Logger.i(TAG, "Calling $cleanNumber")
                return true
            } else {
                Logger.w(TAG, "CALL_PHONE permission not granted")
                
                // Fallback to dialer (ACTION_DIAL)
                val dialIntent = Intent(Intent.ACTION_DIAL)
                dialIntent.data = android.net.Uri.parse("tel:$cleanNumber")
                dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(dialIntent)
                return true
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to make call: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 3. BLUETOOTH CONTROL
    // ========================================================================
    
    fun toggleBluetooth(): Boolean {
        return try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                ?: return false
            
            if (bluetoothAdapter.isEnabled) {
                bluetoothAdapter.disable()
                Logger.i(TAG, "Bluetooth OFF")
            } else {
                bluetoothAdapter.enable()
                Logger.i(TAG, "Bluetooth ON")
            }
            true
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to toggle Bluetooth: ${e.message}")
            false
        }
    }
    
    fun setBluetooth(on: Boolean): Boolean {
        return try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                ?: return false
            
            if (on && !bluetoothAdapter.isEnabled) {
                bluetoothAdapter.enable()
            } else if (!on && bluetoothAdapter.isEnabled) {
                bluetoothAdapter.disable()
            }
            true
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set Bluetooth: ${e.message}")
            false
        }
    }
    
    fun isBluetoothOn(): Boolean {
        return try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            bluetoothAdapter?.isEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    // ========================================================================
    // 4. WI-FI CONTROL
    // ========================================================================
    
    fun toggleWifi(): Boolean {
        return try {
            if (wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = false
                Logger.i(TAG, "Wi-Fi OFF")
            } else {
                wifiManager.isWifiEnabled = true
                Logger.i(TAG, "Wi-Fi ON")
            }
            true
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to toggle Wi-Fi: ${e.message}")
            false
        }
    }
    
    fun setWifi(on: Boolean): Boolean {
        return try {
            wifiManager.isWifiEnabled = on
            Logger.i(TAG, "Wi-Fi ${if (on) "ON" else "OFF"}")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set Wi-Fi: ${e.message}")
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
            Logger.i(TAG, "Volume set to $clampedLevel/$maxVolume")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set volume: ${e.message}")
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
            Logger.e(TAG, "Failed to increase volume: ${e.message}")
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
            Logger.e(TAG, "Failed to decrease volume: ${e.message}")
            false
        }
    }
    
    fun muteVolume(streamType: Int = AudioManager.STREAM_MUSIC): Boolean {
        return try {
            audioManager.setStreamMute(streamType, true)
            Logger.i(TAG, "Volume muted")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to mute volume: ${e.message}")
            false
        }
    }
    
    fun unmuteVolume(streamType: Int = AudioManager.STREAM_MUSIC): Boolean {
        return try {
            audioManager.setStreamMute(streamType, false)
            Logger.i(TAG, "Volume unmuted")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to unmute volume: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 6. SCREEN BRIGHTNESS
    // ========================================================================
    
    fun setBrightness(level: Int): Boolean {
        return try {
            val clampedLevel = level.coerceIn(0, 255)
            
            // Check if we can write settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(context)) {
                    Logger.w(TAG, "Cannot write system settings - need permission")
                    return false
                }
            }
            
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clampedLevel
            )
            
            Logger.i(TAG, "Brightness set to $clampedLevel/255")
            true
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set brightness: ${e.message}")
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
            
            // Broadcast the change
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            intent.putExtra("state", !isEnabled)
            context.sendBroadcast(intent)
            
            Logger.i(TAG, "Airplane mode ${if (isEnabled) "OFF" else "ON"}")
            true
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to toggle airplane mode: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 8. DO NOT DISTURB MODE
    // ========================================================================
    
    fun setDoNotDisturb(enabled: Boolean): Boolean {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
                as android.app.NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (enabled) {
                    notificationManager.setInterruptionFilter(
                        android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    )
                } else {
                    notificationManager.setInterruptionFilter(
                        android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
                Logger.i(TAG, "Do Not Disturb ${if (enabled) "ON" else "OFF"}")
                return true
            }
            false
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set DND: ${e.message}")
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
                intent.data = android.net.Uri.parse(it)
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Logger.i(TAG, "Intent executed: $action")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to execute intent: ${e.message}")
            false
        }
    }
}
