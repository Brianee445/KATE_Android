package com.dti.kate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.dti.kate.R
import com.dti.kate.core.KateResponseGenerator
import com.dti.kate.core.KateWakeSignal
import com.dti.kate.core.LocalSettingsStore
import com.dti.kate.core.toneFromSlider
import com.dti.kate.ui.KateActivity
import kotlin.math.sqrt
import java.util.Locale

class KateForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "kate_foreground_channel"
        private const val NOTIFICATION_ID = 2001

        // Shake detection
        private const val SHAKE_THRESHOLD = 15f
        private const val SHAKE_COOLDOWN_MS = 2000L

        // Raise detection (simple heuristic - not perfect, but a reasonable
        // best-effort: flat/down orientation transitioning quickly to a
        // near-vertical, screen-facing-user orientation)
        private const val RAISE_COOLDOWN_MS = 2000L
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingSpeech: String? = null
    private lateinit var localSettings: LocalSettingsStore
    private val responseGenerator = KateResponseGenerator()

    private var sensorManager: SensorManager? = null
    private var lastShakeTime = 0L
    private var lastRaiseTime = 0L
    private var lastZ = 0f
    private var hasLastZ = false

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            if (localSettings.getShakeEnabled()) {
                checkShake(x, y, z)
            }
            if (localSettings.getRaiseToWakeEnabled()) {
                checkRaise(z)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun checkShake(x: Float, y: Float, z: Float) {
        val gravity = 9.8f
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat() - gravity
        if (magnitude > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now
                onWakeGestureDetected()
            }
        }
    }

    private fun checkRaise(z: Float) {
        if (!hasLastZ) {
            lastZ = z
            hasLastZ = true
            return
        }
        // Device resting closer to flat (z near +/-9.8) rapidly moving
        // toward upright/vertical (z near 0) suggests being lifted to
        // face level. Best-effort heuristic, not perfectly reliable on
        // every device.
        val wasFlat = kotlin.math.abs(lastZ) > 7f
        val nowUpright = kotlin.math.abs(z) < 4f
        if (wasFlat && nowUpright) {
            val now = System.currentTimeMillis()
            if (now - lastRaiseTime > RAISE_COOLDOWN_MS) {
                lastRaiseTime = now
                onWakeGestureDetected()
            }
        }
        lastZ = z
    }

    private fun onWakeGestureDetected() {
        // Bring Kate to the foreground, then signal HomeScreen to start
        // listening once it's visible.
        val launchIntent = Intent(this, KateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(launchIntent)
        KateWakeSignal.trigger()
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val tone = toneFromSlider(localSettings.getToneLevel())
            val message = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> responseGenerator.speechForChargerConnected(tone)
                Intent.ACTION_POWER_DISCONNECTED -> responseGenerator.speechForChargerDisconnected(tone)
                else -> null
            }
            message?.let {
                if (isTtsReady) speak(it) else pendingSpeech = it
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        localSettings = LocalSettingsStore(this)
        createNotificationChannel()

        tts = TextToSpeech(this) { status ->
            isTtsReady = (status == TextToSpeech.SUCCESS)
            if (isTtsReady) {
                pendingSpeech?.let { queued ->
                    pendingSpeech = null
                    speak(queued)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager?.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(powerReceiver)
        sensorManager?.unregisterListener(sensorListener)
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun speak(text: String) {
        val tone = toneFromSlider(localSettings.getToneLevel())
        val rate = when (tone) {
            com.dti.kate.core.KateTone.PROFESSIONAL -> 0.95f
            com.dti.kate.core.KateTone.BALANCED -> 1.0f
            com.dti.kate.core.KateTone.SASSY -> 1.05f
        }
        tts?.setSpeechRate(rate)
        tts?.language = Locale.US
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kate_charging_event")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kate Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kate is listening in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kate Assistant")
            .setContentText("Listening in the background")
            .setSmallIcon(R.drawable.ic_kate_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
