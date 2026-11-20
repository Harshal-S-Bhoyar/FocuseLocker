# Proposal

## Why

The app starts fast now but in-app UI still stutters and background work drains battery: Lottie animations compose off-screen, countdown-adjacent scopes still re-collect, the accessibility filter stays widened, and retry storms (live-wins every tick, repair starts every tick) burn CPU for no-ops.

## What Changes

- Dispose or pause infinite animations when their branch unmounts; keep visuals identical while visible.
- Narrow accessibility package filter dynamically to watched packages; widen only while a block is actively enforced.
- Back off live-wins retries and per-occurrence repair starts so dead ticks stay cheap.
- Align periodic workers; drop ticks that can prove no-ops cheaply before heavy work.
- No verdict, schedule, unlock, or enforcement behavior changes. All block and fire semantics identical.

## Capabilities

### New Capabilities

- None. Internal timing and scoping only; all observable verdicts unchanged, so this change opts out of specs via `skip_specs: true`.

### Modified Capabilities

- None for the same reason.

## Impact

- Affected code: loaders, session screens, `FocusAccessibilityService` filter, `ScheduleCheckWorker` retry paths, worker scheduling.
- No API, data model, or dependency changes.
- Tests: existing unit suites plus targeted regression tests for backoff and filter behavior.
