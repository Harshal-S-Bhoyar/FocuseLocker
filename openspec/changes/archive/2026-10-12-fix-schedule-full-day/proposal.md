# Proposal

## Why

The weekly template claims a full-day preset covering the whole day, but the engine treats ranges as end-exclusive over 0..1439. The preset `0..1439` therefore covers `00:00` to `23:58` and leaves minute `23:59` permanently free, so a true full-day lock is inexpressible. Any window touching `23:59` collides with nothing and fires short.

## What Changes

- Accept `endMinutes` of `1440` as the end-of-day sentinel meaning midnight at the close of the window's day.
- Keep `startMinutes` within `0..1439` and keep rejecting `start == end`.
- Rewire the Full-day preset to save `0..1440` instead of `0..1439`.
- Keep overnight detection (`start > end`), overlap expansion, due and remaining math unchanged in shape, extended to the sentinel.
- **BREAKING**: none. Existing stored windows keep their meaning. `23:59` becomes coverable.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `scheduled-sessions`: full-day and late-evening coverage now reaches the final minute of the day through the `1440` end sentinel.

## Impact

- Affected code: `ScheduleEngine.kt` (validation, absolute minutes, duration), `ScheduleEditorScreen.kt` (Full-day preset, display of `24:00`), engine plus editor tests.
- No database migration. Window bounds stay plain integers.
- Depends on archived `2026-10-12-add-scheduled-sessions` for the base `scheduled-sessions` main spec.
