# Implementation Plan: Stability and Optimization

## Objective
Ensure the application is stable, efficient, and resource-aware.

## Phase 1: Bluetooth HID Robustness
- [x] Correct HID Profile Disconnection: Set `_isBluetoothServiceRunning.value = false` and `_isBluetoothHidProfileRegistered.value = false` in `onServiceDisconnected`.
- [x] Manage 'bluetoothDevice' state: Set `bluetoothDevice = null` on disconnection and update it on connection in the callback.
- [x] Improve Coroutine Management in Service: Use a `serviceScope` with `SupervisorJob` and `Dispatchers.Main.immediate` in `BluetoothHidService`.
- [x] Service Lifecycle Monitoring: Stop service if Bluetooth is disabled or profile is stopped.
- [x] BluetoothScanner Cleanup: Ensure `unregisterBluetoothScannerReceiver` and `cancelDiscovery` are always called when the scanner is no longer needed or when the app is backgrounded.
- [x] Memory Efficiency: Clear scanned devices list when starting a new discovery.

## Phase 2: Sensor Efficiency
- [x] Update Gyroscope Sensor Delay: Use `SensorManager.SENSOR_DELAY_GAME` instead of a hardcoded value to balance power and performance.
- [x] Sensor Lifecycle: Verify `stopListening()` is called consistently when the sensor is not needed (e.g., when the air mouse is disabled) via `DisposableEffect` in UI.

## Phase 3: Performance & Optimization
- [x] Text Report Optimization: Reduced `DELAY_BETWEEN_KEY_PRESSES_IN_MILLIS` to 15ms for better transmission responsiveness.
- [x] BluetoothStatus reactivity: Handled by `CheckBluetoothStateChanged` in UI and `isBluetoothServiceRunning` monitoring in the service.

## Phase 4: Advanced Stability & Resource Optimization
- [x] Non-blocking HID Profile Startup: Refactored `BluetoothHidServiceUseCase` to use `suspend` functions and removed `runBlocking` from service startup.
- [x] Memory Allocation Optimization: Redesigned `MousePadLayout` to use primitive states, eliminating frequent `MouseScrolling` object allocations during air mouse/touchpad use.
- [x] Encapsulation Fix: Moved `tapTimestamp` into `MousePad` Composable state to ensure correct behavior across multiple instances.
- [x] Scanner Efficiency: Optimized `BluetoothScanner` with `HashSet` for $O(1)$ duplicate checks and decoupled it from Compose using `StateFlow`.
- [x] Efficient Widget Updates: Added state change checks in `BluetoothHidService` to prevent redundant Glance widget updates.

## Status Summary
- **Phase 1**: Completed
- **Phase 2**: Completed
- **Phase 3**: Completed
- **Phase 4**: Completed
