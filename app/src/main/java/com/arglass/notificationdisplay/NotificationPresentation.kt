package com.arglass.notificationdisplay

import android.app.Presentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import android.os.BatteryManager
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.Polyline
import com.google.android.gms.location.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.net.URL
import java.net.HttpURLConnection
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.*

class NotificationPresentation(
    context: Context,
    display: Display
) : Presentation(context, display, R.style.Theme_PresentationTransparent) {

    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvBattery: TextView
    private lateinit var cvNavigation: androidx.cardview.widget.CardView
    private lateinit var tvNavigationInstruction: TextView
    private lateinit var tvNavigationDetails: TextView
    private lateinit var ivDirectionIcon: android.widget.ImageView
    private lateinit var miniMap: MapView
    private lateinit var rvNotifications: RecyclerView
    private lateinit var notificationAdapter: NotificationAdapter

    // GPS et localisation
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocationMarker: Marker? = null
    private var routeLine: Polyline? = null

    // Navigation persistante
    private var lastNavigationInstruction: String? = null
    private var lastNavigationDetails: String? = null
    private var navigationActiveTime: Long = 0

    // Menu d'applications et manette Bluetooth
    private lateinit var cvAppMenu: androidx.cardview.widget.CardView
    private lateinit var tvMenuTitle: TextView
    private lateinit var llAppList: android.widget.LinearLayout
    private lateinit var tvMenuInstructions: TextView
    private lateinit var bluetoothGamepadManager: BluetoothGamepadManager
    private lateinit var appLauncherMenu: AppLauncherMenu
    private var isGamepadConnected = false

    private val timeHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.FRANCE)

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.arglass.notificationdisplay.NOTIFICATION_UPDATE") {
                android.util.Log.d("NotificationPresentation", "📡 Broadcast reçu - Mise à jour des notifications")
                updateNotifications()
            }
        }
    }

    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            updateTimeAndDate()
            timeHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forcer la transparence de la fenêtre
        window?.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }

        setContentView(R.layout.presentation_notifications)

        initViews()
        setupNotificationsList()
        setupMiniMap()
        setupLocationServices()
        setupGamepadAndMenu()
        startTimeUpdates()
        registerNotificationReceiver()

        updateNotifications()
        updateTimeAndDate()
    }


    private fun initViews() {
        tvTime = findViewById(R.id.tvTime)
        tvDate = findViewById(R.id.tvDate)
        tvBattery = findViewById(R.id.tvBattery)
        cvNavigation = findViewById(R.id.cvNavigation)
        tvNavigationInstruction = findViewById(R.id.tvNavigationInstruction)
        tvNavigationDetails = findViewById(R.id.tvNavigationDetails)
        ivDirectionIcon = findViewById(R.id.ivDirectionIcon)
        miniMap = findViewById(R.id.miniMap)
        rvNotifications = findViewById(R.id.rvNotifications)

        // Menu d'applications
        cvAppMenu = findViewById(R.id.cvAppMenu)
        tvMenuTitle = findViewById(R.id.tvMenuTitle)
        llAppList = findViewById(R.id.llAppList)
        tvMenuInstructions = findViewById(R.id.tvMenuInstructions)
    }

    private fun setupNotificationsList() {
        notificationAdapter = NotificationAdapter()
        rvNotifications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notificationAdapter
        }
    }

    private fun startTimeUpdates() {
        timeHandler.post(timeUpdateRunnable)
    }

    private fun registerNotificationReceiver() {
        val filter = IntentFilter("com.arglass.notificationdisplay.NOTIFICATION_UPDATE")
        context.registerReceiver(notificationReceiver, filter)
    }

    private fun setupMiniMap() {
        try {
            android.util.Log.d("NotificationPresentation", "🗺️ Configuration minimap...")

            // Configuration simple de la carte
            miniMap.apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                controller.setZoom(16.0)

                // Position par défaut (Paris) en attendant le GPS
                controller.setCenter(GeoPoint(48.8566, 2.3522))
            }

            android.util.Log.d("NotificationPresentation", "✅ Minimap configurée avec succès")
        } catch (e: Exception) {
            android.util.Log.e("NotificationPresentation", "❌ Erreur configuration minimap", e)
        }
    }

    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateMapLocation(location.latitude, location.longitude)
                }
            }
        }

        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.w("NotificationPresentation", "❌ Permission GPS non accordée - minimap reste sur Paris")
            // Réessayer dans 5 secondes
            timeHandler.postDelayed({ startLocationUpdates() }, 5000)
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build()

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            android.util.Log.d("NotificationPresentation", "📍 Suivi GPS démarré (haute précision)")

            // Obtenir la dernière position connue immédiatement
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    android.util.Log.d("NotificationPresentation", "📍 Position récupérée: ${it.latitude}, ${it.longitude}")
                    updateMapLocation(it.latitude, it.longitude)
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationPresentation", "❌ Erreur sécurité GPS", e)
        }
    }

    private fun updateMapLocation(latitude: Double, longitude: Double) {
        val position = GeoPoint(latitude, longitude)

        // Centrer la carte sur la position
        miniMap.controller.setCenter(position)

        // Mettre à jour le marqueur de position
        currentLocationMarker?.let { miniMap.overlays.remove(it) }

        currentLocationMarker = Marker(miniMap).apply {
            this.position = position
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Ma position"
            // Icon personnalisé pour la position (point bleu)
            icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)
        }

        miniMap.overlays.add(currentLocationMarker)

        // Ajouter un tracé d'itinéraire simulé pour test
        addTestRoute(position)

        miniMap.invalidate()

        android.util.Log.d("NotificationPresentation", "📍 Position mise à jour: $latitude, $longitude")
    }

    private fun addTestRoute(currentPosition: GeoPoint) {
        // Supprimer l'ancien tracé
        routeLine?.let { miniMap.overlays.remove(it) }

        // Destination à 1km au nord-est
        val destination = GeoPoint(
            currentPosition.latitude + 0.009,  // ~1km au nord
            currentPosition.longitude + 0.009  // ~1km à l'est
        )

        // Obtenir un vrai itinéraire via API routing
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val realRoute = getRealRoute(currentPosition, destination)
                withContext(Dispatchers.Main) {
                    displayRoute(realRoute, destination)
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationPresentation", "❌ Erreur API routing, fallback ligne droite", e)
                withContext(Dispatchers.Main) {
                    displaySimpleRoute(currentPosition, destination)
                }
            }
        }
    }

    private suspend fun getRealRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        // Utiliser OpenRouteService API (gratuit, pas de clé nécessaire pour usage léger)
        val url = "https://api.openrouteservice.org/v2/directions/driving-car?start=${start.longitude},${start.latitude}&end=${end.longitude},${end.latitude}"

        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")

                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                val coordinates = json
                    .getJSONArray("features")
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONArray("coordinates")

                val routePoints = mutableListOf<GeoPoint>()
                for (i in 0 until coordinates.length()) {
                    val coord = coordinates.getJSONArray(i)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    routePoints.add(GeoPoint(lat, lon))
                }

                android.util.Log.d("NotificationPresentation", "✅ Route API: ${routePoints.size} points")
                routePoints

            } catch (e: Exception) {
                android.util.Log.w("NotificationPresentation", "⚠️ API routing failed, using fallback")
                // Fallback: route simulée réaliste
                createRealisticRoute(start, end)
            }
        }
    }

    private fun createRealisticRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        val routePoints = mutableListOf<GeoPoint>()
        routePoints.add(start)

        // Créer une route en zigzag pour simuler des rues
        val steps = 8
        for (i in 1 until steps) {
            val progress = i.toDouble() / steps
            val lat = start.latitude + (end.latitude - start.latitude) * progress
            val lon = start.longitude + (end.longitude - start.longitude) * progress

            // Ajouter du "zigzag" pour simuler des rues
            val offset = if (i % 2 == 0) 0.001 else -0.001
            routePoints.add(GeoPoint(lat + offset * 0.5, lon + offset))
        }

        routePoints.add(end)
        return routePoints
    }

    private fun displayRoute(routePoints: List<GeoPoint>, destination: GeoPoint) {
        if (routePoints.isEmpty()) return

        // Créer la ligne de route
        routeLine = Polyline().apply {
            setPoints(routePoints)
            outlinePaint.color = android.graphics.Color.BLUE
            outlinePaint.strokeWidth = 12f
        }

        miniMap.overlays.add(routeLine)

        // Ajouter marqueur de destination
        val destinationMarker = Marker(miniMap).apply {
            position = destination
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Destination"
            icon = context.getDrawable(android.R.drawable.ic_dialog_map)
        }
        miniMap.overlays.add(destinationMarker)

        miniMap.invalidate()
        android.util.Log.d("NotificationPresentation", "🛣️ Route réelle affichée (${routePoints.size} points)")
    }

    private fun displaySimpleRoute(start: GeoPoint, end: GeoPoint) {
        val routePoints = createRealisticRoute(start, end)
        displayRoute(routePoints, end)
    }

    private fun updateTimeAndDate() {
        val now = Date()
        tvTime.text = dateFormat.format(now)
        tvDate.text = fullDateFormat.format(now)
        updateBatteryLevel()
    }

    private fun updateBatteryLevel() {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            // Get charging status
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == BatteryManager.BATTERY_STATUS_FULL

            val batteryIcon = if (isCharging) "⚡" else "🔋"
            tvBattery.text = "$batteryIcon $batteryLevel%"

            android.util.Log.d("NotificationPresentation", "Battery updated: $batteryLevel% (charging: $isCharging)")
        } catch (e: Exception) {
            android.util.Log.e("NotificationPresentation", "Error getting battery level", e)
            tvBattery.text = "🔋 -%"
        }
    }

    private fun updateNotifications() {
        val notifications = NotificationListenerService.getRecentNotifications()
        android.util.Log.d("NotificationPresentation", "📱 Mise à jour: ${notifications.size} notifications récupérées")

        // DEBUG: Afficher TOUTES les notifications pour tester
        android.util.Log.d("NotificationPresentation", "🔍 === DEBUT DEBUG NOTIFICATIONS ===")
        notifications.forEach { notif ->
            android.util.Log.d("NotificationPresentation", "📱 ${notif.appName} (${notif.packageName})")
            android.util.Log.d("NotificationPresentation", "  📝 Titre: ${notif.title}")
            android.util.Log.d("NotificationPresentation", "  📄 Contenu: ${notif.content}")
            android.util.Log.d("NotificationPresentation", "  🔍 Est Maps? ${isGoogleMapsNotification(notif)}")
        }
        android.util.Log.d("NotificationPresentation", "🔍 === FIN DEBUG NOTIFICATIONS ===")

        // Séparer les notifications Google Maps des autres
        val mapsNotifications = notifications.filter { isGoogleMapsNotification(it) }
        val otherNotifications = notifications.filterNot { isGoogleMapsNotification(it) }

        android.util.Log.d("NotificationPresentation", "🗺️ Maps trouvées: ${mapsNotifications.size}")

        // Gérer la navigation GPS séparément des autres notifications
        if (mapsNotifications.isNotEmpty()) {
            val latestMaps = mapsNotifications.first()
            updateNavigationData(latestMaps)
            showPersistentNavigation()
        } else {
            // Ne masquer la navigation que si elle est inactive depuis plus de 2 minutes
            checkNavigationTimeout()
        }

        otherNotifications.forEach { notif ->
            android.util.Log.d("NotificationPresentation", "  📱 ${notif.appName}: ${notif.title}")
        }

        // Afficher seulement les notifications NON-GPS dans la liste du bas
        notificationAdapter.updateNotifications(otherNotifications)
        android.util.Log.d("NotificationPresentation", "📋 ${otherNotifications.size} notifications non-GPS affichées")
    }

    private fun isGoogleMapsNotification(notification: NotificationListenerService.NotificationData): Boolean {
        val packageMaps = notification.packageName == "com.google.android.apps.maps"
        val appNameMaps = notification.appName.contains("Maps", ignoreCase = true)

        val title = notification.title?.lowercase() ?: ""
        val content = notification.content?.lowercase() ?: ""
        val fullText = "$title $content"

        val hasNavigationTerms = listOf(
            // Français
            "navigation", "tourner", "tournez", "continuer", "continuez", "sortez",
            "prenez", "rte de", "route", "km", "metres", "arrivée", "destination",
            "tout droit", "demi-tour", "rond-point",
            // Anglais
            "turn left", "turn right", "continue", "straight", "exit", "arrived",
            "destination reached", "u-turn", "roundabout"
        ).any { term -> fullText.contains(term) }

        val result = packageMaps || appNameMaps || hasNavigationTerms

        if (result) {
            android.util.Log.d("NotificationPresentation", "✅ Maps détecté: ${notification.appName} - $title")
        }

        return result
    }

    private fun updateNavigationData(mapsNotification: NotificationListenerService.NotificationData) {
        // Mettre à jour les données de navigation
        lastNavigationInstruction = formatNavigationInstruction(mapsNotification)
        lastNavigationDetails = extractNavigationDetails(mapsNotification)
        navigationActiveTime = System.currentTimeMillis()

        android.util.Log.d("NotificationPresentation", "🗺️ Navigation data updated: $lastNavigationInstruction")
        android.util.Log.d("NotificationPresentation", "📱 App: ${mapsNotification.appName}")
        android.util.Log.d("NotificationPresentation", "📦 Package: ${mapsNotification.packageName}")
        android.util.Log.d("NotificationPresentation", "📝 Title: ${mapsNotification.title}")
        android.util.Log.d("NotificationPresentation", "📄 Content: ${mapsNotification.content}")
        android.util.Log.d("NotificationPresentation", "🎯 Icon: ${mapsNotification.largeIcon != null}")

        // Mettre à jour l'icône de direction si disponible
        if (mapsNotification.largeIcon != null) {
            ivDirectionIcon.setImageBitmap(mapsNotification.largeIcon)
            ivDirectionIcon.visibility = android.view.View.VISIBLE
            android.util.Log.d("NotificationPresentation", "✅ Icône de direction affichée")
        } else {
            ivDirectionIcon.visibility = android.view.View.GONE
            android.util.Log.d("NotificationPresentation", "❌ Pas d'icône de direction disponible")
        }
    }

    private fun showPersistentNavigation() {
        if (lastNavigationInstruction != null && lastNavigationDetails != null) {
            cvNavigation.visibility = android.view.View.VISIBLE
            tvNavigationInstruction.text = lastNavigationInstruction
            tvNavigationDetails.text = lastNavigationDetails
            android.util.Log.d("NotificationPresentation", "🗺️ Navigation persistante affichée")
        }
    }

    private fun checkNavigationTimeout() {
        val currentTime = System.currentTimeMillis()
        val navigationAge = currentTime - navigationActiveTime
        val timeoutMinutes = 2 * 60 * 1000L // 2 minutes

        if (navigationAge > timeoutMinutes) {
            android.util.Log.d("NotificationPresentation", "⏰ Navigation timeout (${navigationAge/1000}s)")
            hideNavigation()
        } else {
            android.util.Log.d("NotificationPresentation", "⏰ Navigation encore active (${navigationAge/1000}s)")
            showPersistentNavigation()
        }
    }

    private fun hideNavigation() {
        cvNavigation.visibility = android.view.View.GONE
        lastNavigationInstruction = null
        lastNavigationDetails = null
        navigationActiveTime = 0
        android.util.Log.d("NotificationPresentation", "🗺️ Navigation masquée et données effacées")
    }

    private fun formatNavigationInstruction(notification: NotificationListenerService.NotificationData): String {
        val content = notification.content ?: ""
        val title = notification.title ?: ""

        android.util.Log.d("NotificationPresentation", "🔍 TITRE: '$title'")
        android.util.Log.d("NotificationPresentation", "🔍 CONTENU: '$content'")

        // Utiliser le contenu ou le titre (celui qui n'est pas vide)
        val text = if (content.isNotEmpty()) content else title

        // Maintenant qu'on a l'icône réelle, on affiche juste le texte sans emoji
        return text.ifEmpty { "Navigation en cours" }
    }

    private fun extractDirection(text: String): String {
        android.util.Log.d("NotificationPresentation", "🔍 Analyse du texte pour direction: '$text'")

        return when {
            // Français - variations de "droite"
            text.contains("tournez à droite") || text.contains("tourner à droite") ||
            text.contains("à droite") || text.contains("droite") -> {
                android.util.Log.d("NotificationPresentation", "✅ Direction détectée: DROITE")
                "➡️"
            }
            // Français - variations de "gauche"
            text.contains("tournez à gauche") || text.contains("tourner à gauche") ||
            text.contains("à gauche") || text.contains("gauche") -> {
                android.util.Log.d("NotificationPresentation", "✅ Direction détectée: GAUCHE")
                "⬅️"
            }
            // Français - tout droit
            text.contains("tout droit") || text.contains("continuer") || text.contains("continuez") -> {
                android.util.Log.d("NotificationPresentation", "✅ Direction détectée: TOUT DROIT")
                "⬆️"
            }
            // Demi-tour
            text.contains("demi-tour") || text.contains("u-turn") -> {
                android.util.Log.d("NotificationPresentation", "✅ Direction détectée: DEMI-TOUR")
                "↩️"
            }
            // Sorties et rond-points
            text.contains("sortez") || text.contains("prenez la sortie") || text.contains("rond-point") -> {
                android.util.Log.d("NotificationPresentation", "✅ Direction détectée: SORTIE/ROND-POINT")
                "🔄"
            }
            // Arrivée
            text.contains("arrivée") || text.contains("destination") || text.contains("arrivé") -> {
                android.util.Log.d("NotificationPresentation", "✅ Direction détectée: ARRIVÉE")
                "🏁"
            }
            // Anglais
            text.contains("turn right") -> "➡️"
            text.contains("turn left") -> "⬅️"
            text.contains("continue straight") -> "⬆️"
            text.contains("roundabout") || text.contains("take exit") -> "🔄"
            text.contains("arrived") -> "🏁"
            else -> {
                android.util.Log.d("NotificationPresentation", "❌ Aucune direction spécifique détectée, utilisation générique")
                "🗺️"
            }
        }
    }

    private fun extractDistance(text: String): String {
        val distanceRegex = Regex("(\\d+)\\s*(m|km|metres?|kilometers?)", RegexOption.IGNORE_CASE)
        val match = distanceRegex.find(text)
        return match?.value ?: ""
    }

    private fun extractNavigationDetails(notification: NotificationListenerService.NotificationData): String {
        val content = notification.content ?: ""
        val title = notification.title ?: ""
        val fullText = "$title $content"

        // Essayer d'extraire temps et distance
        val timeRegex = Regex("(\\d+)\\s*min")
        val distanceRegex = Regex("(\\d+[.,]?\\d*)\\s*(km|m)")

        val timeMatch = timeRegex.find(fullText)
        val distanceMatch = distanceRegex.find(fullText)

        val timePart = timeMatch?.let { "⏱️ ${it.value}" } ?: ""
        val distancePart = distanceMatch?.let { "📍 ${it.value}" } ?: ""

        return when {
            timePart.isNotEmpty() && distancePart.isNotEmpty() -> "$timePart • $distancePart"
            timePart.isNotEmpty() -> timePart
            distancePart.isNotEmpty() -> distancePart
            else -> "🗺️ Navigation active"
        }
    }

    // ===== FONCTIONS MANETTE ET MENU =====

    private fun setupGamepadAndMenu() {
        // Initialiser le gestionnaire de manette
        bluetoothGamepadManager = BluetoothGamepadManager(context, gamepadListener)
        bluetoothGamepadManager.startListening()

        // Initialiser le menu d'applications
        appLauncherMenu = AppLauncherMenu(context)
        appLauncherMenu.setNavigationListener(menuNavigationListener)
        appLauncherMenu.loadInstalledApps()

        android.util.Log.d("NotificationPresentation", "🎮 Système manette + menu initialisé")
    }

    private val gamepadListener = object : BluetoothGamepadManager.GamepadListener {
        override fun onGamepadConnected(device: android.view.InputDevice) {
            isGamepadConnected = true
            android.util.Log.d("NotificationPresentation", "🎮 Manette connectée: ${device.name}")

            // Afficher un indicateur de manette connectée
            timeHandler.post {
                tvMenuTitle.text = "🎮 Menu Applications (${device.name})"
                updateGamepadStatus()
            }
        }

        override fun onGamepadDisconnected() {
            isGamepadConnected = false
            android.util.Log.d("NotificationPresentation", "🎮 Manette déconnectée")

            timeHandler.post {
                tvMenuTitle.text = "🎮 Menu Applications"
                updateGamepadStatus()

                // Masquer le menu si ouvert
                if (appLauncherMenu.isMenuVisible()) {
                    appLauncherMenu.hideMenu()
                }
            }
        }

        override fun onButtonPressed(keyCode: Int, event: android.view.KeyEvent) {
            timeHandler.post {
                handleGamepadButton(keyCode, event)
            }
        }

        override fun onJoystickMoved(x: Float, y: Float, event: android.view.MotionEvent) {
            // Gérer les mouvements de joystick si nécessaire
            if (Math.abs(y) > 0.7f) {
                timeHandler.post {
                    if (y < 0) {
                        appLauncherMenu.navigateUp()
                    } else {
                        appLauncherMenu.navigateDown()
                    }
                }
            }
        }

        override fun onDPadPressed(direction: BluetoothGamepadManager.Direction) {
            timeHandler.post {
                when (direction) {
                    BluetoothGamepadManager.Direction.UP -> appLauncherMenu.navigateUp()
                    BluetoothGamepadManager.Direction.DOWN -> appLauncherMenu.navigateDown()
                    BluetoothGamepadManager.Direction.CENTER -> appLauncherMenu.selectCurrentItem()
                    BluetoothGamepadManager.Direction.LEFT -> {
                        if (!appLauncherMenu.isMenuVisible()) {
                            appLauncherMenu.showMenu()
                        }
                    }
                    BluetoothGamepadManager.Direction.RIGHT -> {
                        if (appLauncherMenu.isMenuVisible()) {
                            appLauncherMenu.hideMenu()
                        }
                    }
                }
            }
        }
    }

    private val menuNavigationListener = object : AppLauncherMenu.MenuNavigationListener {
        override fun onMenuItemSelected(selectedIndex: Int, totalItems: Int) {
            updateMenuDisplay()
        }

        override fun onAppLaunched(appInfo: AppInfo) {
            android.util.Log.d("NotificationPresentation", "🚀 App lancée: ${appInfo.name}")
            // Optionnel: afficher un toast ou une confirmation
        }

        override fun onMenuClosed() {
            cvAppMenu.visibility = android.view.View.GONE
            android.util.Log.d("NotificationPresentation", "📋 Menu fermé")
        }
    }

    private fun handleGamepadButton(keyCode: Int, event: android.view.KeyEvent) {
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_START,
            android.view.KeyEvent.KEYCODE_MENU -> {
                // Bouton Menu/Start: afficher/masquer le menu
                if (appLauncherMenu.isMenuVisible()) {
                    appLauncherMenu.hideMenu()
                } else {
                    appLauncherMenu.showMenu()
                }
            }
            android.view.KeyEvent.KEYCODE_BUTTON_B,
            android.view.KeyEvent.KEYCODE_BACK -> {
                // Bouton B/Back: fermer le menu
                if (appLauncherMenu.isMenuVisible()) {
                    appLauncherMenu.hideMenu()
                }
            }
            android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                // Bouton A: sélectionner l'élément
                if (appLauncherMenu.isMenuVisible()) {
                    appLauncherMenu.selectCurrentItem()
                } else {
                    // Si pas de menu, ouvrir le menu
                    appLauncherMenu.showMenu()
                }
            }
        }
    }

    private fun updateGamepadStatus() {
        val statusText = if (isGamepadConnected) {
            "Manette connectée - Appuyez sur START pour le menu"
        } else {
            "Connectez une manette Bluetooth pour accéder au menu"
        }

        tvMenuInstructions.text = if (isGamepadConnected) {
            "🎮 START: Menu • ↕️ D-Pad: Naviguer • 🎯 A: Sélectionner • 🔙 B: Fermer"
        } else {
            "🎮 Connectez une manette Bluetooth pour utiliser le menu"
        }
    }

    private fun updateMenuDisplay() {
        if (!appLauncherMenu.isMenuVisible()) {
            cvAppMenu.visibility = android.view.View.GONE
            return
        }

        cvAppMenu.visibility = android.view.View.VISIBLE
        llAppList.removeAllViews()

        val visibleApps = appLauncherMenu.getVisibleApps(5)

        for ((appInfo, isSelected) in visibleApps) {
            val itemView = android.view.LayoutInflater.from(context)
                .inflate(R.layout.item_app_menu, llAppList, false)

            val tvAppName = itemView.findViewById<TextView>(R.id.tvAppName)
            val ivAppIcon = itemView.findViewById<android.widget.ImageView>(R.id.ivAppIcon)
            val tvSelectionIndicator = itemView.findViewById<TextView>(R.id.tvSelectionIndicator)

            tvAppName.text = appInfo.name
            if (appInfo.icon != null) {
                ivAppIcon.setImageDrawable(appInfo.icon)
            }

            if (isSelected) {
                tvSelectionIndicator.visibility = android.view.View.VISIBLE
                tvAppName.textSize = 18f
                tvAppName.setTextColor(android.graphics.Color.parseColor("#00FF00"))
            } else {
                tvSelectionIndicator.visibility = android.view.View.GONE
                tvAppName.textSize = 16f
                tvAppName.setTextColor(android.graphics.Color.WHITE)
            }

            llAppList.addView(itemView)
        }

        android.util.Log.d("NotificationPresentation", "📋 Menu mis à jour: ${visibleApps.size} apps visibles")
    }

    // Gérer les événements clavier/manette
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        return if (isGamepadConnected && bluetoothGamepadManager.handleKeyEvent(keyCode, event)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        return if (isGamepadConnected && bluetoothGamepadManager.handleMotionEvent(event)) {
            true
        } else {
            super.onGenericMotionEvent(event)
        }
    }

    override fun onStop() {
        super.onStop()
        timeHandler.removeCallbacks(timeUpdateRunnable)

        // Arrêter le suivi GPS
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }

        // Nettoyer la carte
        if (::miniMap.isInitialized) {
            miniMap.onDetach()
        }

        // Arrêter la manette
        if (::bluetoothGamepadManager.isInitialized) {
            bluetoothGamepadManager.stopListening()
        }

        try {
            context.unregisterReceiver(notificationReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }

        android.util.Log.d("NotificationPresentation", "🧹 Ressources nettoyées (GPS + carte + manette)")
    }
}