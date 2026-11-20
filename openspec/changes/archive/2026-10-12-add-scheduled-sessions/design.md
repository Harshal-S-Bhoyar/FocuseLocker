# Design

## Context

Sessions start only through `ActivateSessionUseCase`, which enforces the single-active-session rule, writes via `SessionRepository.startSession`, schedules timers through `SessionWorkerScheduler`, and starts enforcement through `SessionRuntimeController`. Expiry flows through `SessionExpiryWorker` to the shared completion state. Setup navigation runs Category, AppBlock, Keyword, Timer, Confirm in `AppNavigation.kt` with one ViewModel per screen. Persistence uses Room (`FocusDatabase` with per-domain DAOs) and Hilt modules per layer. `BootReceiver` re-arms background work after reboot. See proposal.md for motivation and specs for requirements.

## Goals / Non-Goals

**Goals:**

- Add automatic firing without touching the manual activation, expiry, or unlock paths.
- Keep the schedule store separate from session and block-list tables.
- Reuse the existing worker, notification, and completion machinery for every scheduled run.

**Non-Goals:**

- Exact-minute start precision; minute-level drift is accepted per the spec.
- Per-day block sets; the live shared stores stay the single source of truth.
- Backend or API changes.

## Decisions

### New Room table for the weekly template

Store one row per window with weekday, start minutes, and end minutes plus one master-switch row. A DAO exposes the full template and the switch in one read. Rationale: the schedule is small, relational, and benefits from the existing database and migration tooling. Alternative was DataStore preferences; rejected because per-window queries and future per-day extensions fit tables better.

### Overlap validation in the domain layer

A use case expands all windows into absolute weekly minutes (0 to 10079) and rejects intersecting saves with the colliding pair. Rationale: validation lives with the other schedule rules and stays testable without Android. The editor calls it before every save, so invalid templates never persist.

### Periodic schedule-check worker

A new WorkManager periodic worker runs every 15 minutes, reads the master switch and template, and invokes the existing activation use case for any due window with the window length as duration. Rationale: reuses proven WorkManager and battery-exemption flows and needs no new permission. Alternative was exact alarms; rejected over the extra permission and OEM delay behavior. `BootReceiver` re-arms this worker alongside existing timers.

### Fire path reuses activation unchanged

The worker calls `ActivateSessionUseCase` with the computed window duration. Rationale: the single-active-session rule then yields the live-session-wins behavior for free, and expiry, completion, and OTP unlock follow the manual path with zero new code. No changes to the use case itself.

### Start notice via existing channel

The worker posts the scheduled-start notice through `SessionNotificationManager` before invoking activation. Rationale: one channel, no new screens, cause and effect stay visible.

### Navigation branch with shared confirmation

`AppNavigation.kt` gains Schedule routes after Keyword selection: a mode-choice step, the template editor, and the management home. Both paths converge on the existing confirmation route. Rationale: smallest navigation delta that keeps both modes.

## Risks / Trade-offs

- [Risk] Doze or OEM skins delay the 15-minute worker past the window start → Mitigation: window fires late but still fires; battery-exemption guidance already exists in onboarding.
- [Risk] Reboot inside a due window loses the fire until the next check → Mitigation: `BootReceiver` re-arms the worker, and the check fires any window whose span covers now.
- [Risk] Overnight ownership confuses Tuesday rows showing Monday-owned locks → Mitigation: editor labels overnight windows with their covered span, e.g. Monday 20:00 to Tuesday 14:00.
- [Risk] Skipped fires feel silent despite the notice → Mitigation: the notice names the skipped rule and the blocking live session.

## Migration Plan

Room auto-migration adds the schedule tables; no data backfill needed since the template starts empty and the master switch defaults off. Rollback removes the new routes, worker, and tables with no effect on existing sessions. No deployment steps beyond the normal app release.

## Open Questions

- Exact periodic interval within 10 to 15 minutes can be tuned during implementation without changing specs.
- Notice copy wording is UI polish and stays out of the spec.
