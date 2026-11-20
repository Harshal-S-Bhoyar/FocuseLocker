# Design: Fix Schedule Confirmation Loop

## Context

In `AppNavigation.kt:190-205`, `ScheduleConfirmScreen` calls `viewModel.armScheduleAndCheckActivation()`. When the active windows do not cover the current time, `activated == false`. In this case, line 200 currently navigates to `Screen.CategorySelection.route` with `popUpTo(Screen.CategorySelection.route) { inclusive = true }`.

`CategorySelectionScreen` was designed as step 1 of the onboarding/setup wizard. When a user lands on `CategorySelectionScreen`, the bottom button is "Next", which takes the user through `AppBlockSelection` -> `KeywordSelection` -> `ScheduleMode` -> `ScheduleEditor` -> `ScheduleConfirm` -> `CategorySelection`. This causes an infinite loop where the user can never reach a concluded, confirmed state for their weekly schedule.

## Goals / Non-Goals

- **Goals:**
  - `ScheduleConfirmScreen` acts as the terminal screen for future weekly schedules.
  - Tapping Done/Confirm transitions the UI into a confirmed armed state (e.g., "Schedule Armed & Saved — Your schedule is active").
  - Break the circular navigation to `CategorySelectionScreen`.
  - Ensure `CategorySelectionScreen` does not show a "Next" button that restarts the setup wizard when a schedule is already armed.
- **Non-Goals:**
  - No changes to underlying schedule evaluation (`ScheduleEngine`), Room database schema, or background WorkManager execution (`scheduleScheduleChecks`).
  - No change to immediate session activation when a window covers the current time.

## Decisions

- **Decision 1: Terminal state on ScheduleConfirmScreen.**
  When `armScheduleAndCheckActivation()` returns `false`, `ScheduleConfirmScreen` does not trigger navigation away to the wizard. Instead, it exposes an armed state in the UI (e.g. "Schedule Active", button switches to "Done" / finish, or remains as the confirmed overview).
- **Decision 2: Remove line 200 loop in AppNavigation.kt.**
  In `AppNavigation.kt`, `onDone` callback for `ScheduleConfirm` only navigates to `Screen.ActiveSession.route` when `activated == true`. When `activated == false`, it does not re-enter `CategorySelection.route`.
- **Decision 3: Guard CategorySelectionScreen when scheduleEnabled.**
  When `scheduleEnabled == true`, the primary bottom action on `CategorySelectionScreen` changes from "Next" (starting the wizard) to "Manage Schedule" (routing to `Screen.ScheduleManage.route` / view schedule).

## Risks / Trade-offs

- [Risk] User wants to close or navigate away from the confirmation screen.
  - Mitigation: Provide a clear exit/home action or back navigation that does not force them back into step 1 of the setup wizard.
- [Risk] Immediate session activation breaks.
  - Mitigation: Keep the `activated == true` path routing directly to `ActiveSessionScreen` with popUpTo cleanup intact.

## Migration Plan

Single atomic code change. No database migration needed. Reversible via simple git revert.
