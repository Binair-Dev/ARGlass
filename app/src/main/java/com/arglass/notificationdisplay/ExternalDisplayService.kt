package com.arglass.notificationdisplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat

class ExternalDisplayService : Service(), DisplayManager.DisplayListener {

    companion object {
        private const val CHANNEL_ID = "external_display_service"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "ExternalDisplayService"
    }

    private lateinit var displayManager: DisplayManager
    private var currentPresentation: NotificationPresentation? = null

    override fun onCreate() {
        super.onCreate()
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Enregistrer le listener pour détecter les changements d'écrans
        displayManager.registerDisplayListener(this, null)

        // Vérifier immédiatement s'il y a un écran externe
        checkAndStartPresentation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkAndStartPresentation()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Service d'affichage externe",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service pour l'affichage sur écran externe"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val statusText = if (currentPresentation != null) {
            "Affichage actif sur écran externe"
        } else {
            "En attente d'écran externe"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ARGlass Service")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun checkAndStartPresentation() {
        val displays = displayManager.displays
        val externalDisplay = displays.find { it.displayId != Display.DEFAULT_DISPLAY }

        if (externalDisplay != null) {
            if (currentPresentation == null) {
                Log.d(TAG, "Démarrage de la présentation sur écran externe: ${externalDisplay.displayId}")
                startPresentation(externalDisplay)
            }
        } else {
            if (currentPresentation != null) {
                Log.d(TAG, "Arrêt de la présentation - aucun écran externe détecté")
                stopPresentation()
            }
        }
    }

    private fun startPresentation(display: Display) {
        try {
            currentPresentation = NotificationPresentation(this, display)
            currentPresentation?.show()

            // Mettre à jour la notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())

            Log.d(TAG, "Présentation démarrée avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du démarrage de la présentation", e)
            currentPresentation = null
        }
    }

    private fun stopPresentation() {
        currentPresentation?.dismiss()
        currentPresentation = null

        // Mettre à jour la notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())

        Log.d(TAG, "Présentation arrêtée")
    }

    // DisplayManager.DisplayListener callbacks
    override fun onDisplayAdded(displayId: Int) {
        Log.d(TAG, "Écran ajouté: $displayId")
        checkAndStartPresentation()
    }

    override fun onDisplayRemoved(displayId: Int) {
        Log.d(TAG, "Écran supprimé: $displayId")
        if (currentPresentation?.display?.displayId == displayId) {
            stopPresentation()
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        Log.d(TAG, "Écran modifié: $displayId")
        // Optionnel: gérer les changements de configuration d'écran
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(this)
        stopPresentation()
        Log.d(TAG, "Service détruit")
    }
}