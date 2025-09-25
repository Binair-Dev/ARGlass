package com.arglass.notificationdisplay

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.os.Handler
import android.os.Looper

class NotificationListenerService : NotificationListenerService() {

    private val cleanupHandler = Handler(Looper.getMainLooper())
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            cleanupExpiredNotifications()
            cleanupHandler.postDelayed(this, 30000) // Check every 30 seconds
        }
    }

    companion object {
        private const val TAG = "NotificationListener"
        var instance: NotificationListenerService? = null
        private val notifications = mutableListOf<NotificationData>()
        private const val NOTIFICATION_EXPIRY_TIME = 1 * 60 * 1000L // 1 minute in milliseconds

        fun getRecentNotifications(): List<NotificationData> {
            // Remove expired notifications (older than 1 minute)
            val currentTime = System.currentTimeMillis()
            synchronized(notifications) {
                notifications.removeAll { notification ->
                    currentTime - notification.timestamp > NOTIFICATION_EXPIRY_TIME
                }
            }
            return notifications.takeLast(10)
        }
    }

    data class NotificationData(
        val appName: String,
        val title: String?,
        val content: String?,
        val timestamp: Long,
        val packageName: String,
        val largeIcon: android.graphics.Bitmap? = null,
        val smallIcon: Int? = null
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "NotificationListenerService created")
        // Start periodic cleanup
        cleanupHandler.post(cleanupRunnable)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            handleNotification(it, true)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Optionally handle notification removal
    }

    private fun handleNotification(sbn: StatusBarNotification, isNew: Boolean) {
        try {
            val notification = sbn.notification
            val packageName = sbn.packageName

            Log.d(TAG, "Received notification from: $packageName")

            // Skip our own notifications
            if (packageName == this.packageName) {
                Log.d(TAG, "Skipping our own notification")
                return
            }

            // Skip system notifications we don't want
            if (shouldSkipNotification(packageName)) {
                Log.d(TAG, "Skipping system notification: $packageName")
                return
            }

            val appName = getAppName(packageName)
            val title = notification.extras.getCharSequence("android.title")?.toString()
            val content = notification.extras.getCharSequence("android.text")?.toString()

            // Récupérer les icônes pour les instructions de navigation
            val largeIcon = notification.getLargeIcon()?.loadDrawable(this)?.let { drawable ->
                // Convertir drawable en bitmap si possible
                try {
                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            drawable.intrinsicWidth,
                            drawable.intrinsicHeight,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bitmap
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur conversion icône en bitmap", e)
                    null
                }
            }

            val smallIconId = notification.icon

            Log.d(TAG, "Processing notification - App: $appName, Title: $title, Content: $content")
            Log.d(TAG, "Icons - Large: ${largeIcon != null}, Small: $smallIconId")

            val notificationData = NotificationData(
                appName = appName,
                title = title,
                content = content,
                timestamp = sbn.postTime,
                packageName = packageName,
                largeIcon = largeIcon,
                smallIcon = smallIconId
            )

            // Add to our list (keep only recent ones)
            synchronized(notifications) {
                notifications.add(notificationData)
                if (notifications.size > 50) {
                    notifications.removeAt(0)
                }
                Log.d(TAG, "Total notifications stored: ${notifications.size}")
            }

            // Broadcast to external display
            broadcastNotificationUpdate()

            Log.d(TAG, "✅ Notification added and broadcast sent: $appName - $title")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification", e)
        }
    }

    private fun shouldSkipNotification(packageName: String): Boolean {
        val skipPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.system",
            this.packageName // Skip our own app specifically
        )
        return skipPackages.contains(packageName)
    }

    private fun getAppName(packageName: String): String {
        return try {
            Log.d(TAG, "🔍 Attempting to resolve app name for: $packageName")

            // Method 1: Try with GET_META_DATA flag
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                Log.d(TAG, "✅ Method 1 success: $packageName -> $appName")
                return appName
            } catch (e1: Exception) {
                Log.w(TAG, "❌ Method 1 failed for $packageName: ${e1.message}")
            }

            // Method 2: Try without flags
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                Log.d(TAG, "✅ Method 2 success: $packageName -> $appName")
                return appName
            } catch (e2: Exception) {
                Log.w(TAG, "❌ Method 2 failed for $packageName: ${e2.message}")
            }

            // Method 3: Try through PackageInfo
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
                Log.d(TAG, "✅ Method 3 success: $packageName -> $appName")
                return appName
            } catch (e3: Exception) {
                Log.w(TAG, "❌ Method 3 failed for $packageName: ${e3.message}")
            }

            // Method 4: Get installed packages and find match
            try {
                val installedPackages = packageManager.getInstalledPackages(0)
                val targetPackage = installedPackages.find { it.packageName == packageName }
                if (targetPackage != null) {
                    val appName = packageManager.getApplicationLabel(targetPackage.applicationInfo).toString()
                    Log.d(TAG, "✅ Method 4 success: $packageName -> $appName")
                    return appName
                } else {
                    Log.w(TAG, "❌ Method 4: Package not found in installed packages")
                }
            } catch (e4: Exception) {
                Log.w(TAG, "❌ Method 4 failed for $packageName: ${e4.message}")
            }

            // Fallback: Map des apps courantes
            Log.w(TAG, "🚨 All methods failed for $packageName, using fallback map")
            getAppNameFallback(packageName)

        } catch (e: Exception) {
            Log.e(TAG, "🚨 Unexpected error getting app name for $packageName", e)
            getAppNameFallback(packageName)
        }
    }

    private fun getAppNameFallback(packageName: String): String {
        return when (packageName) {
            "com.google.android.gm" -> "Gmail"
            "com.facebook.orca" -> "Messenger"
            "com.whatsapp" -> "WhatsApp"
            "com.snapchat.android" -> "Snapchat"
            "com.instagram.android" -> "Instagram"
            "com.twitter.android" -> "Twitter"
            "com.google.android.apps.maps" -> "Google Maps"
            "com.spotify.music" -> "Spotify"
            "com.discord" -> "Discord"
            "com.telegram.messenger" -> "Telegram"
            "com.google.android.youtube" -> "YouTube"
            "com.netflix.mediaclient" -> "Netflix"
            "com.amazon.mShop.android.shopping" -> "Amazon"
            "com.paypal.android.p2pmobile" -> "PayPal"
            "com.uber.app" -> "Uber"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.microsoft.teams" -> "Teams"
            "com.slack" -> "Slack"
            "com.google.android.apps.messaging" -> "Messages"
            "com.android.chrome" -> "Chrome"
            else -> {
                // Essayer d'extraire le nom depuis le package
                val parts = packageName.split(".")
                when {
                    parts.size >= 3 -> parts.last().replaceFirstChar { it.uppercase() }
                    parts.size >= 2 -> parts[1].replaceFirstChar { it.uppercase() }
                    else -> packageName
                }
            }
        }
    }

    private fun broadcastNotificationUpdate() {
        val intent = Intent("com.arglass.notificationdisplay.NOTIFICATION_UPDATE")
        sendBroadcast(intent)
    }

    private fun cleanupExpiredNotifications() {
        val currentTime = System.currentTimeMillis()
        val initialSize = notifications.size
        synchronized(notifications) {
            notifications.removeAll { notification ->
                currentTime - notification.timestamp > NOTIFICATION_EXPIRY_TIME
            }
        }

        if (notifications.size < initialSize) {
            Log.d(TAG, "🧹 Cleaned up ${initialSize - notifications.size} expired notifications")
            // Broadcast update to refresh the display
            broadcastNotificationUpdate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupHandler.removeCallbacks(cleanupRunnable)
        instance = null
        Log.d(TAG, "NotificationListenerService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
}