# Track 002: Remote Interaction and UX

## Specification
Improve the user experience during remote interactions by providing better visual feedback, optimizing input response, and ensuring the UI remains responsive during Bluetooth operations.

## Plan
- [Implementation Plan](./plan.md)

## Status
- [x] Add visual feedback (vibration/haptic) for remote button presses - Integrated in BasicButton and SwipeNavigation.
- [x] Optimize the Remote screen layout for different device orientations - Already handled via portrait/landscape views, settings propagated.
- [x] Investigate and implement adjustable sensitivity for "Air Mouse" - gyroscopeSensitivity added to DataStore and applied in MouseGyroscope.
- [x] Ensure the Bluetooth connection state is clearly visible - Added connection icon to RemoteScreen top bar.
- [x] Add haptic feedback toggle to DataStore and propagate to all buttons.
