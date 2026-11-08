package com.atharok.btremote.domain.usecases

import com.atharok.btremote.common.utils.KEYBOARD_REPORT_ID
import com.atharok.btremote.common.utils.REMOTE_REPORT_ID
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.repositories.BluetoothRepository
import com.atharok.btremote.domain.repositories.DataStoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class BluetoothHidServiceUseCase(
    private val bluetoothRepository: BluetoothRepository,
    private val dataStoreRepository: DataStoreRepository
) {

    suspend fun startHidProfile() {
        val autoConnectDeviceAddress =
            dataStoreRepository.getAutoConnectDeviceAddressFlow().first()
        bluetoothRepository.startHidProfile(autoConnectDeviceAddress)
    }

    fun stopHidProfile() {
        bluetoothRepository.stopHidProfile()
    }

    fun disconnectDevice(): Boolean = bluetoothRepository.disconnectDevice()

    fun getDeviceHidConnectionState(): StateFlow<DeviceHidConnectionState> =
        bluetoothRepository.getDeviceHidConnectionState()

    fun sendReport(id: Int, bytes: ByteArray): Boolean =
        bluetoothRepository.sendReport(id, bytes)

    fun sendRemoteReport(bytes: ByteArray): Boolean =
        sendReport(REMOTE_REPORT_ID, bytes)

    fun sendKeyboardReport(bytes: ByteArray): Boolean =
        sendReport(KEYBOARD_REPORT_ID, bytes)

    suspend fun useEnterForSelection(): Boolean =
        dataStoreRepository.remoteSettingsFlow.first().useEnterForSelection

    fun showRemoteButtonsInNotification(): Flow<Boolean> =
        dataStoreRepository.showRemoteButtonsInNotification()
}
