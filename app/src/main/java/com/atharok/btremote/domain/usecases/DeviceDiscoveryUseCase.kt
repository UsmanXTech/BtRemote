package com.atharok.btremote.domain.usecases

import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.repositories.BluetoothRepository
import kotlinx.coroutines.flow.StateFlow

class DeviceDiscoveryUseCase(
    private val bluetoothRepository: BluetoothRepository,
) {
    fun connectDevice(deviceAddress: String): Boolean = bluetoothRepository.connectDevice(deviceAddress)
    fun disconnectDevice(): Boolean = bluetoothRepository.disconnectDevice()

    fun getScannedDevices(): StateFlow<List<DeviceEntity>> = bluetoothRepository.getScannedDevices()
    fun registerBluetoothScannerReceiver() = bluetoothRepository.registerBluetoothScannerReceiver()
    fun unregisterBluetoothScannerReceiver() = bluetoothRepository.unregisterBluetoothScannerReceiver()
    fun startDiscovery(): Boolean = bluetoothRepository.startDiscovery()
    fun cancelDiscovery(): Boolean = bluetoothRepository.cancelDiscovery()
}
