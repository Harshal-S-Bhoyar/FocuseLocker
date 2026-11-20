# Gates: first-time-schedule-flow

Scope: First-time setup reorder (contact before chooser), Category minus manage entry, single confirm terminal. One-shot internals untouched.

- [x] G1: Category cleanup (tasks 1.1+1.2)
  CHECK: grep for manage entry plus dead routes in CategorySelection path
  EXPECT: no manage-schedule button, route, or callback; banner intact
  EVIDENCE: button+param+lambda+import removed; schedule_manage_entry kept (shared with SessionCompleteScreen); ScheduleManage route kept for SessionComplete; banner code untouched; full suite green

- [x] G2: First-time reorder (tasks 2.1+2.2)
  CHECK: navigation tests for both contact states
  EXPECT: unverified Keywords leads to contact then chooser; verified skips; editor guard intact
  EVIDENCE: FirstTimeFlowTest 8/8 green (chooser/contact branches, -1/0/positive returns, VM flags both states); existing KeywordSelectionViewModelTest updated mechanically, green; editor guard untouched, A1/A2 adversarial green

- [x] G3: Single confirm terminal (tasks 3.1+3.2)
  CHECK: navigation plus activation tests
  EXPECT: both branches reach confirm; lock-in arms with immediate start when live
  EVIDENCE: pure verifyNav/chooserOrContactRoute tested; one-shot branch byte-identical; existing A3 immediate-activation green; ScheduleNavTest green

- [ ] G4: Regression plus land (tasks 4.1+4.2)
  CHECK: .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  EXPECT: BUILD SUCCESSFUL, 0 failures, no new warnings
  EVIDENCE: BUILD SUCCESSFUL in 6m 57s; 542 tests 0 fail 0 error 0 skip; lintDebug clean; device walk half of 4.1 pending (device offline)
