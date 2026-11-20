# Proposal

## Why

A review pass over the startup, schedule-arming, and URL-blocking paths produced 12 findings: a stuck Done button, swallowed arming failures, overlapping URL verdicts that can act on stale pages, unbounded mutex waits during seeding, and concurrent init sequences racing on UI state. Each is small; together they erode trust in exactly the flows users touch first.

## What Changes

- **Startup (batch 1):** seed timeout covers the mutex wait, not just the seed call; `OnboardingViewModel` keeps a single init Job and cancels it before relaunch; dead `as? Set` cast removed so domain normalization always runs.
- **Schedule arming (batch 2):** `armScheduleAndCheckActivation` stops before `setOnboardingComplete()` when enabling fails; Done button stays disabled until verified-contact state is known; confirm screen resets `isArming` in `finally`; due-window tests use an injectable clock with fixed time; `ScheduleRepository` injected non-null.
- **URL matching (batch 3):** `BrowserEventHandler` cancels the previous check before launching a new one, discards verdicts for superseded URLs, and exposes `dispose()` called from service `onDestroy`; Task 2.2 checkbox reconciled with the implemented behavior.
- **Gates/docs (batch 4):** G1 check pattern updated to the audit file's finding format; Reticle verdict guidance includes `reticle_act` with steps.

## Capabilities

### New Capabilities

None. All changes tighten existing specified behaviors.

### Modified Capabilities

- `app-startup`: seed timeout SHALL bound mutex wait in addition to seeding; init SHALL run at most one sequence at a time (retry cancels the prior run).
- `scheduled-sessions`: arming SHALL NOT mark onboarding complete when enabling the schedule fails; Done SHALL stay disabled until verified-contact state is loaded.
- `app-blocking`: only the most recent URL's verdict SHALL be applied; superseded in-flight checks SHALL be discarded.

## Impact

- Files: `InitializeAppUseCase`, `OnboardingViewModel`, `CheckUrlForBlockedContentUseCase`, `ScheduleEditorViewModel`, `ScheduleConfirmScreen`, `ScheduleEditorScreen`, `CategorySelectionViewModel`, `BrowserEventHandler`, `FocusAccessibilityService`, `ScheduleFlowAdversarialTest`, `ScheduleEditorViewModelTest`, `GATES.md`, `.claude/commands/reticle.md`, `fix-startup-perf-lag/tasks.md`.
- No public API changes. Two intentional guarded behavior changes: malformed states now surface instead of being silently absorbed (arming failure stops onboarding completion; stale URL verdicts are dropped rather than applied).
- Verification: full `testDebugUnitTest` green after the change; targeted adversarial tests updated for deterministic time.
