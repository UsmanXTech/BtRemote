# Product Guidelines - BtRemote

## Architecture
- **Layered Design**: Separation of concerns between UI (Presentation), Use Cases (Domain), and Hardware/Data (Data).
- **Dependency Injection**: Always use Koin for managing dependencies.
- **Service Consistency**: Ensure the `BluetoothHidService` is the source of truth for the HID profile state.

## UI/UX
- **Visuals**: Material 3 components and themes.
- **Responsiveness**: Immediate feedback for user input.
- **Accessibility**: Clear labels and high-contrast icons for remote control.

## Performance
- **Sensors**: Minimal delay but balanced with power consumption.
- **Bluetooth**: Efficient report transmission, avoiding redundant or slow operations.
- **Memory**: Proactive cleanup of listeners and receivers in `onDispose` or lifecycle events.

## Safety & Stability
- **Permissions**: Always check for Bluetooth permissions before calling system APIs.
- **Error Handling**: Graceful degradation when Bluetooth is unavailable or not supported.
