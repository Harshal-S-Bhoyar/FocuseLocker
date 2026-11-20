# Proposal

## Why

Weekly fires currently assume a healthy device. Three gaps break that promise: a fresh install or force-stop never enqueues the periodic check until the next reboot, a fire with dead accessibility or overlay still creates a live session the device cannot enforce, and boot rearm listens only to `BOOT_COMPLETED`, missing direct-boot plus quickboot plus update-replace restarts.

## What Changes

- Enqueue the periodic schedule check from `FocusLockApplication.onCreate` in addition to the boot path, so cold starts arm without a reboot.
- Gate `ScheduleCheckWorker` on accessibility plus overlay before activation. A fire with dead permissions skips activation, keeps its occurrence unrecorded for retry, and routes through the existing repair notice.
- Widen boot rearm to direct-boot plus quickboot plus package-replaced intents alongside `BOOT_COMPLETED`.
- No engine, template, dedup, or navigation changes.

## Capabilities

### New Capabilities

- None. New behavior lands as ADDED requirements on the existing `scheduled-sessions` capability below.

### Modified Capabilities

- None. Behavior lands as ADDED requirements, see `specs/scheduled-sessions/spec.md`.

## Impact

- Affected code: `FocusLockApplication.kt`, `ScheduleCheckWorker.kt`, `BootReceiver.kt`, `AndroidManifest.xml`, plus worker plus receiver tests.
- No data model or migration impact.
- Tests: worker guard tests, boot intent tests, cold-start arm test.
