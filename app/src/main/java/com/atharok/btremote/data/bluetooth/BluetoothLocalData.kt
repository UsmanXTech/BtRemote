package com.atharok.btremote.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import com.atharok.btremote.R
import com.atharok.btremote.common.extensions.unpair
import com.atharok.btremote.common.utils.checkBluetoothConnectPermission
import com.atharok.btremote.common.utils.isMacAddress
import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.resources.Result

class BluetoothLocalData(
    private val context: Context,
    private val adapter: BluetoothAdapter?
) {

    // ---- About local device ----

    fun getLocalDeviceName(): String {
        if (!checkBluetoothConnectPermission(context)) {
            return context.getString(R.string.unknown)
        }
        return try {
            adapter?.name ?: context.getString(R.string.unknown)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Bluetooth permission was revoked while reading local device name", exception)
            context.getString(R.string.unknown)
        }
    }

    // ---- Get Bonded Devices ----

    fun getBondedDevices(): List<DeviceEntity> {
        if (!checkBluetoothConnectPermission(context)) return emptyList()

        return try {
            adapter?.bondedDevices.orEmpty().map { device ->
                DeviceEntity(
                    name = device.name ?: context.getString(R.string.unknown),
                    macAddress = device.address ?: "",
                    category = device.bluetoothClass?.majorDeviceClass ?: 0
                )
            }.filter { isMacAddress(it.macAddress) }
        } catch (exception: SecurityException) {
            Log.w(TAG, "Bluetooth permission was revoked while reading bonded devices", exception)
            emptyList()
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Bluetooth adapter is not ready while reading bonded devices", exception)
            emptyList()
        }
    }

    // ---- Unpair ----

    fun unpairDevice(address: String?): Result<Boolean> {
        val failureMessage = context.getString(R.string.unpair_device_failure)
        if (address == null || !isMacAddress(address) || !checkBluetoothConnectPermission(context)) {
            return Result(value = false, message = failureMessage)
        }

        val isUnpaired = try {
            adapter?.getRemoteDevice(address.uppercase())?.unpair() == true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Bluetooth permission was revoked while unpairing", exception)
            false
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Invalid Bluetooth address while unpairing", exception)
            false
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Bluetooth adapter is not ready while unpairing", exception)
            false
        }

        val messageRes =
            if (isUnpaired) R.string.unpair_device_successful else R.string.unpair_device_failure

        return Result(
            value = isUnpaired,
            message = context.getString(messageRes)
        )
    }

    private companion object {
        const val TAG = "BluetoothLocalData"
    }
}
