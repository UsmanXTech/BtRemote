package com.atharok.btremote.domain.usecases

import com.atharok.btremote.common.utils.REMOTE_REPORT_ID
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.repositories.BluetoothRepository
import com.atharok.btremote.domain.repositories.DataStoreRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BluetoothHidServiceUseCase(
    private val bluetoothRepository: BluetoothRepository,
    private val dataStoreRepository: DataStoreRepository
) {

    suspend fun startHidProfile() {
        val autoConnectDeviceAddress: String = dataStoreRepository.getAutoConnectDeviceAddressFlow().first()
        bluetoothRepository.startHidProfile(autoConnectDeviceAddress)
    }

    fun stopHidProfile() {
        bluetoothRepository.stopHidProfile()
    }

    fun isBluetoothServiceRunning(): StateFlow<Boolean> {
        return bluetoothRepository.isBluetoothServiceRunning()
    }

    fun disconnectDevice(): Boolean {
        return bluetoothRepository.disconnectDevice()
    }

    fun getDeviceHidConnectionState(): StateFlow<DeviceHidConnectionState> {
        return bluetoothRepository.getDeviceHidConnectionState()
    }

    fun sendRemoteReport(bytes: ByteArray): Boolean {
        return bluetoothRepository.sendReport(REMOTE_REPORT_ID, bytes)
    }
}