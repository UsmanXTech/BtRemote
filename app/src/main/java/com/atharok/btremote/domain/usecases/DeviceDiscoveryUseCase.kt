package com.atharok.btremote.domain.usecases

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.repositories.BluetoothRepository
import kotlinx.coroutines.flow.StateFlow

class DeviceDiscoveryUseCase(
    private val bluetoothRepository: BluetoothRepository,
) {

    // ---- Connection ----

    fun connectDevice(deviceAddress: String): Boolean {
        return bluetoothRepository.connectDevice(deviceAddress)
    }

    fun disconnectDevice(): Boolean {
        return bluetoothRepository.disconnectDevice()
    }

    // ---- Discover Devices ----

    fun getScannedDevices(): SnapshotStateList<DeviceEntity> = bluetoothRepository.getScannedDevices()

    fun getScannedDevicesState(): StateFlow<List<DeviceEntity>> = bluetoothRepository.getScannedDevicesState()

    fun registerBluetoothScannerReceiver() {
        bluetoothRepository.registerBluetoothScannerReceiver()
    }

    fun unregisterBluetoothScannerReceiver() {
        bluetoothRepository.unregisterBluetoothScannerReceiver()
    }

    fun startDiscovery(): Boolean = bluetoothRepository.startDiscovery()

    fun cancelDiscovery(): Boolean = bluetoothRepository.cancelDiscovery()
}