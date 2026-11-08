package com.atharok.btremote

import com.atharok.btremote.common.utils.isMacAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothAddressValidationTest {

    @Test
    fun acceptsStandardBluetoothMacAddresses() {
        assertTrue(isMacAddress("AA:BB:CC:DD:EE:FF"))
        assertTrue(isMacAddress("aa:bb:cc:dd:ee:ff"))
        assertTrue(isMacAddress("01:23:45:67:89:AB"))
    }

    @Test
    fun rejectsMalformedBluetoothMacAddresses() {
        assertFalse(isMacAddress(""))
        assertFalse(isMacAddress("AA:BB:CC:DD:EE"))
        assertFalse(isMacAddress("AA-BB-CC-DD-EE-FF"))
        assertFalse(isMacAddress("AA:BB:CC:DD:EE:FG"))
        assertFalse(isMacAddress("not-a-mac"))
    }
}
