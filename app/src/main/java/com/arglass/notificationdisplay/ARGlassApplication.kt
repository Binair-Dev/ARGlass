package com.arglass.notificationdisplay

import android.app.Application
import org.osmdroid.config.Configuration
import android.preference.PreferenceManager

class ARGlassApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configuration OSMDroid obligatoire
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = "ARGlass/1.0"

        // Dossier de cache pour les tiles
        Configuration.getInstance().osmdroidBasePath = getExternalFilesDir(null)
        Configuration.getInstance().osmdroidTileCache = getExternalFilesDir("osmdroid/tiles")

        android.util.Log.d("ARGlassApplication", "🗺️ OSMDroid configuré - Cache: ${Configuration.getInstance().osmdroidTileCache}")
    }
}