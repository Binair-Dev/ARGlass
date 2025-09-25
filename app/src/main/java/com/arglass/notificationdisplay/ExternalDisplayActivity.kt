package com.arglass.notificationdisplay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ExternalDisplayActivity : Activity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var rvNotifications: RecyclerView
    private lateinit var notificationAdapter: NotificationAdapter

    private val timeHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.FRANCE)

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.arglass.notificationdisplay.NOTIFICATION_UPDATE") {
                updateNotifications()
            }
        }
    }

    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            updateTimeAndDate()
            timeHandler.postDelayed(this, 1000) // Update every second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window for external display and AR glasses
        setupWindow()

        setContentView(R.layout.activity_external_display)

        initViews()
        setupNotificationsList()
        startTimeUpdates()
        registerNotificationReceiver()

        // Initial update
        updateNotifications()
        updateTimeAndDate()
    }

    private fun setupWindow() {
        // Make window suitable for AR glasses
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // Set window format for transparency
        window.setFormat(PixelFormat.TRANSLUCENT)

        // Position window on external display if specified
        val displayId = intent.getIntExtra("display_id", -1)
        if (displayId != -1) {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            // Note: For API 30+, you might need to use WindowManager.LayoutParams.displayId
        }
    }

    private fun initViews() {
        tvTime = findViewById(R.id.tvTime)
        tvDate = findViewById(R.id.tvDate)
        rvNotifications = findViewById(R.id.rvNotifications)
    }

    private fun setupNotificationsList() {
        notificationAdapter = NotificationAdapter()
        rvNotifications.apply {
            layoutManager = LinearLayoutManager(this@ExternalDisplayActivity)
            adapter = notificationAdapter
        }
    }

    private fun startTimeUpdates() {
        timeHandler.post(timeUpdateRunnable)
    }

    private fun registerNotificationReceiver() {
        val filter = IntentFilter("com.arglass.notificationdisplay.NOTIFICATION_UPDATE")
        registerReceiver(notificationReceiver, filter)
    }

    private fun updateTimeAndDate() {
        val now = Date()
        tvTime.text = dateFormat.format(now)
        tvDate.text = fullDateFormat.format(now)
    }

    private fun updateNotifications() {
        val notifications = NotificationListenerService.getRecentNotifications()
        notificationAdapter.updateNotifications(notifications)
    }

    override fun onDestroy() {
        super.onDestroy()
        timeHandler.removeCallbacks(timeUpdateRunnable)
        try {
            unregisterReceiver(notificationReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    override fun onBackPressed() {
        // Prevent accidental closing on AR glasses
        // Do nothing or show a confirmation
    }
}