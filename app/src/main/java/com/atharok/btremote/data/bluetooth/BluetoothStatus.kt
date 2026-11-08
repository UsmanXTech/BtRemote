package com.atharok.btremote.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.util.Log

class BluetoothStatus(
    private val adapter: BluetoothAdapter?
) {
    fun isBluetoothSupported(): Boolean = adapter != null

    fun isBluetoothEnabled(): Boolean {
        return try {
            adapter?.isEnabled == true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Bluetooth permission unavailable while checking adapter state", exception)
            false
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Bluetooth adapter is not ready", exception)
            false
        }
    }

    private companion object {
        const val TAG = "BluetoothStatus"
    }
}
