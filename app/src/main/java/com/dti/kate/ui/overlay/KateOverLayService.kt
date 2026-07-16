package com.dti.kate.ui.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.dti.kate.R
import com.dti.kate.core.VoskManager

class KateOverlayService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "kate_overlay_channel"
        
        fun start(context: Context) {
            val intent = Intent(context, KateOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, KateOverlayService::class.java))
        }
    }
    
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var voskManager: VoskManager
    
    private var isExpanded = false
    private var isListening = false
    
    override fun onCreate() {
        super.onCreate()
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        voskManager = VoskManager(this)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        setupOverlayView()
    }
    
    private fun setupOverlayView() {
        // Inflate the overlay layout
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_kate, null)
        
        // Configure window params
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
        
        // Add touch listener for dragging
        setupTouchListeners()
        
        windowManager.addView(overlayView, params)
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
                    initialX = (view.layoutParams as WindowManager.LayoutParams).x
                    initialY = (view.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Check if it was a tap (not drag)
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy < 100) {
                        toggleExpanded()
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
        
        if (isExpanded) {
            expandedView.visibility = View.VISIBLE
            bubbleView.visibility = View.GONE
        } else {
            expandedView.visibility = View.GONE
            bubbleView.visibility = View.VISIBLE
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kate Assistant",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Kate is running in the background"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kate Assistant")
            .setContentText("Tap to open Kate")
            .setSmallIcon(R.drawable.ic_kate_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
