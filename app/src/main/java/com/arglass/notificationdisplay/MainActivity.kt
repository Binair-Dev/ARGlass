package com.arglass.notificationdisplay

import android.content.ComponentName
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvExternalDisplay: TextView
    private lateinit var tvNotificationCount: TextView
    private lateinit var btnEnableNotifications: Button
    private lateinit var btnStartService: Button

    private lateinit var displayManager: DisplayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager

        // Demander les permissions GPS
        requestLocationPermissions()

        updateUI()
        checkExternalDisplay()
    }

    private fun requestLocationPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val hasLocationPermission = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasLocationPermission) {
            ActivityCompat.requestPermissions(this, permissions, 1001)
            android.util.Log.d("MainActivity", "📍 Demande permissions GPS...")
        } else {
            android.util.Log.d("MainActivity", "✅ Permissions GPS déjà accordées")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                android.util.Log.d("MainActivity", "✅ Permissions GPS accordées")
                tvStatus.text = "Permissions GPS accordées ✓"
            } else {
                android.util.Log.w("MainActivity", "❌ Permissions GPS refusées")
                tvStatus.text = "Permissions GPS nécessaires pour la minimap"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        checkExternalDisplay()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvExternalDisplay = findViewById(R.id.tvExternalDisplay)
        tvNotificationCount = findViewById(R.id.tvNotificationCount)
        btnEnableNotifications = findViewById(R.id.btnEnableNotifications)
        btnStartService = findViewById(R.id.btnStartService)
    }

    private fun setupClickListeners() {
        btnEnableNotifications.setOnClickListener {
            openNotificationAccessSettings()
        }

        btnStartService.setOnClickListener {
            if (isNotificationServiceEnabled()) {
                startExternalDisplayService()
            } else {
                tvStatus.text = "Veuillez d'abord activer l'accès aux notifications"
            }
        }
    }

    private fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )

        return flat?.contains(packageName) == true
    }

    private fun startExternalDisplayService() {
        val serviceIntent = Intent(this, ExternalDisplayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        tvStatus.text = "Service démarré"
    }

    private fun checkExternalDisplay() {
        val displays = displayManager.displays
        val externalDisplays = displays.filter { it.displayId != Display.DEFAULT_DISPLAY }

        if (externalDisplays.isNotEmpty()) {
            tvExternalDisplay.text = "Écran externe: Détecté (${externalDisplays.size})"
            tvExternalDisplay.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            tvExternalDisplay.text = "Écran externe: Non détecté"
            tvExternalDisplay.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun updateUI() {
        if (isNotificationServiceEnabled()) {
            btnEnableNotifications.text = "Accès aux notifications: Activé ✓"
            btnEnableNotifications.isEnabled = false
            btnStartService.isEnabled = true
        } else {
            btnEnableNotifications.text = "Activer l'accès aux notifications"
            btnEnableNotifications.isEnabled = true
            btnStartService.isEnabled = false
        }
    }
}