package com.dti.kate.ui.overlay

import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dti.kate.R
import com.dti.kate.core.*
import com.dti.kate.utils.DeviceControlManager
import kotlinx.coroutines.*

/**
 * Always-visible floating bubble that listens, processes, and speaks
 * entirely in place - the app is never opened for this. Triggered by
 * KateForegroundService.onWakeGestureDetected() (shake/raise/wake word).
 *
 * Previously this class only rendered a draggable bubble with no wiring to
 * VoskManager, KateCommandProcessor, or TTS at all - it was instantiated
 * but never actually invoked from anywhere, so every gesture fell back to
 * KateActivity's onWakeGestureDetected() launching the full app. That
 * fallback is now reserved for the one case in
 * KateForegroundService.onWakeGestureDetected() that legitimately still
 * needs the app: fresh installs (Repository.isAuthenticated() == false),
 * where there's no onboarded session for the overlay to act on.
 */
class KateOverlayService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "kate_overlay_channel"
        private const val TAG = "KateOverlayService"

        // Matches ui/theme/Color.kt's Kate* state colors - the overlay is
        // a plain Android View, not Compose, so these are duplicated as
        // raw ARGB ints. Keep in sync if the palette changes.
        private const val COLOR_IDLE = 0xFFA79AB8.toInt()       // TextSecondary
        private const val COLOR_LISTENING = 0xFFD4FF4F.toInt()  // LimeAccent
        private const val COLOR_PROCESSING = 0xFF7C3AED.toInt() // Purple70
        private const val COLOR_SPEAKING = 0xFFFF6B9D.toInt()

        private const val AUTO_COLLAPSE_DELAY_MS = 4000L

        private const val ACTION_ENSURE_SHOWING = "com.dti.kate.overlay.ENSURE_SHOWING"
        private const val ACTION_ACTIVATE = "com.dti.kate.overlay.ACTIVATE"

        /** Ensures the bubble is showing (idle), without starting a listen cycle. Call once (e.g. from KateForegroundService.onCreate) so the bubble is present on any screen even before the first wake trigger. */
        fun ensureShowing(context: Context) {
            val intent = Intent(context, KateOverlayService::class.java).apply {
                action = ACTION_ENSURE_SHOWING
            }
            startServiceCompat(context, intent)
        }

        /** Starts (if needed) and immediately kicks off a listen -> process -> speak cycle. This is the gesture/wake-word entry point. */
        fun activate(context: Context) {
            val intent = Intent(context, KateOverlayService::class.java).apply {
                action = ACTION_ACTIVATE
            }
            startServiceCompat(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KateOverlayService::class.java))
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    private enum class OverlayState { IDLE, LISTENING, PROCESSING, SPEAKING }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var audioCapture: AudioCapture
    private lateinit var localSettings: LocalSettingsStore
    private lateinit var commandProcessor: KateCommandProcessor
    private lateinit var sttEngine: KateSttEngine

    private lateinit var ttsEngine: KateTtsEngine

    private var isExpanded = false
    private var overlayAdded = false
    private var state = OverlayState.IDLE
    private var ringAnimator: ValueAnimator? = null
    private var collapseTimer: CountDownTimer? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeCycle: Job? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        localSettings = LocalSettingsStore(this)
        audioCapture = AudioCapture()

        // Repository requires an authenticated user for transcribeCloud's
        // token - null here just means "Kate Pro" quietly behaves like
        // "Kate Classic" (see KateSttEngine's class doc), not an error.
        val repo = com.dti.kate.repository.Repository(applicationContext)
        val repository = if (repo.isAuthenticated()) repo else null
        sttEngine = KateSttEngine(this, audioCapture, localSettings, repository)

        ttsEngine = KateTtsEngine(this)
        serviceScope.launch { ttsEngine.initialize() }

        commandProcessor = KateCommandProcessor(
            context = this,
            responseGenerator = KateResponseGenerator(),
            deviceControl = DeviceControlManager(this),
            weatherService = WeatherService(),
            webSearchService = WebSearchService(),
            appLauncher = AppLauncher(this),
            musicLauncher = MusicLauncher(this),
            contactsHelper = ContactsHelper(this),
            locationHelper = LocationHelper(this),
            permissionBridge = object : KateCommandProcessor.PermissionBridge {
                override fun hasContacts() = ContextCompat.checkSelfPermission(
                    this@KateOverlayService, android.Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
                override fun hasLocation() = ContextCompat.checkSelfPermission(
                    this@KateOverlayService, android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                // Neither a permission-dialog launcher nor accompanist's
                // rememberPermissionState exist outside an Activity -
                // opening the app is the one legitimate exception to
                // "never opens the app for this".
                override fun requestContacts() = openAppForPermission()
                override fun requestLocation() = openAppForPermission()
            },
        )

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ACTIVATE) startListenCycle()
        return START_STICKY
    }

    private fun openAppForPermission() {
        val launchIntent = Intent(this, com.dti.kate.ui.KateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(launchIntent)
    }

    // ------------------------------------------------------------------
    // Listen -> process -> speak cycle
    // ------------------------------------------------------------------

    private fun startListenCycle() {
        if (activeCycle?.isActive == true) return // already mid-cycle, ignore re-trigger
        collapseTimer?.cancel()

        activeCycle = serviceScope.launch {
            setState(OverlayState.LISTENING)
            MicArbiter.setCapturing(true)

            val transcript = try {
                listenForTranscript()
            } finally {
                MicArbiter.setCapturing(false)
            }

            if (transcript.isNullOrBlank()) {
                setState(OverlayState.IDLE)
                scheduleAutoCollapse()
                return@launch
            }

            setState(OverlayState.PROCESSING)
            val tone = toneFromSlider(localSettings.getToneLevel())
            val result = commandProcessor.process(transcript, tone)

            setState(OverlayState.SPEAKING)
            // Per product spec: the overlay never writes the transcript or
            // reply out as text - voice only - EXCEPT when the command
            // itself was an explicit dictation/typing request, where
            // showing what got typed is the point.
            if (result.action is KateAction.TypeText) {
                showTypedTextBriefly(result.action.text)
            }
            speakAndAwait(result.speech)

            setState(OverlayState.IDLE)
            scheduleAutoCollapse()
        }
    }

    private suspend fun listenForTranscript(): String? = sttEngine.listen(serviceScope)

    private suspend fun speakAndAwait(text: String) = ttsEngine.speakAndAwait(text)

    // ------------------------------------------------------------------
    // Visual state - ring pulse + avatar tint, no text
    // ------------------------------------------------------------------

    /**
     * Safe to call from any thread. startListenCycle runs on
     * serviceScope (Dispatchers.IO), and this touches views + starts a
     * ValueAnimator - both require the main/Looper thread. Confirmed via
     * crash report: calling this directly from the IO coroutine threw
     * "Animators may only be run on Looper threads" (ValueAnimator needs
     * a Looper) every time the overlay's listen cycle ran. Posting the
     * UI work to the main looper here means every call site stays as-is
     * - no need to wrap 6+ call sites in withContext(Dispatchers.Main)
     * individually, and any future call site is safe by default too.
     */
    private fun setState(newState: OverlayState) {
        state = newState
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            // Defensive: this runs async relative to when setState() was
            // called, so if the overlay window/view is torn down (service
            // stopping, view detached) in the gap between posting and
            // running, overlayView.findViewById or the animator can throw.
            // An uncaught exception here would crash the whole main thread
            // exactly like the original Looper bug did - a UI ring-pulse
            // update should never be able to take the whole process down.
            try {
                val color = when (newState) {
                    OverlayState.IDLE -> COLOR_IDLE
                    OverlayState.LISTENING -> COLOR_LISTENING
                    OverlayState.PROCESSING -> COLOR_PROCESSING
                    OverlayState.SPEAKING -> COLOR_SPEAKING
                }

                val avatar = overlayView.findViewById<ImageView>(R.id.kate_avatar)
                avatar.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)

                val ring = overlayView.findViewById<View>(R.id.state_ring)
                (ring.background as? GradientDrawable)?.setStroke(dp(3), color)

                ringAnimator?.cancel()
                if (newState == OverlayState.IDLE) {
                    ring.alpha = 0f
                    ring.scaleX = 1f
                    ring.scaleY = 1f
                } else {
                    ring.alpha = 1f
                    // Slow pulse (breathing ring) - faster while actively listening
                    // than while thinking/speaking, so the state is legible at a
                    // glance without needing to read anything.
                    val duration = if (newState == OverlayState.LISTENING) 700L else 1100L
                    ringAnimator = ValueAnimator.ofFloat(1f, 1.25f).apply {
                        this.duration = duration
                        repeatMode = ValueAnimator.REVERSE
                        repeatCount = ValueAnimator.INFINITE
                        addUpdateListener {
                            val scale = it.animatedValue as Float
                            ring.scaleX = scale
                            ring.scaleY = scale
                            ring.alpha = 1f - (scale - 1f) // fades slightly as it expands
                        }
                        start()
                    }
                }
            } catch (e: Exception) {
                DebugLog.log(this@KateOverlayService, "KateOverLayService", "setState UI update failed (state=$newState): ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /**
     * Safe to call from any thread, same reasoning as setState(): both
     * call sites (185, 204) run inside startListenCycle's serviceScope
     * coroutine (Dispatchers.IO). CountDownTimer's constructor creates a
     * Handler() internally, which requires a Looper on the constructing
     * thread - confirmed via bugreport: "Can't create handler inside
     * thread DefaultDispatcher-worker-1 that has not called
     * Looper.prepare()", 8 occurrences. This is a second, separate
     * Looper-requiring API from the ValueAnimator one in setState() - same
     * underlying mistake (IO-dispatcher coroutine touching an
     * Android UI/timer API), different call site, so it needed its own fix.
     */
    private fun scheduleAutoCollapse() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                collapseTimer?.cancel()
                collapseTimer = object : CountDownTimer(AUTO_COLLAPSE_DELAY_MS, AUTO_COLLAPSE_DELAY_MS) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        if (isExpanded) toggleExpanded()
                    }
                }.start()
            } catch (e: Exception) {
                DebugLog.log(this@KateOverlayService, "KateOverLayService", "scheduleAutoCollapse failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /** Text only appears here, and only for an explicit typing command - see setState's call site. Reuses the existing expanded-view transcript TextView rather than adding new UI. */
    private fun showTypedTextBriefly(text: String) {
        val expandedView = overlayView.findViewById<FrameLayout>(R.id.overlay_expanded)
        val transcriptLabel = expandedView.findViewById<android.widget.TextView>(R.id.overlay_transcript)
        transcriptLabel?.text = text
        if (!isExpanded) toggleExpanded()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------
    // Bubble shell (drag/tap)
    // ------------------------------------------------------------------

    private fun setupOverlayView() {
        if (overlayAdded) return
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_kate, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        setupTouchListeners()
        windowManager.addView(overlayView, params)
        overlayAdded = true

        overlayView.findViewById<View>(R.id.state_ring).alpha = 0f // starts idle, no pulse
        overlayView.findViewById<ImageButton>(R.id.overlay_close)?.setOnClickListener {
            if (isExpanded) toggleExpanded()
        }
    }

    private fun setupTouchListeners() {
        val bubbleView = overlayView.findViewById<FrameLayout>(R.id.overlay_bubble)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubbleView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = overlayView.layoutParams as WindowManager.LayoutParams
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = overlayView.layoutParams as WindowManager.LayoutParams
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy < 100) {
                        if (state == OverlayState.IDLE) startListenCycle() else toggleExpanded()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        val expandedView = overlayView.findViewById<FrameLayout>(R.id.overlay_expanded)
        val bubbleView = overlayView.findViewById<FrameLayout>(R.id.overlay_bubble)
        expandedView.visibility = if (isExpanded) View.VISIBLE else View.GONE
        bubbleView.visibility = if (isExpanded) View.GONE else View.VISIBLE
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Kate Assistant", NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Kate is running in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kate Assistant")
            .setContentText("Tap the bubble or say a wake phrase")
            .setSmallIcon(R.drawable.ic_kate_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        activeCycle?.cancel()
        serviceScope.cancel()
        ringAnimator?.cancel()
        collapseTimer?.cancel()
        audioCapture.stop()
        MicArbiter.setCapturing(false)
        ttsEngine.close()
        if (overlayAdded && ::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
