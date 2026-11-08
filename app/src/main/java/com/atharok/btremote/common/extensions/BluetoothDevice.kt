package com.atharok.btremote.common.extensions

import android.bluetooth.BluetoothDevice

/**
 * Android does not expose removeBond() in the public BluetoothDevice API.
 * Return the actual reflection result instead of reporting success merely
 * because the method invocation did not throw.
 */
fun BluetoothDevice.unpair(): Boolean = try {
    javaClass.getMethod("removeBond").invoke(this) as? Boolean == true
} catch (_: ReflectiveOperationException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: IllegalArgumentException) {
    false
}
