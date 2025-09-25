package com.arglass.notificationdisplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.content.ContextCompat

class BluetoothGamepadManager(
    private val context: Context,
    private val listener: GamepadListener
) {

    companion object {
        private const val TAG = "BluetoothGamepadManager"
    }

    interface GamepadListener {
        fun onGamepadConnected(device: InputDevice)
        fun onGamepadDisconnected()
        fun onButtonPressed(keyCode: Int, event: KeyEvent)
        fun onJoystickMoved(x: Float, y: Float, event: MotionEvent)
        fun onDPadPressed(direction: Direction)
    }

    enum class Direction {
        UP, DOWN, LEFT, RIGHT, CENTER
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val inputManager: InputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private var connectedGamepad: InputDevice? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    Log.d(TAG, "📶 Bluetooth device connected: ${device?.name}")
                    checkForGamepads()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    Log.d(TAG, "📶 Bluetooth device disconnected: ${device?.name}")
                    checkForGamepads()
                }
                "android.hardware.input.action.INPUT_DEVICE_ADDED" -> {
                    val deviceId = intent.getIntExtra("android.hardware.input.extra.INPUT_DEVICE_ID", -1)
                    checkInputDevice(deviceId)
                }
                "android.hardware.input.action.INPUT_DEVICE_REMOVED" -> {
                    val deviceId = intent.getIntExtra("android.hardware.input.extra.INPUT_DEVICE_ID", -1)
                    if (connectedGamepad?.id == deviceId) {
                        Log.d(TAG, "🎮 Gamepad removed: ${connectedGamepad?.name}")
                        connectedGamepad = null
                        listener.onGamepadDisconnected()
                    }
                }
            }
        }
    }

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            checkInputDevice(deviceId)
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            if (connectedGamepad?.id == deviceId) {
                Log.d(TAG, "🎮 Input device removed: ${connectedGamepad?.name}")
                connectedGamepad = null
                listener.onGamepadDisconnected()
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            checkInputDevice(deviceId)
        }
    }

    fun startListening() {
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "❌ Missing Bluetooth permissions")
            return
        }

        // Enregistrer les receivers Bluetooth
        val bluetoothFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("android.hardware.input.action.INPUT_DEVICE_ADDED")
            addAction("android.hardware.input.action.INPUT_DEVICE_REMOVED")
        }

        try {
            context.registerReceiver(bluetoothReceiver, bluetoothFilter)
            inputManager.registerInputDeviceListener(inputDeviceListener, null)
            Log.d(TAG, "📱 Started listening for Bluetooth gamepads")

            // Vérifier immédiatement les appareils déjà connectés
            checkForGamepads()
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception starting Bluetooth listener", e)
        }
    }

    fun stopListening() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
            inputManager.unregisterInputDeviceListener(inputDeviceListener)
            Log.d(TAG, "🔇 Stopped listening for Bluetooth gamepads")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping gamepad listener", e)
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkForGamepads() {
        val inputDeviceIds = InputDevice.getDeviceIds()
        for (deviceId in inputDeviceIds) {
            checkInputDevice(deviceId)
        }
    }

    private fun checkInputDevice(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return

        if (isGameController(device)) {
            Log.d(TAG, "🎮 Gamepad detected: ${device.name} (${device.descriptor})")
            connectedGamepad = device
            listener.onGamepadConnected(device)
        }
    }

    private fun isGameController(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
               (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) ||
               (sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD)
    }

    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (connectedGamepad == null || event.deviceId != connectedGamepad?.id) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "🎮 Button pressed: $keyCode")

            // Gérer les boutons D-Pad
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    listener.onDPadPressed(Direction.UP)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    listener.onDPadPressed(Direction.DOWN)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    listener.onDPadPressed(Direction.LEFT)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    listener.onDPadPressed(Direction.RIGHT)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    listener.onDPadPressed(Direction.CENTER)
                    return true
                }
            }

            // Transmettre l'événement pour les autres boutons
            listener.onButtonPressed(keyCode, event)
            return true
        }

        return false
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (connectedGamepad == null || event.deviceId != connectedGamepad?.id) return false

        // Gérer les joysticks analogiques
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)

        // Seuil pour éviter les micro-mouvements
        if (Math.abs(x) > 0.2f || Math.abs(y) > 0.2f) {
            listener.onJoystickMoved(x, y, event)
            return true
        }

        return false
    }

    fun isGamepadConnected(): Boolean = connectedGamepad != null

    fun getConnectedGamepadName(): String? = connectedGamepad?.name
}