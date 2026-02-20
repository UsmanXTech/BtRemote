package com.atharok.btremote.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.atharok.btremote.common.extensions.parcelable
import com.atharok.btremote.common.utils.checkBluetoothConnectPermission
import com.atharok.btremote.common.utils.checkBluetoothScanPermission
import com.atharok.btremote.domain.entities.DeviceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BluetoothScanner(
    private val context: Context,
    private val adapter: BluetoothAdapter?
) {
    private val _scannedDevicesState = MutableStateFlow<List<DeviceEntity>>(emptyList())
    val scannedDevicesState: StateFlow<List<DeviceEntity>> = _scannedDevicesState.asStateFlow()

    @Deprecated("Use scannedDevicesState instead for better architectural separation", ReplaceWith("scannedDevicesState"))
    val scannedDevices = mutableStateListOf<DeviceEntity>()

    private val scannedAddresses = HashSet<String>()

    // ---- BroadcastReceiver ----

    private val bluetoothScannerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    intent.parcelable(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)?.let { device: BluetoothDevice ->
                        if(checkBluetoothConnectPermission(this@BluetoothScanner.context)) {
                            val address = device.address ?: return@let
                            if(scannedAddresses.add(address)) {
                                val entity = DeviceEntity(
                                    name = device.name ?: "null",
                                    macAddress = address,
                                    category = device.bluetoothClass.majorDeviceClass
                                )
                                _scannedDevicesState.update { it + entity }
                                // Keep legacy support for now
                                scannedDevices.add(entity)
                            }
                        }
                    }
                }
            }
        }
    }

    fun registerBluetoothScannerReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(bluetoothScannerReceiver, filter)
    }

    fun unregisterBluetoothScannerReceiver() {
        try {
            context.unregisterReceiver(bluetoothScannerReceiver)
        } catch (e: java.lang.RuntimeException) {
            Log.e("unregisterReceiver()", "Receiver already unregister: ${e.message ?: e.toString()}")
        }
    }

    fun clearScannedDevices() {
        scannedAddresses.clear()
        _scannedDevicesState.value = emptyList()
        scannedDevices.clear()
    }

    fun startDiscoveryDevices(): Boolean {
        return if (checkBluetoothScanPermission(context)) {
            clearScannedDevices()
            adapter?.startDiscovery() == true
        } else false
    }

    fun cancelDiscoveryDevices(): Boolean {
        return if (checkBluetoothScanPermission(context)) {
            adapter?.cancelDiscovery() == true
        } else false
    }
}