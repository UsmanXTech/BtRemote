package com.atharok.btremote.domain.repositories

import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import com.atharok.btremote.domain.resources.Result
import kotlinx.coroutines.flow.StateFlow

interface BluetoothRepository {
    fun isBluetoothSupported(): Boolean
    fun isBluetoothEnabled(): Boolean

    // Device discovery
    fun getScannedDevices(): StateFlow<List<DeviceEntity>>
    fun registerBluetoothScannerReceiver(): Boolean
    fun unregisterBluetoothScannerReceiver()
    fun startDiscovery(): Boolean
    fun cancelDiscovery(): Boolean

    // Local Bluetooth data
    fun getLocalDeviceName(): String
    fun getBondedDevices(): List<DeviceEntity>
    fun unpairDevice(address: String?): Result<Boolean>

    // Bluetooth HID
    fun startHidProfile(autoConnectDeviceAddress: String)
    fun stopHidProfile()
    fun isBluetoothServiceRunning(): StateFlow<Boolean>
    fun isBluetoothHidProfileRegistered(): StateFlow<Boolean>
    fun connectDevice(deviceAddress: String): Boolean
    fun disconnectDevice(): Boolean
    fun getDeviceHidConnectionState(): StateFlow<DeviceHidConnectionState>
    fun sendReport(id: Int, bytes: ByteArray): Boolean
    suspend fun sendTextReport(
        text: String,
        virtualKeyboardLayout: VirtualKeyboardLayout,
        shouldSendEnter: Boolean
    ): Boolean
}
