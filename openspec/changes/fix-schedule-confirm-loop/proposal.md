# Proposal: Fix Schedule Confirmation Loop

## Why

When a user finishes setting up a weekly schedule that starts in the future (`activated == false`), line 200 in `AppNavigation.kt` redirects `ScheduleConfirmScreen` back to `Screen.CategorySelection.route`. Because `CategorySelectionScreen` displays a primary "Next" button that routes forward to `AppBlockSelection` -> `KeywordSelection` -> `ScheduleMode` -> `ScheduleEditor` -> `ScheduleConfirm`, the user is trapped in an endless loop and can never reach a clear confirmed state.

## What Changes

- **Schedule Confirmation Terminal State:** `ScheduleConfirmScreen` transitions into a confirmed/armed terminal state upon arming (`activated == false`). It displays confirmation that the weekly schedule is armed and active without redirecting back to the setup wizard.
- **Break Wizard Redirection:** Remove the circular navigation to `Screen.CategorySelection.route` from `ScheduleConfirmScreen.onDone` in `AppNavigation.kt`.
- **Category Screen Guard:** When `scheduleEnabled == true`, `CategorySelectionScreen` must not prompt the user with "Next" to restart the setup wizard. Its action button routes to manage/view schedule or reflects the active armed state.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `scheduled-sessions`: Confirming a weekly schedule with future windows SHALL transition to a terminal armed confirmation state and SHALL NOT redirect to the category selection wizard.

## Impact

- Files: `app/src/main/java/com/focuslock/ui/navigation/AppNavigation.kt`, `app/src/main/java/com/focuslock/ui/setup/ScheduleConfirmScreen.kt`, `app/src/main/java/com/focuslock/ui/setup/CategorySelectionScreen.kt`.
- Tests: `ScheduleNavTest.kt`, `ScheduleFlowAdversarialTest.kt`, `FirstTimeFlowTest.kt`.
- No database migrations, background service, or battery impact.
