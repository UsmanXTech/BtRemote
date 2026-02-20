# Workflow - BtRemote

## Track Lifecycle
1. **Identify Requirement**: New feature, bug fix, or optimization.
2. **Create Track**: Create a folder in `conductor/tracks/` with `index.md` and `plan.md`.
3. **Register Track**: Add the new track to `conductor/tracks.md`.
4. **Implement**: Execute the plan, making code changes and updating documentation.
5. **Verify**: Test and lint (if tools are available).
6. **Finalize**: Mark as completed in registry and update documentation.

## Guidelines
- Follow Clean Architecture.
- Use StateFlow for reactive state.
- Ensure proper lifecycle management of sensors and Bluetooth.
- Adhere to Material 3 principles.
