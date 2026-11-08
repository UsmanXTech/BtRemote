package com.atharok.btremote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.usecases.DeviceDiscoveryUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DeviceDiscoveryViewModel(
    private val useCase: DeviceDiscoveryUseCase
): ViewModel() {
    fun connectDevice(macAddress: String): Boolean = useCase.connectDevice(macAddress)
    fun disconnectDevice(): Boolean = useCase.disconnectDevice()

    val scannedDevices: StateFlow<List<DeviceEntity>> get() = useCase.getScannedDevices()

    fun registerBluetoothScannerReceiver(): Boolean = useCase.registerBluetoothScannerReceiver()
    fun unregisterBluetoothScannerReceiver() = useCase.unregisterBluetoothScannerReceiver()

    private val _isDiscoveringFlow = MutableStateFlow(false)
    val isDiscoveringFlow: StateFlow<Boolean> = _isDiscoveringFlow

    private var discoveryTimeoutJob: Job? = null

    fun startDiscovery() {
        cancelDiscovery()
        val started = useCase.startDiscovery()
        _isDiscoveringFlow.value = started
        if (!started) return

        discoveryTimeoutJob = viewModelScope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            stopDiscoveryAfterTimeout()
        }
    }

    fun cancelDiscovery() {
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = null
        useCase.cancelDiscovery()
        _isDiscoveringFlow.value = false
    }

    private fun stopDiscoveryAfterTimeout() {
        discoveryTimeoutJob = null
        useCase.cancelDiscovery()
        _isDiscoveringFlow.value = false
    }

    override fun onCleared() {
        cancelDiscovery()
        super.onCleared()
    }

    private companion object {
        const val DISCOVERY_TIMEOUT_MS = 12_000L
    }
}
