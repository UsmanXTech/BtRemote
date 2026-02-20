# Track 001: Stability and Optimization

## Specification
Ensure the BtRemote application is robust, uses resources efficiently, and avoids memory leaks.

## Plan
- [Implementation Plan](./plan.md)

## Status
- [x] BluetoothHidCore - Handle HID profile disconnection correctly and manage 'bluetoothDevice' state in 'onConnectionStateChanged'.
- [x] BluetoothHidService - Use SupervisorJob and Dispatchers.Main.immediate in serviceScope, and stop self if Bluetooth is disabled.
- [x] GyroscopeSensor - Use SENSOR_DELAY_GAME for efficiency and ensure listener registration only if sensor exists.
- [x] BluetoothScanner - Stop discovery on app backgrounding (ON_STOP) and clear scanned devices list on new discovery for memory efficiency.
- [x] sendTextReport - Optimize text transmission to 15ms delay per characters for better responsiveness.
- [x] BluetoothStatus - Reactivity is handled by UI components and service state monitoring.
