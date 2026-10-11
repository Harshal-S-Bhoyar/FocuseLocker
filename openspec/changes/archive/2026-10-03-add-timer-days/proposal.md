# Proposal

## Why

The Set Timer screen offers only hours (0–168) and minutes, so a 1-day session must be expressed as "24 hours" and multi-day durations as unwieldy hour counts (e.g., 72 hours). Adding a days unit with hours capped at 23 makes durations say what they mean, matching how users think about multi-day focus commitments.

## What Changes

- Add a Days picker (0–7) to the Set Timer screen, reusing the existing `NumberPicker` component.
- Cap the hours picker at 23; incrementing past 23 rolls over into days (23h +1 → 0h, days +1), and decrementing days rolls back symmetrically.
- Keep the 7-day maximum and 1-minute minimum; reaching exactly 7 days requires hours and minutes at 0.
- Extend the session confirmation summary to render days ("2 days, 3 hours") instead of "51 hours".
- No changes to duration storage (millis), session activation bounds, workers, or countdown: the total is still `days*1440 + hours*60 + minutes`, bounded to 1..10080 minutes.

## Capabilities

### New Capabilities

- `session-timer`: focus session duration selection (days/hours/minutes units, rollover semantics, range bounds) and duration summary display.

### Modified Capabilities

(none — no existing spec covers timer behavior)

## Impact

- Affected code: `TimerSetupScreen` (days picker, rollover wiring), `TimerSetupViewModel` (days flow, rollover + 7-day edge logic, millis math), `SessionConfirmationScreen` (days-aware summary + `session_duration_days` plurals).
- Affected tests: `TimerSetupViewModelTest` (rollover, edge, math), confirmation display coverage.
- No API, database, navigation, permission, worker, or countdown impact. Existing hour/minute behavior for sub-24h durations is unchanged.
