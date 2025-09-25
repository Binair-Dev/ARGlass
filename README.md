# ARGlass Notification Display

Une application Android qui affiche les notifications en temps réel sur un écran externe connecté en USB-C, optimisée pour les lunettes AR.

## Fonctionnalités

- **Détection automatique d'écran externe USB-C** : L'application détecte automatiquement les écrans connectés via USB-C
- **Service en arrière-plan** : Fonctionne en permanence pour capturer les notifications
- **Affichage des notifications** : Affiche les dernières notifications avec le nom de l'app, titre et contenu
- **Heure et date** : Affichage permanent de l'heure et de la date
- **Optimisé pour lunettes AR** : Fond transparent, interface adaptée
- **Démarrage automatique** : Lance le service au démarrage du téléphone

## Installation

1. Construire le projet avec Android Studio
2. Installer l'APK sur votre appareil Android
3. Accorder les permissions nécessaires :
   - Accès aux notifications (obligatoire)
   - Affichage par-dessus d'autres applications
   - Démarrage au boot

## Utilisation

1. **Première configuration** :
   - Ouvrir l'application
   - Appuyer sur "Activer l'accès aux notifications"
   - Sélectionner "ARGlass Notification Display" dans les paramètres

2. **Connecter un écran externe** :
   - Connecter votre écran ou lunettes AR via USB-C
   - L'application détectera automatiquement l'écran externe

3. **Démarrer le service** :
   - Appuyer sur "Démarrer le service" dans l'application
   - Le service restera actif en arrière-plan

## Architecture

### Composants principaux

- `MainActivity.kt` : Interface principale de configuration
- `ExternalDisplayService.kt` : Service en arrière-plan qui gère l'affichage externe
- `NotificationListenerService.kt` : Capture les notifications système
- `ExternalDisplayActivity.kt` : Interface affichée sur l'écran externe
- `NotificationAdapter.kt` : Gère l'affichage des notifications dans la liste
- `BootReceiver.kt` : Démarre automatiquement le service au boot

### Permissions requises

```xml
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

## Configuration pour lunettes AR

L'application est optimisée pour les lunettes AR avec :
- Fond transparent (`Theme.Transparent`)
- Texte avec ombre pour la lisibilité
- Interface minimaliste
- Positionnement optimisé des éléments

## Compatibilité

- Android 8.0 (API 26) et supérieur
- Écrans externes via USB-C (DisplayPort Alt Mode)
- Lunettes AR compatibles Android

## Développement

Le projet utilise :
- Kotlin
- Android Jetpack Components
- RecyclerView pour l'affichage des notifications
- Services et BroadcastReceivers pour la gestion en arrière-plan

Pour compiler :
```bash
./gradlew assembleDebug
```

## Notes importantes

- L'accès aux notifications doit être accordé manuellement par l'utilisateur
- Le service fonctionne en arrière-plan et consomme de la batterie
- Les notifications de l'application elle-même sont filtrées
- Compatible avec la plupart des lunettes AR supportant Android