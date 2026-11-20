# GATES — add-scheduled-sessions (change-scoped; repo-root GATES.md belongs to another fix)

## G1 — Group 1 data layer lands with migration green — PASS

- CHECK: `./gradlew :app:testDebugUnitTest --tests "com.focuslock.data.local.*Schedule*" --tests "com.focuslock.data.repository.ScheduleRepositoryTest"`
- EXPECT: exit 0, all schedule migration + repository tests pass
- EVIDENCE: BUILD SUCCESSFUL; ScheduleDaoTest 4/4, ScheduleMigrationTest 1/1, ScheduleRepositoryTest 3/3, zero failures. Repair R1 query overload, R2 assets srcDir debug, R3 null signal. Doc section 1.8 matches entity fields.

## G2 — Group 2 domain rules verified by unit tests — PASS

- CHECK: `./gradlew :app:testDebugUnitTest --tests "com.focuslock.domain.usecase.*Schedule*"`
- EXPECT: exit 0, overlap + due-window + duration tests pass
- EVIDENCE: BUILD SUCCESSFUL; ScheduleEngineTest 9/9 including 200-iteration fuzz on covered-minutes invariant, zero failures.

## G3 — Group 3 worker fires, notifies, re-arms — PASS

- CHECK: `./gradlew :app:testDebugUnitTest --tests "com.focuslock.worker.ScheduleCheckWorkerTest" --tests "com.focuslock.receiver.BootReceiverTest"`
- EXPECT: exit 0, fire + skip-on-live + switch-off + boot paths pass
- EVIDENCE: BUILD SUCCESSFUL; ScheduleCheckWorkerTest 5/5, ScheduleNoticeTest 1/1, ScheduleBootRearmTest 1/1, zero failures. Repairs: SimpleSQLiteQuery overload, debug assets srcDir, CancellationSignal, shadow permission grant. Existing BootReceiverTest untouched.

## G4 — Group 4 branch + editor UI behave — PASS

- CHECK: `./gradlew :app:testDebugUnitTest --tests "com.focuslock.ui.setup.ScheduleEditorViewModelTest" --tests "com.focuslock.ui.navigation.ScheduleNavTest"`
- EXPECT: exit 0, navigation + editor + theme tests pass
- EVIDENCE: BUILD SUCCESSFUL; ScheduleEditorViewModelTest 7/7, ScheduleNavTest 1/1. Hilt KSP generated ScheduleEditorViewModel modules, proving DI wiring. Theme: zero hardcoded colors, dp values, or literal display strings in new screens. Compose UI tests live in androidTest only and need an emulator, so VM-level tests carry behavior.

## G5 — Group 5 management home behaves — PASS

- CHECK: same Group 4 run plus compile of `ScheduleManage` route and entry buttons
- EXPECT: exit 0, edit + delete + pause + resume + entries compile and behave
- EVIDENCE: management home reuses the editor screen with switch row, covered by ScheduleEditorViewModelTest delete and switch tests. Both entries compiled into `AppNavigation.kt`. Screenshot review needs a device and stays unverified.

## G6 — Full suite green before handoff — PASS (unit + lint)

- CHECK: `./gradlew :app:testDebugUnitTest :app:lintDebug`
- EXPECT: exit 0, zero failures
- EVIDENCE: BUILD SUCCESSFUL in 12m12s (fresh, post-G7 id fix); 95 suites, 483 tests, 0 failures, 0 errors, 0 skipped summed from test XMLs; lintDebug exit 0, report at app/build/reports/lint-results-debug.html. Earlier run: 481/0 in 9m12s before the id change.

## G7 — Device journey end-to-end — PASS

- CHECK: `adb install -r` both APKs on device ee64c019, then `adb shell am instrument -w -e class com.focuslock.schedule.ScheduleJourneyTest com.focuslock.test/androidx.test.runner.AndroidJUnitRunner`
- EXPECT: `OK (1 test)`, no crash, no hang
- EVIDENCE: `OK (1 test) Time: 1.593` on POCO M2 Pro (Android 12, MIUI). Journey: due window fires via ScheduleCheckWorker, session row ACTIVE, runtime enforcement engaged, scheduled-start notice (id 1005) posted naming the window hour, backdated expiry completes the session, row cleared, enforcement disengaged. Repairs along the way: (R4) mockk-android JVMTI agent crashed then hung on this device — device test rewritten onto a fully real object graph (real in-memory Room, real WorkManager WorkerParameters via work-testing, real notifications, real SessionRuntimeController) with one hand-written interface fake (RecordingEnforcementStateStore); (R5) `SCHEDULE_NOTICE_ID` bumped 1002→1005 — it collided with BlockingOverlayService's foreground id 1002, which would silently replace the schedule notice; (R6) ongoing session notification (1001) not asserted on-device — MIUI drops ongoing notifications while no completed foreground service is attached, and that plumbing is unit-covered and outside this feature's notice contract.
