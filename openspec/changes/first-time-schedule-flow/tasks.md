# Tasks

## 1. Category cleanup

- [x] 1.1 Remove the manage-schedule entry button plus its route and dead schedule callbacks from CategorySelection, keeping the armed banner, verified by `testDebugUnitTest` green plus lint showing no unreferenced navigation warnings
- [x] 1.2 Delete strings and resources orphaned by 1.1 only where compiler plus tests prove them unreferenced, verified by `assembleDebug` clean plus `lintDebug` with no new warnings

## 2. First-time reorder

- [x] 2.1 Route Keywords to trusted contact plus OTP before the session-type chooser for unverified first-time users, skipping directly to the chooser when verified, verified by navigation tests for both contact states
- [x] 2.2 Keep the schedule editor Done-time contact guard routing unverified users to contact setup then confirmation, verified by navigation test asserting guard fires and confirm follows verification

## 3. Single confirm terminal

- [x] 3.1 Route both chooser branches (weekly editor Done, one-shot set-time) to the existing confirm screen with unchanged args, verified by navigation tests for both paths reaching confirm
- [x] 3.2 Confirm lock-in arms via the existing arm path with immediate activation when the window covers now, verified by existing activation tests plus one new test for schedule-confirm immediate start

## 4. Regression and land

- [ ] 4.1 Run the one-shot timer setup end to end unchanged (contact check, set-time, confirm, activation), verified by existing one-shot suites green plus one manual device walk
- [x] 4.2 Run full `testDebugUnitTest` plus `lintDebug` plus `assembleDebug` after the last edit, verified by pasted BUILD SUCCESSFUL output with zero failures
