package com.atharok.btremote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.btremote.common.extensions.capitalizeFirstChar
import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.resources.Result
import com.atharok.btremote.domain.usecases.DeviceSelectionUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DeviceSelectionViewModel(
    private val useCase: DeviceSelectionUseCase
) : ViewModel() {

    // ---- Connection ----

    fun connectDevice(macAddress: String): Boolean = useCase.connectDevice(macAddress)

    fun disconnectDevice(): Boolean = useCase.disconnectDevice()

    // ---- Get Bonded Devices ----

    private val _devicesFlow: MutableStateFlow<List<DeviceEntity>> = MutableStateFlow(emptyList())
    val devicesFlow: StateFlow<List<DeviceEntity>> = _devicesFlow

    fun findBondedDevices() = viewModelScope.launch {
        _devicesFlow.value = useCase.getBondedDevices().sortedBy { it.name.capitalizeFirstChar() }
    }

    // ---- Unpair device ----

    fun unpairDevice(address: String?): Result<Boolean> {
        val result = useCase.unpairDevice(address)
        if (result.value) {
            // Do not block the main thread while Android updates the bond list.
            viewModelScope.launch {
                delay(200L)
                findBondedDevices()
            }
        }
        return result
    }

    // ---- Settings ----

    fun getFavoriteDevices(): Flow<List<String>> = useCase.getFavoriteDevices()

    fun saveFavoriteDevices(macAddresses: List<String>) = viewModelScope.launch {
        useCase.saveFavoriteDevices(macAddresses)
    }

    fun getAutoConnectDeviceAddressFlow(): Flow<String> = useCase.getAutoConnectDeviceAddressFlow()

    fun saveAutoConnectDeviceAddress(address: String) = viewModelScope.launch {
        useCase.saveAutoConnectDeviceAddress(address)
    }
}
