package com.arglass.notificationdisplay

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.*

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean = false
)

class AppLauncherMenu(private val context: Context) {

    companion object {
        private const val TAG = "AppLauncherMenu"
    }

    interface MenuNavigationListener {
        fun onMenuItemSelected(selectedIndex: Int, totalItems: Int)
        fun onAppLaunched(appInfo: AppInfo)
        fun onMenuClosed()
    }

    private var listener: MenuNavigationListener? = null
    private var installedApps: List<AppInfo> = emptyList()
    private var currentSelectedIndex = 0
    private var isMenuVisible = false

    // Applications favorites/fréquentes en premier
    private val favoriteApps = listOf(
        "com.google.android.gm", // Gmail
        "com.whatsapp", // WhatsApp
        "com.spotify.music", // Spotify
        "com.google.android.youtube", // YouTube
        "com.google.android.apps.photos", // Google Photos
        "com.android.camera2", // Appareil Photo
        "com.google.android.calculator", // Calculatrice
        "com.android.settings", // Paramètres
        "com.google.android.apps.maps", // Google Maps
        "com.google.android.chrome" // Chrome
    )

    fun setNavigationListener(listener: MenuNavigationListener) {
        this.listener = listener
    }

    fun loadInstalledApps() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "📱 Chargement de la liste des applications...")

                val packageManager = context.packageManager
                val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

                val appList = installedPackages
                    .filter { appInfo ->
                        // Filtrer les applications avec launcher intent
                        val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                        intent != null
                    }
                    .map { appInfo ->
                        try {
                            AppInfo(
                                name = appInfo.loadLabel(packageManager).toString(),
                                packageName = appInfo.packageName,
                                icon = appInfo.loadIcon(packageManager),
                                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error loading app info for ${appInfo.packageName}", e)
                            AppInfo(
                                name = appInfo.packageName,
                                packageName = appInfo.packageName,
                                icon = null,
                                isSystem = true
                            )
                        }
                    }
                    .sortedWith { app1, app2 ->
                        // Tri: favoris d'abord, puis alphabétique
                        val app1Priority = favoriteApps.indexOf(app1.packageName).takeIf { it >= 0 } ?: Int.MAX_VALUE
                        val app2Priority = favoriteApps.indexOf(app2.packageName).takeIf { it >= 0 } ?: Int.MAX_VALUE

                        when {
                            app1Priority != app2Priority -> app1Priority.compareTo(app2Priority)
                            else -> app1.name.compareTo(app2.name, ignoreCase = true)
                        }
                    }

                withContext(Dispatchers.Main) {
                    installedApps = appList
                    currentSelectedIndex = 0
                    Log.d(TAG, "✅ ${appList.size} applications chargées")

                    // Notifier le listener
                    if (isMenuVisible) {
                        listener?.onMenuItemSelected(currentSelectedIndex, installedApps.size)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors du chargement des applications", e)
            }
        }
    }

    fun showMenu() {
        if (installedApps.isEmpty()) {
            loadInstalledApps()
        }

        isMenuVisible = true
        currentSelectedIndex = 0

        Log.d(TAG, "📋 Menu affiché avec ${installedApps.size} applications")
        listener?.onMenuItemSelected(currentSelectedIndex, installedApps.size)
    }

    fun hideMenu() {
        isMenuVisible = false
        Log.d(TAG, "📋 Menu masqué")
        listener?.onMenuClosed()
    }

    fun navigateUp() {
        if (!isMenuVisible || installedApps.isEmpty()) return

        currentSelectedIndex = if (currentSelectedIndex > 0) {
            currentSelectedIndex - 1
        } else {
            installedApps.size - 1 // Boucler vers la fin
        }

        Log.d(TAG, "⬆️ Navigation menu: ${currentSelectedIndex + 1}/${installedApps.size}")
        listener?.onMenuItemSelected(currentSelectedIndex, installedApps.size)
    }

    fun navigateDown() {
        if (!isMenuVisible || installedApps.isEmpty()) return

        currentSelectedIndex = if (currentSelectedIndex < installedApps.size - 1) {
            currentSelectedIndex + 1
        } else {
            0 // Boucler vers le début
        }

        Log.d(TAG, "⬇️ Navigation menu: ${currentSelectedIndex + 1}/${installedApps.size}")
        listener?.onMenuItemSelected(currentSelectedIndex, installedApps.size)
    }

    fun selectCurrentItem() {
        if (!isMenuVisible || installedApps.isEmpty()) return

        val selectedApp = installedApps[currentSelectedIndex]
        Log.d(TAG, "🚀 Lancement de l'application: ${selectedApp.name}")

        launchApp(selectedApp)
        hideMenu()
    }

    private fun launchApp(appInfo: AppInfo) {
        try {
            val packageManager = context.packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)

                Log.d(TAG, "✅ Application lancée: ${appInfo.name}")
                listener?.onAppLaunched(appInfo)
            } else {
                Log.w(TAG, "❌ Impossible de lancer: ${appInfo.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors du lancement de ${appInfo.name}", e)
        }
    }

    fun getCurrentSelectedApp(): AppInfo? {
        return if (isMenuVisible && installedApps.isNotEmpty() && currentSelectedIndex < installedApps.size) {
            installedApps[currentSelectedIndex]
        } else null
    }

    fun getVisibleApps(maxCount: Int = 5): List<Pair<AppInfo, Boolean>> {
        if (!isMenuVisible || installedApps.isEmpty()) return emptyList()

        // Retourner les apps autour de la sélection actuelle
        val startIndex = maxOf(0, currentSelectedIndex - maxCount / 2)
        val endIndex = minOf(installedApps.size, startIndex + maxCount)

        return installedApps.subList(startIndex, endIndex).mapIndexed { index, app ->
            val isSelected = (startIndex + index) == currentSelectedIndex
            Pair(app, isSelected)
        }
    }

    fun isMenuVisible(): Boolean = isMenuVisible

    fun getSelectedIndex(): Int = currentSelectedIndex

    fun getTotalAppsCount(): Int = installedApps.size
}