package com.dti.kate.utils

import android.Manifest
import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dti.kate.core.Logger
import java.text.SimpleDateFormat
import java.util.*

class DeviceControlManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceControlManager"
        
        // Calendar constants
        private const val CALENDAR_ACCOUNT_NAME = "Kate Assistant"
        private const val CALENDAR_ACCOUNT_TYPE = "com.dti.kate"
    }
    
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private var torchState = false
    private var currentCameraId: String? = null
    
    // ========================================================================
    // 1. MAKE PHONE CALLS
    // ========================================================================
    
    fun makeCall(phoneNumber: String): Boolean {
        try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            
            if (cleanNumber.isEmpty()) {
                Logger.w(TAG, "Invalid phone number")
                return false
            }
            
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!telephonyManager.isVoiceCapable) {
                    Logger.w(TAG, "Device not capable of making calls")
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
                Logger.i(TAG, "Calling $cleanNumber")
                return true
            } else {
                // Fallback to dialer
                val dialIntent = Intent(Intent.ACTION_DIAL)
                dialIntent.data = Uri.parse("tel:$cleanNumber")
                dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(dialIntent)
                return true
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to make call: ${e.message}")
            return false
        }
    }
    
    // ========================================================================
    // 2. SEND MESSAGES (SMS)
    // ========================================================================
    
    fun sendMessage(phoneNumber: String, message: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            
            if (cleanNumber.isEmpty()) {
                Logger.w(TAG, "Invalid phone number")
                return false
            }
            
            if (message.isEmpty()) {
                Logger.w(TAG, "Empty message")
                return false
            }
            
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(cleanNumber, null, message, null, null)
                Logger.i(TAG, "Message sent to $cleanNumber")
                return true
            } else {
                // Fallback to SMS intent
                val intent = Intent(Intent.ACTION_SENDTO)
                intent.data = Uri.parse("smsto:$cleanNumber")
                intent.putExtra("sms_body", message)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send message: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 3. SEND MESSAGE TO CONTACT (by name)
    // ========================================================================
    
    fun sendMessageToContact(contactName: String, message: String): Boolean {
        return try {
            val phoneNumber = getContactPhoneNumber(contactName)
            if (phoneNumber.isEmpty()) {
                Logger.w(TAG, "Contact not found: $contactName")
                return false
            }
            sendMessage(phoneNumber, message)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send message to contact: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 4. GET CONTACT PHONE NUMBER
    // ========================================================================
    
    fun getContactPhoneNumber(contactName: String): String {
        return try {
            val contentResolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")
            
            val cursor = contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    return it.getString(numberIndex)
                }
            }
            ""
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get contact: ${e.message}")
            ""
        }
    }
    
    // ========================================================================
    // 5. ADD CALENDAR EVENT
    // ========================================================================
    
    fun addCalendarEvent(title: String, description: String, startTime: Long, endTime: Long): Boolean {
        return try {
            // Check if we have a calendar
            val calendarId = getOrCreateCalendar()
            if (calendarId == -1L) {
                Logger.w(TAG, "No calendar found")
                return false
            }
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }
            
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            
            if (uri != null) {
                // Add reminder
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, uri.lastPathSegment?.toLong() ?: 0)
                    put(CalendarContract.Reminders.MINUTES, 10)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                
                Logger.i(TAG, "Calendar event added: $title")
                return true
            }
            
            false
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to add calendar event: ${e.message}")
            false
        }
    }
    
    private fun getOrCreateCalendar(): Long {
        return try {
            val contentResolver = context.contentResolver
            val uri = CalendarContract.Calendars.CONTENT_URI
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.NAME
            )
            val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ?"
            val selectionArgs = arrayOf(CALENDAR_ACCOUNT_NAME)
            
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                    return cursor.getLong(idIndex)
                }
            } finally {
                cursor?.close()
            }
            
            // Create calendar if it doesn't exist
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
                put(CalendarContract.Calendars.NAME, "Kate Assistant")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Kate Assistant")
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0x7C3AED)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDAR_ACCOUNT_NAME)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            }
            
            val newUri = contentResolver.insert(uri, values)
            newUri?.lastPathSegment?.toLong() ?: -1L
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get calendar: ${e.message}")
            -1L
        }
    }
    
    // ========================================================================
    // 6. SET REMINDER
    // ========================================================================
    
    fun setReminder(title: String, timeInMillis: Long): Boolean {
        return try {
            // Use Calendar event as reminder
            val endTime = timeInMillis + 30 * 60 * 1000 // 30 minutes duration
            addCalendarEvent("REMINDER: $title", "", timeInMillis, endTime)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set reminder: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 7. SET TIMER
    // ========================================================================
    
    fun setTimer(minutes: Int): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use system timer
                val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                intent.putExtra(AlarmClock.EXTRA_LENGTH, minutes)
                intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Logger.i(TAG, "Timer set for $minutes minutes")
                return true
            } else {
                // Fallback: create a notification with countdown
                createTimerNotification(minutes)
                return true
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set timer: ${e.message}")
            false
        }
    }
    
    private fun createTimerNotification(minutes: Int) {
        val channelId = "kate_timer_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kate Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timer notifications from Kate"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = Notification.Builder(context, channelId)
            .setContentTitle("⏱️ Timer Set")
            .setContentText("Timer for $minutes minutes")
            .setSmallIcon(android.R.drawable.ic_menu_alarm)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(1001, notification)
    }
    
    // ========================================================================
    // 8. CREATE NOTE
    // ========================================================================
    
    fun createNote(title: String, content: String): Boolean {
        return try {
            // Try Google Keep via intent
            val intent = Intent(Intent.ACTION_INSERT)
            intent.type = "vnd.android.cursor.item/vnd.google.note"
            intent.putExtra(Intent.EXTRA_TITLE, title)
            intent.putExtra(Intent.EXTRA_TEXT, content)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Logger.i(TAG, "Note created: $title")
                return true
            } else {
                // Fallback: save to a local file
                saveNoteToFile(title, content)
                return true
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create note: ${e.message}")
            false
        }
    }
    
    private fun saveNoteToFile(title: String, content: String) {
        try {
            val fileName = "${System.currentTimeMillis()}_$title.txt"
            val file = java.io.File(context.filesDir, "notes/$fileName")
            file.parentFile?.mkdirs()
            file.writeText("$title\n\n$content")
            Logger.i(TAG, "Note saved to file: $fileName")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save note: ${e.message}")
        }
    }
    
    // ========================================================================
    // 9. TORCH / FLASHLIGHT
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
                Logger.w(TAG, "Torch not available")
                return false
            }
            
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
    // 10. BLUETOOTH
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
    
    // ========================================================================
    // 11. WI-FI
    // ========================================================================
    
    fun toggleWifi(): Boolean {
        return try {
            wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
            Logger.i(TAG, "Wi-Fi ${if (wifiManager.isWifiEnabled) "ON" else "OFF"}")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to toggle Wi-Fi: ${e.message}")
            false
        }
    }
    
    // ========================================================================
    // 12. VOLUME
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
    
    // ========================================================================
    // 13. DO NOT DISTURB
    // ========================================================================
    
    fun setDoNotDisturb(enabled: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CO
