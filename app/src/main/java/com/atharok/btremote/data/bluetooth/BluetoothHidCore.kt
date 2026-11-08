package com.atharok.btremote.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.ServiceListener
import android.content.Context
import androidx.annotation.RequiresPermission
import com.atharok.btremote.common.utils.DELAY_BETWEEN_KEY_PRESSES_IN_MILLIS
import com.atharok.btremote.common.utils.KEYBOARD_REPORT_ID
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.common.utils.checkBluetoothConnectPermission
import com.atharok.btremote.common.utils.isMacAddress
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class BluetoothHidCore(
    private val context: Context,
    private val adapter: BluetoothAdapter?,
    private val hidSettings: BluetoothHidDeviceAppSdpSettings
) {
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var profileProxyRequested = false

    private val _isBluetoothServiceRunning: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isBluetoothServiceRunning: StateFlow<Boolean> = _isBluetoothServiceRunning

    private val _isBluetoothHidProfileRegistered: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isBluetoothHidProfileRegistered: StateFlow<Boolean> = _isBluetoothHidProfileRegistered

    private var autoConnectDeviceAddress: String = ""

    // ---- HID Profile ----

    fun startBluetoothHidProfile(autoConnectDeviceAddress: String) {
        this.autoConnectDeviceAddress = autoConnectDeviceAddress
        if (profileProxyRequested || adapter == null) return

        profileProxyRequested = adapter.getProfileProxy(
            context,
            serviceListener,
            BluetoothProfile.HID_DEVICE
        )
    }

    fun stopBluetoothHidProfile() {
        bluetoothHidDevice?.let { hidDevice ->
            if (checkBluetoothConnectPermission(context)) {
                disconnectDevice(hidDevice)
                hidDevice.unregisterApp()
            }
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        }
        bluetoothHidDevice = null
        profileProxyRequested = false
        bluetoothDevice = null
        _deviceHidConnectionState.value = DeviceHidConnectionState()
        _isBluetoothHidProfileRegistered.value = false
        _isBluetoothServiceRunning.value = false
    }

    // ---- BluetoothProfile.ServiceListener implementation ----

    private val serviceListener = object : ServiceListener {
        override fun onServiceConnected(profile: Int, bluetoothProfile: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE) return

            bluetoothHidDevice = bluetoothProfile as? BluetoothHidDevice
            bluetoothHidDevice?.let { hidDevice ->
                if (checkBluetoothConnectPermission(context)) {
                    _isBluetoothHidProfileRegistered.value =
                        hidDevice.registerApp(hidSettings, null, null, Runnable::run, callback)
                }
            }
            _isBluetoothServiceRunning.value = bluetoothHidDevice != null
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return

            bluetoothHidDevice = null
            profileProxyRequested = false
            bluetoothDevice = null
            _isBluetoothHidProfileRegistered.value = false
            _isBluetoothServiceRunning.value = false
            _deviceHidConnectionState.value = DeviceHidConnectionState()
        }
    }

    // ---- BluetoothHidDevice.Callback implementation ----

    private val callback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            _isBluetoothHidProfileRegistered.value = registered
            if (registered && isMacAddress(autoConnectDeviceAddress)) {
                connectDevice(autoConnectDeviceAddress)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            if (checkBluetoothConnectPermission(context)) {
                _deviceHidConnectionState.value = DeviceHidConnectionState(state, device?.name ?: "")
            }
        }
    }

    // ---- Device Connection ----

    private var bluetoothDevice: BluetoothDevice? = null

    fun connectDevice(deviceAddress: String): Boolean {
        if (checkBluetoothConnectPermission(context)) {
            bluetoothHidDevice?.let {
                return connectDevice(it, deviceAddress)
            }
        }
        return false
    }

    fun disconnectDevice(): Boolean {
        if (checkBluetoothConnectPermission(context)) {
            bluetoothHidDevice?.let {
                return disconnectDevice(it)
            }
        }
        return false
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun connectDevice(hidDevice: BluetoothHidDevice, deviceAddress: String): Boolean {
        bluetoothDevice = adapter?.getRemoteDevice(deviceAddress.uppercase(Locale.getDefault()))
        return bluetoothDevice?.let { hidDevice.connect(it) } ?: false
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun disconnectDevice(hidDevice: BluetoothHidDevice): Boolean {
        val device = bluetoothDevice ?: return false

        // Keep the Pixel 3a workaround documented above: that device may require
        // a second disconnect request to reach DISCONNECTED.
        hidDevice.disconnect(device)
        val isDisconnected = hidDevice.disconnect(device)
        bluetoothDevice = null
        return isDisconnected
    }

    // ---- StateFlow ----

    private val _deviceHidConnectionState: MutableStateFlow<DeviceHidConnectionState> =
        MutableStateFlow(DeviceHidConnectionState())
    val deviceHidConnectionState: StateFlow<DeviceHidConnectionState> = _deviceHidConnectionState

    // ---- Send Report ----

    fun sendReport(id: Int, bytes: ByteArray): Boolean {
        val device = bluetoothDevice ?: return false
        val hidDevice = bluetoothHidDevice ?: return false
        return checkBluetoothConnectPermission(context) && hidDevice.sendReport(device, id, bytes)
    }

    private suspend fun sendKeyAndRelease(
        hidDevice: BluetoothHidDevice,
        device: BluetoothDevice,
        keyReport: ByteArray
    ): Boolean {
        var success = true
        var keyDownAttempted = false

        try {
            keyDownAttempted = true
            success = hidDevice.sendReport(device, KEYBOARD_REPORT_ID, keyReport)
            delay(DELAY_BETWEEN_KEY_PRESSES_IN_MILLIS)
        } finally {
            // Always attempt a key-up report, including when the coroutine is
            // cancelled during the key-down delay.
            if (keyDownAttempted &&
                !hidDevice.sendReport(device, KEYBOARD_REPORT_ID, REMOTE_INPUT_NONE)
            ) {
                success = false
            }
        }

        return success
    }

    suspend fun sendTextReport(
        text: String,
        virtualKeyboardLayout: VirtualKeyboardLayout,
        shouldSendEnter: Boolean
    ): Boolean {
        val device = bluetoothDevice ?: return false
        val hidDevice = bluetoothHidDevice ?: return false
        if (!checkBluetoothConnectPermission(context)) return false

        var success = true
        text.forEach { character ->
            if (!sendKeyAndRelease(
                    hidDevice,
                    device,
                    virtualKeyboardLayout.getKeyboardKey(character)
                )
            ) {
                success = false
            }
            delay(DELAY_BETWEEN_KEY_PRESSES_IN_MILLIS)
        }

        if (shouldSendEnter &&
            !sendKeyAndRelease(
                hidDevice,
                device,
                RemoteButtonProperties.KeyboardEnterButton.input
            )
        ) {
            success = false
        }

        return success
    }
}
