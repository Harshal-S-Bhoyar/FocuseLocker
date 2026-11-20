# Design

## Context

See proposal.md Why. `SessionWorkerScheduler.scheduleScheduleChecks` enqueues unique periodic work with `UPDATE` policy, so calling it from both application start and boot is idempotent. `ScheduleCheckWorker.runCheck` currently guards only the master switch, due math, and dedup. `AccessibilityHealthWorker` already watches permissions reactively but never gates firing.

## Goals / Non-Goals

**Goals:**
- Every app start ends with the periodic check enqueued.
- No session starts that the device cannot enforce.
- Reboots, updates, and direct-boot mode all rearm the check.

**Non-Goals:**
- No change to enforcement or repair flows themselves.
- No new permissions requested. Battery plus notification stay out of the gate.

## Decisions

- Gate inside `ScheduleCheckWorker.runCheck` rather than `ActivateSessionUseCase`, so manual starts keep their current behavior and the worker owns the skip plus retry plus notice path.
- Check accessibility plus overlay only, matching the subset `BootReceiver` and `AccessibilityHealthWorker` already use. Device admin plus VPN stay out to avoid over-blocking fires.
- Skip without recording the occurrence, so the next 15-minute tick retries after the user repairs permissions.
- Cold-start call placed in `FocusLockApplication.onCreate` next to channel creation, same idempotent enqueue the boot path uses.

## Risks / Trade-offs

- [Risk] Fires during a revoked-permission stretch never activate until repair → Mitigation: skip is logged plus repair notice routes, same as today.
- [Risk] Extra manifest intents wake the receiver more often → Mitigation: rearm is enqueue-only `UPDATE`, no activation work.
