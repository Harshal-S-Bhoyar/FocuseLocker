# Tasks: Fix Schedule Confirmation Loop

## 1. Navigation Graph Fix

- [x] 1.1 Remove line 200 loop in `AppNavigation.kt`: when `activated == false`, do not re-navigate to `Screen.CategorySelection.route`
- [x] 1.2 Update `ScheduleConfirmScreen` to present a confirmed/armed terminal state once `armScheduleAndCheckActivation()` completes with `activated == false`

## 2. Category Screen Guard

- [x] 2.1 Update `CategorySelectionScreen.kt` bottom action: when `scheduleEnabled == true`, button navigates to `ScheduleManage` instead of starting `AppBlockSelection`

## 3. Test Suite & Verification

- [x] 3.1 Add unit tests in `ScheduleNavTest.kt` and `ScheduleFlowAdversarialTest.kt` verifying that confirming a future schedule does not loop to `CategorySelection`
- [x] 3.2 Run `testDebugUnitTest` and verify all tests pass green
- [x] 3.3 Run `lintDebug` and `assembleDebug` clean
