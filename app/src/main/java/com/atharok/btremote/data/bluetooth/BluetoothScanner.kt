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
            if (!checkBluetoothConnectPermission(this@BluetoothScanner.context)) return

            try {
                intent.parcelable(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )?.let { device ->
                    val address = device.address ?: return
                    if (_scannedDevices.value.none { it.macAddress == address }) {
                        val entity = DeviceEntity(
                            name = device.name ?: "Unknown device",
                            macAddress = address,
                            category = device.bluetoothClass?.majorDeviceClass ?: 0
                        )
                        _scannedDevices.value = _scannedDevices.value + entity
                    }
                }
            } catch (exception: SecurityException) {
                Log.w(TAG, "Bluetooth permission revoked during discovery callback", exception)
            } catch (exception: IllegalStateException) {
                Log.w(TAG, "Bluetooth device unavailable during discovery callback", exception)
            }
        }
    }

    fun registerBluetoothScannerReceiver(): Boolean {
        if (receiverRegistered) return true
        return try {
            context.registerReceiver(
                bluetoothScannerReceiver,
                IntentFilter(BluetoothDevice.ACTION_FOUND)
            )
            receiverRegistered = true
            true
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to register Bluetooth discovery receiver", exception)
            receiverRegistered = false
            false
        }
    }

    fun unregisterBluetoothScannerReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(bluetoothScannerReceiver)
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Bluetooth discovery receiver was already unregistered", exception)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Unable to unregister Bluetooth discovery receiver", exception)
        } finally {
            receiverRegistered = false
        }
    }

    fun startDiscoveryDevices(): Boolean {
        if (!checkBluetoothScanPermission(context)) return false
        val started = try {
            adapter?.startDiscovery() == true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Bluetooth scan permission revoked before discovery start", exception)
            false
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Bluetooth adapter is not ready for discovery", exception)
            false
        }
        if (started) _scannedDevices.value = emptyList()
        return started
    }

    fun cancelDiscoveryDevices(): Boolean {
        if (!checkBluetoothScanPermission(context)) return false
        return try {
            adapter?.cancelDiscovery() == true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Bluetooth scan permission revoked before discovery cancellation", exception)
            false
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Bluetooth adapter is not ready to cancel discovery", exception)
            false
        }
    }

    private companion object {
        const val TAG = "BluetoothScanner"
    }
}
