# Proposal

## Why

Launch init is down to 1.9s from 15s, yet cold start still brushes 7s on slow runs and in-app UI stutters: the countdown recomposes whole screens every second, first-frame GPU work is heavy, hot DAO flows recompose full lists, and URL matching runs on Main. Users feel lag on the two most-watched surfaces: launch and the active session.

## What Changes

- Cap launch-to-content under 4s worst case on the reference device, measured per change on hardware.
- Scope countdown recomposition to the timer text only; freeze the rest of the active session tree.
- Defer first-frame GPU work (gradient blend, offscreen pass, Lottie) until after first paint or gate behind a frame budget.
- Move URL matching off Main with a bounded worker and cache the normalized domain set instead of rebuilding per event.
- Stabilize list recomposition with content types plus keys so DB writes do not rebind whole lists.
- Judge smoothness on release builds with baseline profiles; keep debug builds for correctness only.

## Capabilities

### New Capabilities

- `ui-smoothness`: in-app surfaces hold frame deadlines with scoped recomposition and off-Main matching.

### Modified Capabilities

- `app-startup`: add a launch-to-content budget requirement covering window draw plus init plus first paint.

## Impact

- Affected code: `ActiveSessionScreen` plus countdown flow, onboarding first-frame composition, schedule plus category lists, `CheckUrlForBlockedContentUseCase` threading plus cache, release profile configuration.
- No behavior, API, or data model changes. All verdicts identical, only faster.
- Tests: frame-deadline assertions via `dumpsys gfxinfo`, unit tests for cache reuse, device timing loop per change.
