package com.atharok.btremote.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.ServiceListener
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.atharok.btremote.common.utils.DELAY_BETWEEN_KEY_PRESSES_IN_MILLIS
import com.atharok.btremote.common.utils.KEYBOARD_REPORT_ID
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.common.utils.checkBluetoothConnectPermission
import com.atharok.btremote.common.utils.isMacAddress
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private var profileGeneration = 0L
    private var connectionAttemptId = 0L
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connectionTimeoutJob: Job? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var autoConnectDeviceAddress: String = ""

    private val _isBluetoothServiceRunning = MutableStateFlow(false)
    val isBluetoothServiceRunning: StateFlow<Boolean> = _isBluetoothServiceRunning

    private val _isBluetoothHidProfileRegistered = MutableStateFlow(false)
    val isBluetoothHidProfileRegistered: StateFlow<Boolean> = _isBluetoothHidProfileRegistered

    private val _deviceHidConnectionState = MutableStateFlow(DeviceHidConnectionState())
    val deviceHidConnectionState: StateFlow<DeviceHidConnectionState> = _deviceHidConnectionState

    // ---- HID Profile ----

    fun startBluetoothHidProfile(autoConnectDeviceAddress: String) {
        this.autoConnectDeviceAddress = autoConnectDeviceAddress
        if (profileProxyRequested || adapter == null) return
        if (!checkBluetoothConnectPermission(context)) {
            _isBluetoothServiceRunning.value = false
            _isBluetoothHidProfileRegistered.value = false
            return
        }

        val generation = ++profileGeneration
        profileProxyRequested = true
        val accepted = try {
            adapter.getProfileProxy(context, createServiceListener(generation), BluetoothProfile.HID_DEVICE)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Failed to request HID profile proxy", exception)
            false
        }
        if (!accepted && generation == profileGeneration) {
            profileProxyRequested = false
            _isBluetoothServiceRunning.value = false
        }
    }

    fun stopBluetoothHidProfile() {
        ++profileGeneration
        ++connectionAttemptId
        cancelConnectionTimeout()

        val hidDevice = bluetoothHidDevice
        bluetoothHidDevice = null
        profileProxyRequested = false

        if (hidDevice != null) {
            if (checkBluetoothConnectPermission(context)) {
                safeBluetoothCall("disconnect HID device", false) {
                    disconnectDevice(hidDevice)
                }
                safeBluetoothCall("unregister HID app", false) {
                    hidDevice.unregisterApp()
                }
            }
            safeBluetoothCall("close HID profile proxy", Unit) {
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
            }
        }

        bluetoothDevice = null
        _deviceHidConnectionState.value = DeviceHidConnectionState()
        _isBluetoothHidProfileRegistered.value = false
        _isBluetoothServiceRunning.value = false
    }

    private fun createServiceListener(generation: Long) = object : ServiceListener {
        override fun onServiceConnected(profile: Int, bluetoothProfile: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hidDevice = bluetoothProfile as? BluetoothHidDevice ?: return

            // Android may deliver a profile callback after the service was stopped.
            if (generation != profileGeneration || !profileProxyRequested) {
                safeBluetoothCall("close stale HID profile proxy", Unit) {
                    adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
                }
                return
            }

            bluetoothHidDevice = hidDevice
            _isBluetoothServiceRunning.value = true
            if (!checkBluetoothConnectPermission(context)) {
                _isBluetoothHidProfileRegistered.value = false
                return
            }

            val registered = safeBluetoothCall("register HID app", false) {
                hidDevice.registerApp(
                    hidSettings,
                    null,
                    null,
                    Runnable::run,
                    createHidCallback(generation)
                )
            }
            _isBluetoothHidProfileRegistered.value = registered
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE || generation != profileGeneration) return

            bluetoothHidDevice = null
            profileProxyRequested = false
            bluetoothDevice = null
            ++connectionAttemptId
            cancelConnectionTimeout()
            _isBluetoothHidProfileRegistered.value = false
            _isBluetoothServiceRunning.value = false
            _deviceHidConnectionState.value = DeviceHidConnectionState()
        }
    }

    private fun createHidCallback(generation: Long) = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            if (generation != profileGeneration || !profileProxyRequested) return

            _isBluetoothHidProfileRegistered.value = registered
            if (registered && isMacAddress(autoConnectDeviceAddress)) {
                connectDevice(autoConnectDeviceAddress)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            if (generation != profileGeneration || !profileProxyRequested) return
            if (!checkBluetoothConnectPermission(context)) {
                _deviceHidConnectionState.value = DeviceHidConnectionState()
                return
            }

            // A connection callback can arrive after the connection timeout. Do not
            // resurrect a timed-out connection as active; ask Android to disconnect it.
            val currentTarget = bluetoothDevice
            if (state == BluetoothProfile.STATE_CONNECTED &&
                device != null &&
                currentTarget == null
            ) {
                safeBluetoothCall("disconnect late HID connection", false) {
                    bluetoothHidDevice?.disconnect(device)
                }
                return
            }

            val deviceName = safeBluetoothCall("read HID device name", "") {
                device?.name ?: ""
            }
            _deviceHidConnectionState.value = DeviceHidConnectionState(state, deviceName)

            if (state == BluetoothProfile.STATE_CONNECTED) {
                cancelConnectionTimeout()
                if (device != null) bluetoothDevice = device
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                cancelConnectionTimeout()
                bluetoothDevice = null
            }
        }
    }

    // ---- Device Connection ----

    fun connectDevice(deviceAddress: String): Boolean {
        if (!checkBluetoothConnectPermission(context)) return false
        if (!isMacAddress(deviceAddress)) return false

        val hidDevice = bluetoothHidDevice ?: return false
        val currentState = _deviceHidConnectionState.value.state
        if (currentState == BluetoothProfile.STATE_CONNECTING ||
            currentState == BluetoothProfile.STATE_CONNECTED
        ) {
            return false
        }
        return connectDevice(hidDevice, deviceAddress)
    }

    fun disconnectDevice(): Boolean {
        if (!checkBluetoothConnectPermission(context)) return false
        val hidDevice = bluetoothHidDevice ?: return false
        ++connectionAttemptId
        cancelConnectionTimeout()
        val disconnected = safeBluetoothCall("disconnect HID device", false) {
            disconnectDevice(hidDevice)
        }
        if (disconnected) {
            bluetoothDevice = null
            _deviceHidConnectionState.value = DeviceHidConnectionState()
        }
        return disconnected
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun connectDevice(hidDevice: BluetoothHidDevice, deviceAddress: String): Boolean {
        val device = safeBluetoothCall("resolve Bluetooth device", null as BluetoothDevice?) {
            adapter?.getRemoteDevice(deviceAddress.uppercase(Locale.ROOT))
        } ?: return false

        val deviceName = safeBluetoothCall("read Bluetooth device name", "") {
            device.name ?: ""
        }
        bluetoothDevice = device
        _deviceHidConnectionState.value = DeviceHidConnectionState(
            BluetoothProfile.STATE_CONNECTING,
            deviceName
        )

        val attemptId = ++connectionAttemptId
        val accepted = safeBluetoothCall("connect HID device", false) {
            hidDevice.connect(device)
        }
        if (!accepted) {
            bluetoothDevice = null
            _deviceHidConnectionState.value = DeviceHidConnectionState()
            return false
        }

        cancelConnectionTimeout()
        connectionTimeoutJob = connectionScope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (attemptId == connectionAttemptId &&
                _deviceHidConnectionState.value.state == BluetoothProfile.STATE_CONNECTING
            ) {
                safeBluetoothCall("disconnect timed-out HID device", false) {
                    hidDevice.disconnect(device)
                }
                bluetoothDevice = null
                _deviceHidConnectionState.value = DeviceHidConnectionState()
            }
            if (attemptId == connectionAttemptId) connectionTimeoutJob = null
        }
        return true
    }

    private fun cancelConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun disconnectDevice(hidDevice: BluetoothHidDevice): Boolean {
        val device = bluetoothDevice ?: return false

        // Preserve the existing Pixel 3a workaround: the second disconnect result
        // is used because some devices need a repeated request.
        safeBluetoothCall("first HID disconnect request", false) {
            hidDevice.disconnect(device)
        }
        return safeBluetoothCall("second HID disconnect request", false) {
            hidDevice.disconnect(device)
        }
    }

    // ---- Send Report ----

    fun sendReport(id: Int, bytes: ByteArray): Boolean {
        if (!checkBluetoothConnectPermission(context)) return false
        if (_deviceHidConnectionState.value.state != BluetoothProfile.STATE_CONNECTED) return false
        val device = bluetoothDevice ?: return false
        val hidDevice = bluetoothHidDevice ?: return false
        return safeBluetoothCall("send HID report", false) {
            hidDevice.sendReport(device, id, bytes)
        }
    }

    private fun safeSendReport(
        hidDevice: BluetoothHidDevice,
        device: BluetoothDevice,
        reportId: Int,
        bytes: ByteArray
    ): Boolean {
        if (!checkBluetoothConnectPermission(context)) return false
        return safeBluetoothCall("send keyboard HID report", false) {
            hidDevice.sendReport(device, reportId, bytes)
        }
    }

    private suspend fun sendKeyAndRelease(
        hidDevice: BluetoothHidDevice,
        device: BluetoothDevice,
        keyReport: ByteArray
    ): Boolean {
        var success = false
        var keyDownAttempted = false

        try {
            keyDownAttempted = true
            success = safeSendReport(hidDevice, device, KEYBOARD_REPORT_ID, keyReport)
            delay(DELAY_BETWEEN_KEY_PRESSES_IN_MILLIS)
        } finally {
            // Always attempt key-up even if the delay is cancelled. Bluetooth errors
            // are contained by safeSendReport so they cannot mask cancellation.
            if (keyDownAttempted &&
                !safeSendReport(hidDevice, device, KEYBOARD_REPORT_ID, REMOTE_INPUT_NONE)
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
        if (!checkBluetoothConnectPermission(context)) return false
        if (_deviceHidConnectionState.value.state != BluetoothProfile.STATE_CONNECTED) return false

        val device = bluetoothDevice ?: return false
        val hidDevice = bluetoothHidDevice ?: return false
        var success = true

        try {
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
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Text HID report failed", exception)
            return false
        }

        return success
    }

    private inline fun <T> safeBluetoothCall(
        operation: String,
        fallback: T,
        call: () -> T
    ): T = try {
        call()
    } catch (exception: SecurityException) {
        Log.w(TAG, "Bluetooth permission/security failure: $operation", exception)
        fallback
    } catch (exception: IllegalArgumentException) {
        Log.w(TAG, "Invalid Bluetooth argument: $operation", exception)
        fallback
    } catch (exception: IllegalStateException) {
        Log.w(TAG, "Bluetooth stack is not ready: $operation", exception)
        fallback
    }

    private companion object {
        const val TAG = "BluetoothHidCore"
        const val CONNECTION_TIMEOUT_MS = 15_000L
    }
}
