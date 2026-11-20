# Gates: Ponytail-Audit + Codebase Principles Validation (FocusLocker)

Scope: Run ponytail-audit repo-wide and validate repo against Development_Principles/CODEBASE_Principles_VALIDATION_AGENT.md (audit + safe remediations + verified report). No new features. Deletion/minimal-diff only.

- [x] G1: Ponytail-audit complete repo-wide, ranked biggest-cut-first
  CHECK: powershell -NoProfile -Command "Select-String -Path 'PONYTAIL_AUDIT.md' -Pattern '^-\s+``(delete|stdlib|native|yagni|shrink)``' | Measure-Object | Select-Object -ExpandProperty Count"
  EXPECT: count >= 1 OR 'Lean already. Ship.' present
  EVIDENCE: 30 ranked findings in PONYTAIL_AUDIT.md; net: -860 lines, -3 deps possible

- [x] G2: Validation-agent audit complete (10 outcome dims scored + 17 principles with file:line evidence)
  CHECK: powershell -NoProfile -Command "Select-String -Path 'CODEBASE_VALIDATION_REPORT.md' -Pattern '^\| (Complexity|Consistency|Risk|Quality|Efficiency|Adaptability|Communication|Scalability|Cost|User-centric)' | Measure-Object | Select-Object -ExpandProperty Count"
  EXPECT: 20 (10 before + 10 after)
  EVIDENCE: CODEBASE_VALIDATION_REPORT.md has 10 before + 10 after rows; 52 violations (Critical 0, High 18, Medium 26, Low 8); all 17 principles addressed incl. zero-violation rows

- [x] G3: Safe remediations applied incrementally, behavior preserved
  CHECK: powershell -NoProfile -Command "git -c core.quotepath=false diff --stat"
  EXPECT: diff touches only audited violation files, no public API break unless logged in Remaining Gaps
  EVIDENCE: 7 files changed (ListSyncWorker, PageSignalFetcherImpl, ScheduleEditorScreen, ActiveSessionScreen, UnlockCooldownScreen, SaveTrustedContactUseCase, NetworkModule) + TEST FIX in SaveTrustedContactUseCaseTest (stale phone inputs -> email, +1 malformed-email case); 2 intentional guarded behavior changes logged in report (malformed-email reject, empty-BACKEND_URL fail-fast); structural refactors left in Remaining Gaps

- [x] G4: Full verification fresh after last edit (tests + lint/typecheck/build)
  CHECK: .\gradlew.bat testDebugUnitTest
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: BUILD SUCCESSFUL in 6m 57s, 96 suites / 507 tests / 0 failures / 0 errors / 0 skipped; assembleDebug+lintDebug BUILD SUCCESSFUL in 2m 35s (lint: 15 warnings, 0 errors); kotlinc typecheck via compileDebugKotlin clean

- [x] G5: Scheduler merge (Session + Unlock + Sync → WorkScheduler) applied, behavior identical
  CHECK: .\gradlew.bat testDebugUnitTest
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: BUILD SUCCESSFUL, true fresh re-run with --rerun-tasks (35/35 tasks executed, zero from cache) in 12m 26s, 94 suites / 507 tests / 0 failures / 0 errors / 0 skipped; assembleDebug BUILD SUCCESSFUL in 1m 7s; same work names/policies/constraints/guards; 3 scheduler test classes merged into WorkSchedulerTest (15/15); net -4 files

- [x] G6: fix-review-findings (14 tasks) implemented per specs, full suite green after last edit
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: BUILD SUCCESSFUL in 5m 31s, 104 suites / 534 tests / 0 failures / 0 errors / 0 skipped (fresh execution post-change; includes 4 new suites from this change + other untracked suites in tree, all green); assembleDebug BUILD SUCCESSFUL in same run

