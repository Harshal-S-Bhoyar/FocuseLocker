# Tasks

## 1. Startup concurrency (spec: app-startup)

- [x] 1.1 Move `SEED_TIMEOUT_MS` to wrap the `SEED_MUTEX.withLock` block in `seedOnceWithinBudget`, keep the fast-path early return outside, and verify the seed-timeout unit test passes
- [x] 1.2 Retain the init `Job` in `OnboardingViewModel.initialize`, cancel before each relaunch from `retry()`, and verify `OnboardingViewModelTest` passes including a double-retry case with no concurrent sequences
- [x] 1.3 Remove the dead `(domains as? Set<String>)` cast so normalization always runs, and verify `CheckUrlForBlockedContentUseCaseTest` passes unchanged

## 2. Schedule arming (spec: scheduled-sessions)

- [x] 2.1 Propagate `setScheduleEnabled(true)` failure out of `armScheduleAndCheckActivation` before `setOnboardingComplete()` runs, and verify a new test asserting onboarding stays incomplete on enable failure passes
- [x] 2.2 Add contact-loaded gating so Done stays disabled until verified-contact state loads, and verify the editor screen test taps Done only after load with the loaded result
- [x] 2.3 Reset `isArming` in a `finally` block in `ScheduleConfirmScreen`, and verify Done re-enables after a throwing arm call in a compose/unit test
- [x] 2.4 Make `ScheduleRepository` non-null in `CategorySelectionViewModel` (drop fallback), and verify the category selection tests pass
- [x] 2.5 Thread an injectable `now` (default `LocalDateTime.now()`) through `armScheduleAndCheckActivation`, fix `advA3`/`ScheduleEditorViewModelTest` to a fixed time, and verify both suites pass deterministically

## 3. URL matching single-flight (spec: app-blocking)

- [x] 3.1 Cancel the previous check job before launching a new one in `BrowserEventHandler`, discard verdicts for superseded URLs, and verify a burst-event test applies only the latest verdict
- [x] 3.2 Add `dispose()` cancelling `handlerScope` and call it from `FocusAccessibilityService.onDestroy`, and verify no pending check outlives the service in a lifecycle test
- [x] 3.3 Reconcile Task 2.2 in `fix-startup-perf-lag/tasks.md` with the implemented behavior, and verify the checkbox state matches the code

## 4. Gates and docs (no spec change)

- [x] 4.1 Update the G1 CHECK pattern in `GATES.md` to the audit file's finding format, and verify the check command returns the finding count
- [x] 4.2 Include `reticle_act` with steps in the verdict guidance of `.claude/commands/reticle.md`, and verify the doc agrees with `AGENTS.md`

## 5. Integration verification

- [x] 5.1 Run the full `testDebugUnitTest` suite and verify BUILD SUCCESSFUL with zero failures, then run `assembleDebug` and verify BUILD SUCCESSFUL
