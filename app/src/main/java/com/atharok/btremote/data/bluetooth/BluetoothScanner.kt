package com.atharok.btremote.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.atharok.btremote.common.extensions.parcelable
import com.atharok.btremote.common.utils.checkBluetoothConnectPermission
import com.atharok.btremote.common.utils.checkBluetoothScanPermission
import com.atharok.btremote.domain.entities.DeviceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothScanner(
    private val context: Context,
    private val adapter: BluetoothAdapter?
) {
    private val _scannedDevices = MutableStateFlow<List<DeviceEntity>>(emptyList())
    val scannedDevices: StateFlow<List<DeviceEntity>> = _scannedDevices.asStateFlow()
    private var receiverRegistered = false

    private val bluetoothScannerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_FOUND) return

            intent.parcelable(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )?.let { device ->
                if (!checkBluetoothConnectPermission(this@BluetoothScanner.context)) return
                val address = device.address
                if (_scannedDevices.value.none { it.macAddress == address }) {
                    val entity = DeviceEntity(
                        name = device.name ?: "Unknown device",
                        macAddress = address,
                        category = device.bluetoothClass.majorDeviceClass
                    )
                    _scannedDevices.value = _scannedDevices.value + entity
                }
            }
        }
    }

    fun registerBluetoothScannerReceiver() {
        if (receiverRegistered) return
        context.registerReceiver(
            bluetoothScannerReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )
        receiverRegistered = true
    }

    fun unregisterBluetoothScannerReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(bluetoothScannerReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("BluetoothScanner", "Receiver was already unregistered", e)
        } finally {
            receiverRegistered = false
        }
    }

    fun startDiscoveryDevices(): Boolean {
        if (!checkBluetoothScanPermission(context)) return false
        val started = adapter?.startDiscovery() == true
        if (started) _scannedDevices.value = emptyList()
        return started
    }

    fun cancelDiscoveryDevices(): Boolean =
        if (checkBluetoothScanPermission(context)) {
            adapter?.cancelDiscovery() == true
        } else {
            false
        }
}