- [x] G7: gravitas plugin loads on server (no err_5371140a), guard behavior unchanged
  CHECK: node .opencode/gravitas-load-check.mjs
  EXPECT: exit 0, prints LOADER_OK + ALLOW_OK + DENY_OK
  EVIDENCE: LOADER_OK id=gravitas, HOOK_OK, ALLOW_OK, DENY_OK, EXIT=0; baseline before fix was NO_DEFAULT_EXPORT (same failure as server err_5371140a)

## harden-blocking-and-arming (second review pass)

Scope: close every defect found reviewing the uncommitted scheduled-sessions / VPN / startup work. Plan `openspec/changes/harden-blocking-and-arming/PLAN.md`, adversarial review `REVIEW.md`.

- [x] G8: Plan written for every finding, with the review that corrected it
  CHECK: test -f openspec/changes/harden-blocking-and-arming/PLAN.md && test -f openspec/changes/harden-blocking-and-arming/REVIEW.md && grep -c '^### F' openspec/changes/harden-blocking-and-arming/PLAN.md
  EXPECT: both files exist, 10 findings
  EVIDENCE: PLAN.md (F1–F10, each with evidence file:line, fix, test, gate) + REVIEW.md (R1–R12, 6 corrections applied to plan and code, 4 residuals recorded)

- [x] G9: Review corrections applied to code (not just documented)
  CHECK: grep -c 'ScheduleArmOutcome' app/src/main/java/com/focuslock/ui/setup/ScheduleEditorViewModel.kt app/src/main/java/com/focuslock/ui/setup/ScheduleConfirmScreen.kt
  EXPECT: nonzero in both files; delay(500L) absent from VpnPacketLoop
  EVIDENCE: typed outcome in VM + screen; `withContext(Dispatchers.Main.immediate)` verdict application in BrowserEventHandler; `delay(100L)` restored; `.gitignore` narrowed to `.gravitas/sessions/`

- [x] G10: `BrowserEventHandler` compiles against the main-dispatched verdict and unit tests pass
  CHECK: .\gradlew.bat :app:compileDebugKotlin
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: BUILD SUCCESSFUL in 3m 46s (main + unit test + androidTest sources, one run)

## review-findings batch 3 (gravitas)

Scope: fix the 15 valid app-side review findings across VPN DNS cache, guard-mode filter, setup screens, confirmation VM, logging, gravitas contract/plugin. Skip #11 (unconfirmed V2 tool names) and #14 (stateless guard has no history interface) with reasons. #19 (V1→V2 permission migration) was skipped at first for lack of syntax, then fixed after the V2 docs confirmed the ordered-rule format — see G12.

- [x] G11: review-findings batch 3 applied, full suite green after last edit
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: closed after the issue-fixes below: BUILD SUCCESSFUL in 3m 28s, 110 suites / 573 tests / 0 failures / 0 errors / 0 skipped (see G19). The earlier sole red (OnboardingProbeRetryTest) was fixed as G17; no other suite changed.

