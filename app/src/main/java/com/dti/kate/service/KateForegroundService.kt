package com.dti.kate.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dti.kate.R
import com.dti.kate.core.KateResponseGenerator
import com.dti.kate.core.KateTtsEngine
import com.dti.kate.core.KateWakeSignal
import com.dti.kate.core.LocalSettingsStore
import com.dti.kate.core.MicArbiter
import com.dti.kate.core.toneFromSlider
import com.dti.kate.ui.KateActivity
import com.dti.kate.wakeword.MelSpectrogram
import com.dti.kate.wakeword.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class KateForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "kate_foreground_channel"
        private const val NOTIFICATION_ID = 2001

        // Shake detection
        // Threshold is empirical, not derived from any spec - if this
        // still doesn't feel right, walking/handling noise on most
        // devices sits in the 2-8 range on this metric, so there's room
        // to lower further (e.g. to 10f) before false-triggering becomes
        // a real risk.
        private const val SHAKE_THRESHOLD = 12f
        private const val SHAKE_COOLDOWN_MS = 2000L

        // Raise detection (simple heuristic - not perfect, but a reasonable
        // best-effort: flat/down orientation transitioning quickly to a
        // near-vertical, screen-facing-user orientation)
        private const val RAISE_COOLDOWN_MS = 2000L
    }

    private lateinit var ttsEngine: KateTtsEngine
    private var isTtsReady = false
    private var pendingSpeech: String? = null
    private lateinit var localSettings: LocalSettingsStore
    private val responseGenerator = KateResponseGenerator()

    private var sensorManager: SensorManager? = null
    private var lastShakeTime = 0L
    private var lastRaiseTime = 0L
    private var lastZ = 0f
    private var hasLastZ = false

    // --- Wake word ("Hey Kate") ---
    // Runs a small always-on TFLite classifier over a continuous low-duty
    // AudioRecord, separate from HomeScreen's AudioCapture (which is
    // scoped to an active command-listening session). Paused whenever
    // HomeScreen owns the mic - see MicArbiter's class doc for why two
    // concurrent AudioRecord instances is a bad idea across OEMs.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeWordDetector: WakeWordDetector? = null
    private var wakeWordAudioRecord: AudioRecord? = null
    private var wakeWordJob: Job? = null

    // Reacts to the "Hey Kate" toggle in Settings while the service is
    // already running - without this, flipping it off wouldn't take effect
    // until the next time HomeScreen starts/stops a capture session (which
    // is what otherwise re-checks localSettings.getWakeWordEnabled()).
    private val settingsChangeListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "wake_word_enabled") {
                val enabled = prefs.getBoolean(key, true)
                if (enabled && !MicArbiter.appIsCapturing.value) {
                    startWakeWordListening()
                } else if (!enabled) {
                    stopWakeWordListening()
                }
            }
        }

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
        // Fresh installs (never completed sign-in/onboarding) have nothing
        // for the overlay to act on yet - only that case still opens the
        // app. Every other trigger (shake/raise/wake word) now stays as
        // the floating bubble and never opens KateActivity - see
        // KateOverlayService's class doc for why this used to always open
        // the app regardless.
        val isAuthenticated = com.dti.kate.repository.Repository(applicationContext).isAuthenticated()
        if (!isAuthenticated) {
            val launchIntent = Intent(this, KateActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(launchIntent)
            KateWakeSignal.trigger()
            return
        }

        if (!android.provider.Settings.canDrawOverlays(this)) {
            // No overlay permission - fall back to the old behavior rather
            // than silently doing nothing on a gesture the user just
            // triggered on purpose.
            val launchIntent = Intent(this, KateActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(launchIntent)
            KateWakeSignal.trigger()
            return
        }

        com.dti.kate.ui.overlay.KateOverlayService.activate(applicationContext)
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

    // Batch 5: announces incoming callers by name. Fires on the standard
    // PHONE_STATE broadcast rather than TelephonyCallback/PhoneStateListener,
    // since a plain registered BroadcastReceiver fits this service's
    // existing pattern (see powerReceiver above) without needing a second
    // listener lifecycle to manage. Caller name comes from ContactsHelper's
    // existing number->name matching - no READ_CALL_LOG permission needed
    // for this, since ContactsHelper already reads READ_CONTACTS-scoped
    // data. An unknown number (not in contacts) announces the raw number
    // instead of silently saying nothing, so the user still gets useful
    // information.
    private var lastAnnouncedNumber: String? = null

    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            val state = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE)
            if (state != android.telephony.TelephonyManager.EXTRA_STATE_RINGING) {
                if (state == android.telephony.TelephonyManager.EXTRA_STATE_IDLE) lastAnnouncedNumber = null
                return
            }

            val incomingNumber = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER)
            if (incomingNumber.isNullOrBlank() || incomingNumber == lastAnnouncedNumber) return
            lastAnnouncedNumber = incomingNumber

            serviceScope.launch {
                val tone = toneFromSlider(localSettings.getToneLevel())
                val contactsHelper = com.dti.kate.core.ContactsHelper(applicationContext)
                val callerName = if (contactsHelper.hasPermission()) {
                    contactsHelper.getAllContacts()
                        .firstOrNull { normalizePhoneNumber(it.phoneNumber) == normalizePhoneNumber(incomingNumber) }
                        ?.name
                } else null

                val message = responseGenerator.speechForIncomingCall(callerName ?: incomingNumber, tone)
                if (isTtsReady) speak(message) else pendingSpeech = message
            }
        }
    }

    /** Loose digits-only comparison - contact numbers and Caller ID numbers
     * frequently differ in formatting (spaces, dashes, a leading country
     * code) even when they're the same number, so an exact string match
     * would miss legitimate matches constantly. */
    private fun normalizePhoneNumber(number: String): String = number.filter { it.isDigit() }.takeLast(10)

    override fun onCreate() {
        super.onCreate()
        localSettings = LocalSettingsStore(this)
        createNotificationChannel()

        ttsEngine = KateTtsEngine(this)
        serviceScope.launch {
            ttsEngine.initialize()
            isTtsReady = true
            pendingSpeech?.let { queued ->
                pendingSpeech = null
                speak(queued)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter)

        // READ_PHONE_STATE is already declared in the manifest. If the user
        // hasn't granted it at runtime yet, Android denies delivery of this
        // broadcast at the OS level (logged as a "Permission Denial"
        // rather than a crash here) - confirmed via Android's own
        // BroadcastQueue behavior, not assumed. So no extra try/catch is
        // needed around this registration for the not-yet-granted case;
        // the receiver just won't fire until the permission is granted.
        registerReceiver(phoneStateReceiver, IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager?.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        setUpWakeWord()
        applicationContext
            .getSharedPreferences("kate_local_settings", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(settingsChangeListener)

        // Bubble should be visible on any screen once the user's set up -
        // not just conjured during a gesture cycle. onWakeGestureDetected()
        // already checks canDrawOverlays before routing there; same check
        // here so this is a no-op until the permission's granted.
        val isAuthenticated = com.dti.kate.repository.Repository(applicationContext).isAuthenticated()
        if (isAuthenticated && android.provider.Settings.canDrawOverlays(this)) {
            com.dti.kate.ui.overlay.KateOverlayService.ensureShowing(applicationContext)
        }
    }

    /**
     * Loads the wake-word model (if bundled/trained - see WakeWordDetector's
     * class doc) and, if the user has the "Hey Kate" trigger enabled, starts
     * the always-on listening loop. Also subscribes to MicArbiter so the
     * loop yields the mic whenever HomeScreen starts an active
     * command-listening session, and reclaims it when that session ends.
     */
    private fun setUpWakeWord() {
        val detector = WakeWordDetector(applicationContext)
        wakeWordDetector = detector

        serviceScope.launch {
            val loaded = detector.initialize()
            if (!loaded) return@launch // no model bundled yet - feature silently stays off

            serviceScope.launch {
                MicArbiter.appIsCapturing.collect { appCapturing ->
                    if (appCapturing) {
                        stopWakeWordListening()
                    } else if (localSettings.getWakeWordEnabled()) {
                        startWakeWordListening()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // service is only started from KateNavHost after RECORD_AUDIO is confirmed granted
    private fun startWakeWordListening() {
        if (wakeWordJob != null) return // already running
        val detector = wakeWordDetector ?: return
        if (!detector.isReady) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            MelSpectrogram.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) return

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, // see AudioCapture's doc on why MIC over VOICE_RECOGNITION on this device family
            MelSpectrogram.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        wakeWordAudioRecord = record
        record.startRecording()

        wakeWordJob = serviceScope.launch {
            val buffer = ByteArray(minBufferSize)
            while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val detected = detector.feedAudio(buffer.copyOf(read))
                    if (detected) {
                        onWakeGestureDetected()
                    }
                }
            }
        }
    }

    private fun stopWakeWordListening() {
        wakeWordJob?.cancel()
        wakeWordJob = null
        wakeWordAudioRecord?.let {
            try {
                if (it.state == AudioRecord.STATE_INITIALIZED) it.stop()
            } catch (e: Exception) { /* already stopped */ }
            it.release()
        }
        wakeWordAudioRecord = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    // Called when the user swipes the app away from recents. START_STICKY
    // alone isn't enough on all OEMs to guarantee a prompt restart - a
    // sibling Kate project hit this exact issue and documented the fix:
    // explicitly schedule a restart via AlarmManager rather than relying
    // on the OS's own sticky-service recovery, which can be delayed or
    // skipped entirely by aggressive OEM battery/task management (this
    // device's Transsion skin has shown that behavior repeatedly
    // elsewhere in this app - foreground service kills, Doze throttling
    // of the wake-gesture sensor listener, etc). Without this, swiping
    // Kate away can leave wake-gesture detection dead until the user
    // manually reopens the app.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            val restartIntent = Intent(applicationContext, KateForegroundService::class.java)
            val pendingIntent = android.app.PendingIntent.getService(
                applicationContext, 1, restartIntent,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.set(
                android.app.AlarmManager.ELAPSED_REALTIME,
                android.os.SystemClock.elapsedRealtime() + 1000,
                pendingIntent
            )
        } catch (e: Exception) {
            // Scheduling the restart failing shouldn't crash whatever's
            // tearing the task down - worst case we fall back to plain
            // START_STICKY behavior.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(powerReceiver)
        unregisterReceiver(phoneStateReceiver)
        sensorManager?.unregisterListener(sensorListener)
        ttsEngine.close()

        stopWakeWordListening()
        wakeWordDetector?.close()
        wakeWordDetector = null
        serviceScope.cancel()
        applicationContext
            .getSharedPreferences("kate_local_settings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(settingsChangeListener)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun speak(text: String) {
        val tone = toneFromSlider(localSettings.getToneLevel())
        serviceScope.launch { ttsEngine.speakAndAwait(text, tone) }
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
