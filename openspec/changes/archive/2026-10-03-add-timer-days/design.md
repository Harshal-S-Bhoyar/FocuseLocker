# Design

## Context

See proposal.md (Why) for motivation. Current state, verified in code:
- `TimerSetupScreen` hosts two `NumberPicker`s (hours 0-168, minutes 0-59) with clamp-style enable flags; the component is unit-agnostic and reusable for days.
- `TimerSetupViewModel` holds `hours`/`minutes` flows, clamps in `updateHours`/`updateMinutes`, and derives millis as `(h*60+m)` bounded to 1..10080 minutes.
- `SessionConfirmationScreen` derives hours/minutes from `durationMillis` and renders "H hours, M minutes" via existing plurals; a 2-day session would read "48 hours".
- Downstream (`ActivateSessionUseCase` millis bounds, workers, countdown) is unit-agnostic: only millis cross the boundary.

## Goals / Non-Goals

**Goals:**
- Days picker with rollover/borrow hour semantics; hours capped at 23 by construction.
- 7-day max and 1-minute min preserved exactly; confirmation renders days.
- Existing sub-24h behavior and all downstream millis consumers untouched.

**Non-Goals:**
- No rollover for minutes (0-59 clamp stays), no weeks unit, no custom durations.
- No changes to activation bounds, workers, countdown, notifications, or navigation.
- No change to blank/duplicate keyword behavior or any other setup screen.

## Decisions

- **Rollover + symmetric borrow in the ViewModel (user chose over clamp).** Rationale: 23h +1 must go somewhere since 24h is unrepresentable; rolling into days keeps every tap productive and matches clock semantics users already know. Alternative (clamp at 23, days raised separately) rejected by the user for requiring dead taps.
- **Clamp the 7-day edge by gating, not by auto-zeroing.** Rationale: days may reach 7 only with hours/minutes at 0 (mirrors the existing 168h rule); auto-zeroing user input would silently destroy their selection. Rollover attempts past the max are refused with values unchanged.
- **Reuse `NumberPicker`, add `session_duration_days` plurals.** Rationale: zero new UI primitives; confirmation follows the file's own H/M pattern extended to D/H/M, omitting zero units.
- **Keep millis as the inter-screen contract.** Rationale: navigation args, activation bounds, and countdown all speak millis; changing the contract would ripple through workers for no behavioral gain.

## Risks / Trade-offs

- [Risk] Rollover surprises (user taps + on 23h, sees 0h + days+1) → Mitigation: the days picker visibly increments in the same frame; standard clock behavior, covered by explicit scenarios.
- [Risk] Three-picker row crowds small screens → Mitigation: reuse existing spacing scale; verify on a 360dp-wide device in UAT.
- [Risk] `TimerSetupViewModelTest` asserts hour/minute APIs that gain a days sibling → Mitigation: tasks update the tests first-class, not as an afterthought.

## Migration Plan

No migration: no stored state changes. Rollback is a code revert; in-flight setups simply show the old picker.

## Open Questions

None — rollover semantics, edge gating, and confirmation scope were confirmed before planning.