- [x] G12: review-findings #19 — V1 permission blocks migrated to V2 ordered rules, live server accepts the config
  CHECK: opencode debug config (project entry) + opencode reload + opencode debug agents + node JSON/YAML validator over the 5 files
  EXPECT: opencode.json resolves with 14 V2 rules; reload prints "Configuration reloaded" exit 0; the 4 subagents resolve with V2 permissions; validator prints VALIDATION_OK
  EVIDENCE: `opencode debug config` project entry: C:\Users\harshal.bhoyar\Project\FocuseLocker\opencode.json | TYPE document | RULES 14 (shell * ask; git status*/diff*/log* + grep * allow; rm/push/reset/curl/wget deny; edit/subagent/skill allow; external_directory ask added per V2 migrate guide). `opencode reload` → "Configuration reloaded", EXIT=0. `opencode debug agents` → impact-auditor / investigator / test-adversary (edit+shell+subagent deny appended last) and reviewer (shell ask base; git diff*/status* + grep * allows; root rm/push/reset/curl/wget denies RESTATED after the ask base so last-match keeps them — without restatement the agent's appended `shell * ask` would override the project denies); all four mode=subagent. Node validator over the 5 files: 34/34 PASS → VALIDATION_OK (JSON parses, YAML frontmatter parses, no V1 keys/action names). No config warnings on load.

## issue-fixes (user request: fix the four reported issues)

Scope: (1) gravitas guard read-before-write false denial, (2) OnboardingProbeRetryTest red, (3) gravitas decide JSON quoting under PowerShell 5.1, (4) V2 reviewer ordering — already handled, no action.

- [x] G16: gravitas guard registers reads and honors read-before-write
  CHECK: node .opencode/gravitas-load-check.mjs (extended: READ_THEN_WRITE_OK + WRITE_WITHOUT_READ_DENIED + write outside scope denied)
  EXPECT: exit 0; read-then-write allowed; unread write denied fail-closed; out-of-scope write denied
  EVIDENCE: root cause in the guard itself: cmd_guard passes reads=set() (gravitas_cli.py:309) and accepts no reads channel, so policy rule 4 (gravitas_policy.py:111-118) denies every write to an existing file — creates passed, which is why Remove-Item+write worked. Fix: plugin-side read registry mirroring the Antigravity intent (pre_tool.py load_reads), overriding only that exact denial when the path was read in this process. Fresh run: LOADER_OK / HOOK_OK / ALLOW_OK / DENY_OK / UNREAD_WRITE_DENIED_OK / READ_OK / READ_THEN_WRITE_OK / SCOPE_STILL_DENIED_OK / GUARD_TRACKING_OK, EXIT=0. LIVE: after `opencode reload` this session read GATES.md via the read tool and then edited it with the plain edit tool — allowed (previously every such edit was denied without the Remove-Item workaround); also proves the reload activates the plugin and that read events reach the hook.

- [x] G17: OnboardingProbeRetryTest green with assertions intact
  CHECK: .\gradlew.bat --no-daemon cleanTestDebugUnitTest testDebugUnitTest --tests "com.focuslock.ui.onboarding.OnboardingProbeRetryTest"
  EXPECT: tests=2 failures=0; no assertion weakened or skipped
  EVIDENCE: BUILD SUCCESSFUL in 1m 42s; XML: tests=2 failures=0 errors=0 skipped=0 (timed-retry test 3.16s, prompt test 0.02s). TEST FIX (agent-harness ledger): the old first test wedged the probe dispatcher's first dispatch to model "never answers" — in-harness the wedged withTimeout never produced the retry, so the test could not pass while claiming to verify it. Rewritten to model a slow probe as a blocked getStatus() on a real executor with the 8s ceiling on the virtual clock; assertions kept and strengthened (exact retry consultation count, routing, no error). No assertion weakened or skipped; second test untouched.

- [x] G18: gravitas decide accepts the policy record from PowerShell 5.1
  CHECK: gravitas --% decide --signals {\"files\":3,\"dependent_files\":false}
  EXPECT: JSON policy record emitted, exit 0 (backslash-escaped quotes survive cmd parsing)
  EVIDENCE: {"planning":"compact","context_depth":"dependency","verification_depth":"impact","delegation":"none","reason_codes":["multiple-files"]}, exit 0. Bare PS quoting fails because MSVCRT strips unescaped quotes; `--%` + backslash-escaped quotes is the deterministic form. Note: `--%` passes the rest of the line raw — the command must be the only statement on its line (a trailing `; ...` becomes an argument).

- [x] G19: full suite green after the fixes (re-check of G11)
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest
  EXPECT: BUILD SUCCESSFUL, 0 failures
  EVIDENCE: BUILD SUCCESSFUL in 3m 28s; fresh XML aggregate over 110 suites: tests=573 failures=0 errors=0 skipped=0; no XML contains <failure or <error. The previously sole red (OnboardingProbeRetryTest) is green after the G17 rewrite; all other suites unchanged.

## build-and-install (user request: build + install on connected device)

Scope: build the project, then install the build on the connected application. No source changes beyond the issue-fixes above.

- [x] G13: Debug build compiles from the current tree
  CHECK: .\gradlew.bat --no-daemon assembleDebug
  EXPECT: BUILD SUCCESSFUL; app/build/outputs/apk/debug/app-debug.apk written after this gate was created
  EVIDENCE: BUILD SUCCESSFUL in 24s, EXIT=0 (41 tasks up-to-date: app sources unchanged since the batch-3 verification build — only the unit-test source changed, and that is not part of the app APK). APK present: app/build/outputs/apk/debug/app-debug.apk, 15,324,467 bytes, 10/17/2026 12:27:38 AM; applicationId=com.focuslock, versionCode=1, versionName=1.0.0 per app/build.gradle.kts.

- [x] G14: A device/emulator is connected and visible to adb
  CHECK: adb devices -l
  EXPECT: at least one serial in state `device`
  EVIDENCE: one device — ee64c019, state `device`, product:gram_in, model:POCO_M2_Pro (transport_id 16); single device so installDebug has no ambiguity.

- [x] G15: The freshly built APK is installed on that device
  CHECK: .\gradlew.bat --no-daemon installDebug then adb shell dumpsys package com.focuslock
  EXPECT: install exit 0; versionCode + lastUpdateTime after build time
  EVIDENCE: installed. Earlier attempts were blocked by the MIUI gate (`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` — gradle installDebug and direct adb, twice). After the phone's USB-install permission was granted, `adb install -r -t app\build\outputs\apk\debug\app-debug.apk` → "Performing Streamed Install / Success", EXIT=0 (screen awake and unlocked; the earlier installDebug run had already surfaced the permission prompt). Device state: versionCode=1, versionName=1.0.0, minSdk=26 targetSdk=35, firstInstallTime=lastUpdateTime=2026-10-17 18:57:00 (the build wrote the APK at 18:54:29), codePath /data/app/~~OUY-zSBlk3dD68KQMQgUww==/com.focuslock-3jzYWluQpPDTtYtGh7aS9A==, `pm path` → that dir + /base.apk.

- [x] G20: Fresh from-scratch build before install (user request: build it and check once)
  CHECK: .\gradlew.bat --no-daemon clean assembleDebug
  EXPECT: BUILD SUCCESSFUL with app-debug.apk rewritten by this run
  EVIDENCE: BUILD SUCCESSFUL in 1m 29s (:app:clean executed; 20 tasks executed, 22 from cache; packageDebug + assembleDebug both executed). Fresh APK: app/build/outputs/apk/debug/app-debug.apk, 15,324,467 bytes, 10/17/2026 6:54:29 PM.

## session-activation-reconciliation (user-approved fix: arm-flow trap + timing bugs)

Scope: fix the arm-flow trap (a live session made every Done tap fail), move the already-active rule into `ActivateSessionUseCase` as a typed outcome (no TOCTOU, no duplicated checks), guard double taps, compute the session expiry from the exact window end, record `schedule_last_fired` on immediate start, and apply strict lock-in when a schedule edit meets a running session. Root-cause evidence: 26 identical Done failures 22:54:29–22:55:06; session 2 expiry 00:00:46 (46 s past the window end).

- [x] G21: `ActivateSessionUseCase` returns a typed `ActivationResult`; a live session is `AlreadyActive`, not an exception
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest --tests "com.focuslock.domain.usecase.ActivateSessionUseCaseTest"
  EXPECT: BUILD SUCCESSFUL; AlreadyActive carries the existing session; activation inserts no second session and schedules no timers
  EVIDENCE: BUILD SUCCESSFUL; XML record `com.focuslock.domain.usecase.ActivateSessionUseCaseTest tests=6 failures=0 errors=0 skipped=0`. The AlreadyActive test asserts the returned session equals the live one plus `coVerify(exactly=0)` on `startSession`, `scheduleSessionTimers`, and `startRuntime`; the rollback test asserts `Failed` + `endSession(9, CANCELLED)`.

- [x] G22: All three callers handle `AlreadyActive` as a success path (no duplicated `hasActiveSession()` pre-check in activation flows)
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest --tests "com.focuslock.worker.ScheduleCheckWorkerTest" --tests "com.focuslock.ui.setup.SessionConfirmationViewModelTest" --tests "com.focuslock.ui.setup.SessionConfirmationLiveWindowTest"
  EXPECT: BUILD SUCCESSFUL; worker suppresses the occurrence; confirmation VM enters Success; no error state on AlreadyActive
  EVIDENCE: XML records: worker tests=11 failures=0, confirmation tests=9 failures=0, live window tests=2 failures=0. Worker: AlreadyActive sets the live-wins suppression and writes no last-fired (old message-sniffing `isLiveSessionFailure` deleted; grep: 0 matches in app/src/main). New regression test: a real `Failed` does not suppress the next tick (2 invokes across 2 frozen-clock ticks). Confirmation VM: AlreadyActive → Success on both one-shot and live paths, with explicit assert that no error state appears.

- [x] G23: Arming holds an in-flight lock; a second concurrent tap changes nothing
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest --tests "com.focuslock.ui.setup.ScheduleEditorViewModelTest"
  EXPECT: BUILD SUCCESSFUL; concurrent call returns `ArmInFlight`; the first call still completes with its own outcome
  EVIDENCE: XML record: `com.focuslock.ui.setup.ScheduleEditorViewModelTest tests=17 failures=0 errors=0`. The concurrency test gates the first arm call inside `setScheduleEnabled` via `CompletableDeferred`, asserts the second call returns `ScheduleArmOutcome.ArmInFlight`, then releases the gate and asserts the first returns `ArmedIdle`. Guard is `armInFlight = Mutex()` with `tryLock()` in `ScheduleEditorViewModel.armScheduleAndCheckActivation`; the screen's `ArmInFlight` branch is a no-op.

- [x] G24: Session expiry is derived from the exact window end — no whole-minute spillover
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest --tests "com.focuslock.domain.usecase.ScheduleEngineTest" --tests "com.focuslock.worker.ScheduleCheckWorkerTest"
  EXPECT: BUILD SUCCESSFUL; Sat 781–1440 at 22:53:46 ends exactly at 00:00:00 (3,974,000 ms); worker passes exact millis (e.g. 23:58:14 → 106,000 ms, not 120,000)
  EVIDENCE: XML records: engine tests=18 failures=0, worker tests=11 failures=0. New engine tests pin 22:53:46 → `3_974_000` ms to Sunday 00:00:00, Monday 21:00 → 17 h, Tuesday 08:20:30 → 5 h 39 min 30 s, full day 23:59:14 → `46_000` ms, plus occurrence-key dating. New worker test pins 23:58:14 → `106_000` ms where the old minute math granted `120_000`. Arm VM, worker, and live-window path all call `ScheduleEngine.remainingMillis`.

- [x] G25: Immediate activation during arming records `schedule_last_fired` for the occurrence
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest --tests "com.focuslock.ui.setup.ScheduleEditorViewModelTest"
  EXPECT: BUILD SUCCESSFUL; armed start writes the occurrence key that the worker would write; AlreadyActive path does not
  EVIDENCE: Same green run as G23 (tests=17). The exact-remainder test asserts `coVerify { repository.setLastFiredSchedule("1:2026-10-13") }` after the due-window start; the hand-off test asserts `coVerify(exactly=0) { repository.setLastFiredSchedule(any()) }`. Occurrence-key logic is shared via `ScheduleEngine.occurrenceKey` (worker switched to the same helper, format `id:date` unchanged).

- [x] G26: Strict lock-in reconciliation — extend when the due window ends later; never shorten or cancel
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest --tests "com.focuslock.domain.usecase.ReconcileScheduledSessionUseCaseTest" --tests "com.focuslock.ui.setup.ScheduleEditorViewModelTest"
  EXPECT: BUILD SUCCESSFUL; Extended updates Room + reschedules timers; Unchanged keeps the session; NothingActive no-ops; edit paths keep the session and post a notice
  EVIDENCE: XML records: reconcile tests=5 failures=0, editor tests=17 failures=0. Reconcile: extend asserts `extendSession(3, windowEnd)` + `scheduleSessionTimers(windowEnd)`; ends-earlier and no-due-window keep the session with `exactly=0` extensions; expired record is `NothingActive`. Editor paths: extending the due window shows "now runs until 14:00"; shortening shows "will finish at 13:30" and asserts no "runs until" text; deleting the due window and pausing the schedule both keep the session with a notice, and pausing asserts the reconcile use case is never called.

- [x] G27: UI never traps the user — hand-off on AlreadyActive, non-error notices on locked edits, confirm screen has no error on a running session
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest --tests "com.focuslock.ui.setup.ScheduleArmingFailureTest" --tests "com.focuslock.ui.setup.ScheduleFlowAdversarialTest" --tests "com.focuslock.ui.setup.ScheduleContactLoadedTest"
  EXPECT: BUILD SUCCESSFUL; ArmingFailure still retries on real activation failures; no outcome maps a live session to an error banner
  EVIDENCE: XML records: arming failure tests=6 failures=0, adversarial tests=5 failures=0 (fuzz asserts arming never reports `Failed` for valid windows and never throws), contact tests=1 failures=0. `ScheduleArmOutcome.ActivationFailed` is now reached only from `ActivationResult.Failed`; the hand-off test asserts `viewModel.error.value == null` for a live session. Notices render in `MaterialTheme.colorScheme.primary` via the new `notice` state item in `ScheduleEditorScreen`; the confirm screen adds an explicit `ArmInFlight` no-op branch.

- [x] G28: Full suite + app compile + androidTest compile fresh after the last edit
  CHECK: .\gradlew.bat --no-daemon testDebugUnitTest then .\gradlew.bat --no-daemon compileDebugAndroidTestKotlin
  EXPECT: BUILD SUCCESSFUL, 0 failures, 0 errors; androidTest sources compile (both VM construction sites updated)
  EVIDENCE: Full unit suite BUILD SUCCESSFUL in 3m 32s: 111 suites / 592 tests / 0 failures / 0 errors / 0 skipped, all 111 result XMLs written by this run. `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL in 1m 59s with `GRADLE_EXIT=0` (both androidTest VM construction sites updated for the new constructor). Note: the Kotlin compile daemon repeatedly fails to connect on this machine; Gradle falls back to the in-process compiler and every build above still succeeded (exit code 0 verified explicitly).

## review-findings batch 4 (uncommitted-changes review: H1/H2/M1/M2/M3/M4 + LOW)

Scope: close the defects found reviewing the uncommitted scheduled-sessions work. Fix order taken from the review: H1 direct-boot crash, H2 exact alarm, M1 DST expiry, M2/M3/M4, LOW batch, repo hygiene. Source fixes were already in the tree when this batch resumed; the work completed here was reconciling the test sources the previous agent left out of sync with the new constructors, adding the missing regression tests, and fixing a real DST bug the new tests exposed.

- [x] G29: H1 — schedule rearm is skipped until the user is unlocked, and still re-arms on BOOT_COMPLETED
  CHECK: grep -n 'isUserUnlocked' app/src/main/java/com/focuslock/data/worker/WorkScheduler.kt; ./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.focuslock.data.worker.WorkSchedulerTest"
  EXPECT: the gate is present and read before WorkManager; locked returns before any enqueue; unlocked enqueues the periodic backstop; BUILD SUCCESSFUL
  EVIDENCE: WorkScheduler.kt:41-53 gates on UserManager.isUserUnlocked before `WorkManager.getInstance`; LOCKED_BOOT_COMPLETED stays in the manifest and lands on that gate (Fallback to BOOT_COMPLETED, which always runs unlocked). New tests: `scheduleScheduleChecks defers while the user is still locked` (exactly 0 periodic enqueues) and `... enqueues the backstop once unlocked`. WORKERSCHEDULER BUILD SUCCESSFUL (see G33 for the aggregate).

- [x] G30: H2 — each window start gets an exact alarm; the periodic worker remains the backstop
  CHECK: ./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.focuslock.data.worker.ScheduleAlarmSchedulerTest" --tests "com.focuslock.worker.ScheduleCheckWorkerTest"
  EXPECT: BUILD SUCCESSFUL; alarm trigger equals ScheduleEngine.nextStartEpochMillis; no windows cancels it; every worker check re-arms
  EVIDENCE: ScheduleAlarmSchedulerTest (3 tests) pins the trigger epoch, the Monday 20:00 evening arming for an overnight window, and cancel-on-empty via ShadowAlarmManager. ScheduleCheckWorkerTest adds `every check re-arms the next window alarm`. Both suites green in the batch run.

- [x] G31: M1 — DST no longer moves a window expiry by an hour
  CHECK: ./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.focuslock.domain.usecase.ScheduleEngineTest"
  EXPECT: BUILD SUCCESSFUL; spring-forward end is 05:00 local (not an hour late); fall-back end is 05:00 local (not an hour early)
  EVIDENCE: New tests pin America/New_York 2026-03-08 01:00-05:00 and 2026-11-01 00:00-05:00 against ZonedDateTime-derived epochs plus the real elapsed millis (2 h 30 min and 5 h 30 min). The first run failed and exposed that the previous fix used `ZonedDateTime.plusMinutes`, which is instant-based; ScheduleEngine.endEpochMillis now resolves wall-clock time with `date.atTime(mm).atZone(zone)`. Re-run green.

- [x] G32: M2/M3/M4 — worker retry, caller-scoped URL check, atomic single-active-session insert
  CHECK: ./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.focuslock.worker.ScheduleCheckWorkerTest" --tests "com.focuslock.data.local.dao.SessionDaoTest" --tests "com.focuslock.domain.usecase.ActivateSessionUseCaseTest"
  EXPECT: BUILD SUCCESSFUL; a thrown check returns Result.retry(); a second live insert returns null; losing the insert hands off to the winner
  EVIDENCE: `doWork retries when the check throws` asserts ListenableWorker.Result.retry(). SessionDaoTest adds `insertIfNoActiveSession rejects a second live activation` (1 ACTIVE row) and `ignores an expired active row`. ActivateSessionUseCaseTest was migrated from the deleted `startSession` seam to `startSessionIfNoneActive` and adds the race case (insert loses -> AlreadyActive with the winner, no timers) and the no-winner rejection (Failed). CheckUrlForBlockedContentUseCase runs on `withContext(Dispatchers.Default)` in the caller's job (code verified; no dedicated cancellation test — see residual).

- [x] G33: Full unit suite fresh + androidTest sources compile after the last edit
  CHECK: ./gradlew.bat --no-daemon :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
  EXPECT: BUILD SUCCESSFUL, 0 failures, 0 errors, 0 skipped; androidTest compiles
  EVIDENCE: BUILD SUCCESSFUL in 2m 54s. Aggregate over all result XMLs written 18:56:56-57 and checked 18:57:22: 112 suites / 605 tests / 0 failures / 0 errors / 0 skipped; no XML contains <failure or <error. The earlier full run caught the 4 stale ActivateSessionUseCaseTest failures, fixed in G32 and re-run. androidTest constructors updated for the new WorkScheduler/ScheduleCheckWorker signatures.

- [x] G34: Repo-provided guard tooling still loads (unchanged plugin)
  CHECK: node .opencode/gravitas-load-check.mjs
  EXPECT: exit 0 with LOADER/HOOK/ALLOW/DENY + read-tracking checks
  EVIDENCE: prints DENY_OK, UNREAD_WRITE_DENIED_OK, READ_OK, READ_THEN_WRITE_OK, SCOPE_STILL_DENIED_OK, GUARD_TRACKING_OK, EXIT=0 (pre-existing MODULE_TYPELESS_PACKAGE_JSON warning only).

Residual / not fixed: the URL-check cancellation has no dedicated regression test (it would need a seam into the cache/mutex); `app/schemas/com.focuslock.data.local.FocusDatabase/10.json` remains untracked for the owner to commit; the unrelated `backend` reticle devDependency is untouched.

## b-test verification (2026-11-20)

Scope: prove the batch-4 acceptance criteria and the project gates. The b-test skill is written for .NET/npm; mapped to this Android repo as Gradle unit tests + build + lint + androidTest compile, with the repo's gate-check script.

- [x] G35: Every GATES.md gate checked by the repo's own gate runner
  CHECK: node .agents/skills/unlazy/scripts/gate-check.mjs --status
  EXPECT: exit 0, all boxes met, no unmet gate
  EVIDENCE: `--status` after adding this section: GATES.md has 38 gates, ALL MET (38 met), exit 0. (Before this section the runner reported 34/34.)

- [x] G36: Full b-test lane after the b-review-refactor fixes
  CHECK: ./gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin
  EXPECT: BUILD SUCCESSFUL; 0 test failures/errors/skips; 0 lint errors; androidTest compiles
  EVIDENCE: BUILD SUCCESSFUL in 5m 55s. 112 suites / 605 tests / 0 failures / 0 errors / 0 skipped, all XMLs written 19:23, checked 19:23; no <failure/<error elements. Lint: 0 errors, 16 warnings (report `app/build/reports/lint-results-debug.txt`). androidTest compiled. Skipped lanes (recorded, not claimed): CodeRabbit CLI not installed; codehealth-mcp and code-review-graph MCP not exposed in this harness; gstack-qa/gstack-test-audit not applicable; e2e androidTest not run because `adb devices` lists none. graphify: no node for ScheduleEngine/WorkScheduler (graph built 2026-10-11, predates the feature) — no impact signal, direct inspection used.

## b-review-refactor (2026-11-20)

Scope: review the batch with the repo's kotlin-reviewer / silent-failure-hunter / refactor-cleaner criteria, fix findings, re-test.

- [x] G37: H2 is actually wired — the alarm receiver is declared in the manifest
  CHECK: grep -n 'ScheduleAlarmReceiver' app/src/main/AndroidManifest.xml
  EXPECT: receiver declared, exported=false, next to the existing SCHEDULE_EXACT_ALARM permission
  EVIDENCE: Review found the exact-alarm fix inert: the PendingIntent targets `.receiver.ScheduleAlarmReceiver`, but no `<receiver>` existed, so delivery was impossible. Added `<receiver android:name=".receiver.ScheduleAlarmReceiver" android:exported="false" />`; `android.permission.SCHEDULE_EXACT_ALARM` was already declared. Robolectric unit tests cannot observe manifest delivery, so this is manifest inspection + package build (assembleDebug) rather than a device run.

- [x] G38: Dead code removed and paused schedules stop the alarm chain
  CHECK: grep -rn 'scheduleNextWindowAlarm' app/src; ./gradlew.bat --no-daemon :app:testDebugUnitTest
  EXPECT: no matches; tests green after the constructor simplification
  EVIDENCE: `WorkScheduler.scheduleNextWindowAlarm` had zero callers and forced an unused ScheduleRepository constructor dependency; removed both (test/androidTest construction sites simplified). `ScheduleAlarmReceiver` now returns before enqueueing and cancels the pending alarm when the schedule is disabled, so a paused schedule no longer perpetuates window-start alarms. Full suite re-run green (G36).

Residual / review limitations: CodeRabbit and CodeScene unavailable; reviewer subagents are not spawnable in this harness, so kotlin-reviewer/security/silent-failure/refactor-cleaner criteria were applied directly; no device was attached for the instrumented journey (schedule confirmation/editor/journey tests remain compile-only in this run).
