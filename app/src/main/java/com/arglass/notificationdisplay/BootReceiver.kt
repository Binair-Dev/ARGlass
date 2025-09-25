package com.arglass.notificationdisplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Boot completed or package replaced, starting service")

                // Check if notification access is enabled before starting service
                if (isNotificationServiceEnabled(context)) {
                    val serviceIntent = Intent(context, ExternalDisplayService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(packageName) == true
    }
}