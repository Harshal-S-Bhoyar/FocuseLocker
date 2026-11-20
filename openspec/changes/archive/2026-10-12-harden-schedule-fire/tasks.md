# Tasks

## 1. Cold-start arm

- [x] 1.1 Call `scheduleScheduleChecks` from `FocusLockApplication.onCreate` and verify a fresh start enqueues the unique periodic check without duplicating the boot path

## 2. Pre-activation guard

- [x] 2.1 Gate `runCheck` on accessibility plus overlay before activation, skipping without recording the occurrence, and verify a dead-permission tick never calls `ActivateSessionUseCase` and retries on the next tick
- [x] 2.2 Route the skip through the existing repair notice path and verify the user gets directed to repair instead of silence

## 3. Boot intents

- [x] 3.1 Widen the manifest plus `BootReceiver` to direct-boot plus quickboot plus package-replaced intents and verify rearm fires on each intent in receiver tests
