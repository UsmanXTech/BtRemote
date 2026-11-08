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

class BluetoothScanner(
    private val context: Context,
    private val adapter: BluetoothAdapter?
) {
    val scannedDevices = mutableStateListOf<DeviceEntity>()
    private var receiverRegistered = false

    // ---- BroadcastReceiver ----

    private val bluetoothScannerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    intent.parcelable(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )?.let { device: BluetoothDevice ->
                        if (checkBluetoothConnectPermission(this@BluetoothScanner.context)) {
                            val address = device.address
                            if (scannedDevices.none { it.macAddress == address }) {
                                scannedDevices.add(
                                    DeviceEntity(
                                        name = device.name ?: "Unknown device",
                                        macAddress = address,
                                        category = device.bluetoothClass.majorDeviceClass
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun registerBluetoothScannerReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(bluetoothScannerReceiver, filter)
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
        if (started) scannedDevices.clear()
        return started
    }

    fun cancelDiscoveryDevices(): Boolean {
        return if (checkBluetoothScanPermission(context)) {
            adapter?.cancelDiscovery() == true
        } else {
            false
        }
    }
}
