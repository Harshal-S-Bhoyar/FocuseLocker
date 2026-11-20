# Design

## Context

See proposal.md Why. Ranges are end-exclusive over minute indexes. Validation in `ScheduleEngine.kt:20` caps both bounds at `1439`. The Full-day preset in `ScheduleEditorScreen.kt:243` writes `0..1439`. Window bounds persist as plain integers, so no migration is available or needed.

## Goals / Non-Goals

**Goals:**
- Make `23:59` coverable and the full-day preset truly full-day.
- Keep every existing stored window meaning exactly what it means today.

**Non-Goals:**
- No change to overnight ownership, dedup keys, firing cadence, or navigation.
- No picker support for typing `24:00` by hand. The sentinel is preset-only input.

## Decisions

- End-of-day sentinel `1440` over redefining `start == end` as full-day.
- Rationale: `start == end` currently means zero-length and is rejected with a clear message plus unit coverage. Reusing it would erase that guard and force every call site to disambiguate. The sentinel only widens the end bound by one value, and existing math (`..<` ranges, `end - now`, `end - start`) already handles it without branches.
- Preset writes `0..1440`. Display formats `1440` as `24:00` through the existing `%02d` path.
- Validation becomes start in `0..1439`, end in `0..1440`, still rejecting `start == end` (unreachable at `1440` since start caps at `1439`).

## Risks / Trade-offs

- [Risk] Hand-typed `23:59` end still leaves the last minute free by design → Mitigation: preset is the blessed full-day path, documented in the confirmation summary.
- [Risk] Overnight check `start > end` never misfires on the sentinel since `1439 < 1440` always → No mitigation needed, noted for reviewers.
