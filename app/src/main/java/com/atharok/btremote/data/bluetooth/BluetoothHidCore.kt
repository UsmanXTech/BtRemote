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
import com.atharok.btremote.common.utils.DiagnosticLogger
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
        DiagnosticLogger.info("bluetooth.hid_profile_start_requested")
        this.autoConnectDeviceAddress = autoConnectDeviceAddress
        if (profileProxyRequested || adapter == null) {
            DiagnosticLogger.warn("bluetooth.hid_profile_start_skipped", "already_requested_or_adapter_missing")
            return
        }
        if (!checkBluetoothConnectPermission(context)) {
            DiagnosticLogger.error("bluetooth.permission_missing", "connect_permission_required_for_hid_profile")
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
        DiagnosticLogger.info("bluetooth.hid_profile_proxy_request_result", "accepted=$accepted")
        if (!accepted && generation == profileGeneration) {
            profileProxyRequested = false
            _isBluetoothServiceRunning.value = false
        }
    }

    fun stopBluetoothHidProfile() {
        DiagnosticLogger.info("bluetooth.hid_profile_stop_requested")
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
            DiagnosticLogger.info("bluetooth.hid_profile_connected")
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
            DiagnosticLogger.info("bluetooth.hid_app_registration_result", "registered=$registered")
            _isBluetoothHidProfileRegistered.value = registered
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE || generation != profileGeneration) return

            DiagnosticLogger.warn("bluetooth.hid_profile_disconnected")
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

            DiagnosticLogger.info("bluetooth.hid_app_status_changed", "registered=$registered")
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

            val callbackDevice = device ?: return
            val currentTarget = bluetoothDevice
            val callbackAddress = safeBluetoothCall("read callback device address", null as String?) {
                callbackDevice.address
            }
            val targetAddress = safeBluetoothCall("read active target address", null as String?) {
                currentTarget?.address
            }

            // HID callbacks can be delayed and arrive out of order. Accept state changes
            // only for the device belonging to the currently active connection attempt.
            if (currentTarget == null || callbackAddress == null || targetAddress == null ||
                !callbackAddress.equals(targetAddress, ignoreCase = true)
            ) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    safeBluetoothCall("disconnect stale HID connection", false) {
                        bluetoothHidDevice?.disconnect(callbackDevice)
                    }
                }
                return
            }

            val deviceName = safeBluetoothCall("read HID device name", "") {
                callbackDevice.name ?: ""
            }
            DiagnosticLogger.info("bluetooth.hid_connection_state", "state=$state")
            _deviceHidConnectionState.value = DeviceHidConnectionState(state, deviceName)

            if (state == BluetoothProfile.STATE_CONNECTED) {
                cancelConnectionTimeout()
                bluetoothDevice = callbackDevice
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                cancelConnectionTimeout()
                bluetoothDevice = null
            }
        }
    }

    // ---- Device Connection ----

    fun connectDevice(deviceAddress: String): Boolean {
        DiagnosticLogger.info("bluetooth.connect_requested")
        if (!checkBluetoothConnectPermission(context)) {
            DiagnosticLogger.error("bluetooth.connect_rejected", "missing_connect_permission")
            return false
        }
        if (!isMacAddress(deviceAddress)) {
            DiagnosticLogger.warn("bluetooth.connect_rejected", "invalid_device_address")
            return false
        }

        val hidDevice = bluetoothHidDevice ?: run {
            DiagnosticLogger.warn("bluetooth.connect_rejected", "hid_profile_unavailable")
            return false
        }
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

        // Do not invalidate the connection timeout until Android accepts the disconnect.
        // Otherwise a rejected disconnect can strand the state in CONNECTING forever.
        val disconnected = safeBluetoothCall("disconnect HID device", false) {
            disconnectDevice(hidDevice)
        }
        if (disconnected) {
            ++connectionAttemptId
            cancelConnectionTimeout()
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
        DiagnosticLogger.info("bluetooth.connect_attempt_started")
        val accepted = safeBluetoothCall("connect HID device", false) {
            hidDevice.connect(device)
        }
        if (!accepted) {
            DiagnosticLogger.error("bluetooth.connect_request_failed", "android_rejected_hid_connect")
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
                DiagnosticLogger.error("bluetooth.connect_timeout", "timeout_ms=$CONNECTION_TIMEOUT_MS")
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

        // Some devices need a repeated disconnect request. Treat either accepted request
        // as success; the second call may legitimately return false after the first worked.
        val firstAccepted = safeBluetoothCall("first HID disconnect request", false) {
            hidDevice.disconnect(device)
        }
        val secondAccepted = safeBluetoothCall("second HID disconnect request", false) {
            hidDevice.disconnect(device)
        }
        return firstAccepted || secondAccepted
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
        DiagnosticLogger.error("bluetooth.operation_security_failure", operation, exception)
        Log.w(TAG, "Bluetooth permission/security failure: $operation", exception)
        fallback
    } catch (exception: IllegalArgumentException) {
        DiagnosticLogger.warn("bluetooth.operation_invalid_argument", operation, exception)
        Log.w(TAG, "Invalid Bluetooth argument: $operation", exception)
        fallback
    } catch (exception: IllegalStateException) {
        DiagnosticLogger.error("bluetooth.stack_not_ready", operation, exception)
        Log.w(TAG, "Bluetooth stack is not ready: $operation", exception)
        fallback
    }

    private companion object {
        const val TAG = "BluetoothHidCore"
        const val CONNECTION_TIMEOUT_MS = 15_000L
    }
}
