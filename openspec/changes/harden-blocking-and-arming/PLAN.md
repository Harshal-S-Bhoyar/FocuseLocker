# Plan — harden blocking and arming

Scope: close every defect found in the second review pass over the uncommitted scheduled-sessions /
VPN / startup work. No new features, no new screens, no public API changes, no changes to
`ScheduleEngine` maths, WorkManager policies, or the block-list matching path.

Findings are ordered by severity. Each row names the evidence (file:line at review time), the fix,
and the gate that proves it. `REVIEW.md` records the adversarial pass over this plan and the
corrections it forced.

## Findings and fixes

### F1 — URL verdict guard is unobservable, and the check-then-act race is open (High)

Evidence: `app/src/main/java/com/focuslock/service/accessibility/BrowserEventHandler.kt:35`
(`latestUrl` plain `var`), `:64` (`if (url != latestUrl) return@launch` read on
`Dispatchers.Default` while the accessibility callback writes it on main).

Problems:
1. A plain field written on the callback thread and read on a `Dispatchers.Default` worker is not
   guaranteed to be visible; the guard can read a stale value and act on a superseded verdict.
2. Even with visibility fixed, `if (url != latestUrl)` followed by effects is a read-then-act on two
   threads: a newer URL can be published between the read and the effect, so a stale verdict can
   still navigate back / launch the overlay on the new page.

Fix: publish `latestUrl` safely (keep `@Volatile` as defence in depth) **and** run the guard read +
effects inside `withContext(Dispatchers.Main.immediate)`, i.e. on the same thread that publishes the
URL. There is no suspension between the read and the effects, and main is single-threaded, so
publication and application are serialized: a newer URL can no longer land in the window. Matching
stays off Main (`Dispatchers.Default`); only verdict application moves. _Corrected during review
(`REVIEW.md` R1): the originally planned per-effect re-read closes nothing, because the read and the
effect are still on different threads from the write._

Tests: existing `BrowserEventSingleFlightTest` (superseded verdict discarded; dispose cancels) now
running with a test main dispatcher, plus new `blocked verdict effects apply on the main dispatcher`
(absent while Main has not run after the check finished, applied once it runs) and
`latest blocked page is still enforced` (the positive path no test covered).

Gate: G10.

### F2 — DNS response cache is unbounded in entries (High)

Evidence: `app/src/main/java/com/focuslock/service/vpn/VpnPacketLoop.kt` (pre-fix
`ConcurrentHashMap<String, CachedDns>` with a 60 s sweeper and no cap). Keys come from DNS names the
visited page chooses, so a page resolving many unique names grows the map without limit for the life
of the session.

Fix: replace the map with the existing bounded `com.focuslock.domain.util.SimpleLruCache`
(`@Synchronized` `LinkedHashMap`, `MAX_CACHE_ENTRIES = 2048`). Expired entries are already dropped
lazily on read, so the bound holds without a reaper.

Tests: the bound is provided by `SimpleLruCache`, which now has `SimpleLruCacheTest` (new: eviction
at the cap, recency on `get`, remove/clear) — previously untested despite four features depending on
it. `VpnPacketLoopCacheTest` keeps covering the wiring through the loop (hit, TXID/port rewrite,
qtype isolation, fingerprint mismatch, blocked-domain bypass). _Corrected during review
(`REVIEW.md` R7): the originally claimed cap/no-sweeper tests through the loop do not exist and are
not practical — observing the cap needs `MAX_CACHE_ENTRIES + 1` distinct domains through a socket
pair, and there is no sweeper left to observe._

Gate: G11.

### F3 — The live-window shortcut absorbs an unrelated running session (High)

Evidence: `app/src/main/java/com/focuslock/ui/setup/SessionConfirmationViewModel.kt:46,69`
(pre-fix `if (sessionRepository.hasActiveSession()) { Success; return }` on every entry path).

Problem: every entry path reaches the same screen, so a one-shot start with a chosen duration
silently adopts whatever session is running and discards the requested duration.

Fix: the schedule hand-off navigates with `?live=true`
(`AppNavigation.kt:71-72`), the route declares `live` with `defaultValue = false` (`:238`), and the
shortcut is gated on `liveWindow && hasActiveSession()`. Other callers keep their route shape and
read `false`.

Tests: `ScheduleNavTest` (both route shapes), `SessionConfirmationViewModelTest` (one-shot with an
unrelated active session still activates the requested duration; live window succeeds without a
second activation; no running session activates normally).

Gate: G12.

### F4 — Arming reports success that did not happen, and a failure is not retryable (High)

Evidence: `app/src/main/java/com/focuslock/ui/setup/ScheduleEditorViewModel.kt:78-83`
(`scheduleScheduleChecks()` failure only logged), and
`app/src/main/java/com/focuslock/ui/setup/ScheduleConfirmScreen.kt:64-72` (pre-fix
`if (!activated) { isConfirmedArmed = true; onDone(null) }`).

Problems:
1. A failure to enqueue the periodic checks was logged and ignored, so the flow could report an
   armed schedule whose worker was never enqueued; onboarding then completed for a schedule that
   can never fire.
2. `false` from `armScheduleAndCheckActivation()` means both "armed, nothing due now" (the success
   terminal state) and "arming failed". The screen therefore renders the armed banner and disables
   the button on a failure: the user sees the error text *and* a success state with no way to retry.
3. `scheduleRepository.getWindowsSync()` is not guarded, so a storage failure escapes the suspend
   function and crashes the caller instead of failing the arming.

Fix: make arming total and explicit. _Extended during review (`REVIEW.md` R2, R3): the first revision
surfaced the error text but left the screen reporting success on failure with the retry button
disabled, and left the storage read unguarded so a storage failure crashed the caller instead of
failing the arming._

- Return `ScheduleArmOutcome` — `ArmedIdle` (armed, no window due now) / `LiveWindow(remainingMillis)`
  (armed, window live now) / `Failed` (nothing armed; `error` explains why). Arming never throws:
  the due-window read and activation are wrapped like the other steps.
- The screen switches on the outcome: `Failed` keeps the confirm screen and its retryable button
  (error text already rendered at `ScheduleConfirmScreen.kt:121-127`), `ArmedIdle` shows the terminal
  banner and reports no hand-off, `LiveWindow` reports the remaining duration.
- `liveRemainingMillis` state is deleted: the outcome carries it, so there is one source of truth.

Tests: `ScheduleArmingFailureTest` (enable failure, enqueue failure, onboarding-flag failure, storage
failure → `Failed`, onboarding stays incomplete), `ScheduleEditorViewModelTest` / 
`ScheduleFlowAdversarialTest` (updated call sites), `ScheduleConfirmArmingTest` (androidTest: Done
stays enabled after a failing arm).

Gate: G13.

### F5 — Unobservable result capturing in startup (Medium)

Evidence: `OnboardingViewModel.initialize()` — two `runCatching`/`getOrThrow()` pairs inside
`supervisorScope`; the first `getOrThrow()` throws before the second capture is ever read, so the
second capture can never be observed and the comment overstated what it bought.

Fix: plain `await()`s inside the existing `supervisorScope` (which is what actually prevents sibling
cancellation). Delete the dead capturing.

Tests: `OnboardingViewModelTest` (`initializeAppUseCase` failure still surfaces; existing routing
cases unchanged), `OnboardingProbeRetryTest`.

Gate: G14.

### F6 — One slow binder probe presents a false "permissions missing" flow (Medium)

Evidence: `OnboardingViewModel.probePermissions()` — a single 8 s timeout degraded to all-false,
routing a merely slow device into the permission-repair flow.

Fix: `probePermissions()` re-probes once on `TimeoutCancellationException` and only then returns the
not-granted status (`:138-155`). The existing contract and repair-flow fallback are unchanged; no UI
or signature change.

Tests: `OnboardingProbeRetryTest` (timed-out probe retried exactly once; prompt probe consulted once).

Gate: G14.

### F7 — A DNS cache sweeper per run loop (Medium)

Evidence: `VpnPacketLoop.runLoop()` launched a 60 s sweeper coroutine into `loopScope` on every call,
and nothing bounded the number of sweeper generations. The bound in the cache made the sweeper
redundant.

Fix: delete the sweeper; expiry is enforced on read (`serveCachedResponse`) and `SimpleLruCache`
bounds entries. Verified by reading plus the loop tests; there is deliberately no test asserting the
absence of a coroutine that no longer exists (`REVIEW.md` R7).

Gate: G11.

### F8 — The buffer pool accepts buffers of any size (Low)

Evidence: `VpnPacketLoop.releaseBuffer` offered every array back, including the larger array
`buildTunDnsResponse` allocates when a response exceeds the pool size.

Fix: `releaseBuffer` recycles only `POOL_BUFFER_SIZE` arrays (`:68-74`); the pool keeps meaning what
it says.

Test: `VpnPacketLoopCacheTest` — `releaseBuffer` is called reflectively with an oversized array (pool
stays empty) and with a pool-sized array (pool takes it back). Reflection matches the existing
`PackageFilterTest` convention. The oversized path is otherwise unreachable in a unit test because the
DoH mock cannot produce a response larger than the pool.

Gate: G11.

### F9 — Agent-tooling paths not ignored, and a broad ignore would hide project files (Low)

Evidence: `.gitignore:128-135`. Originally: rules covered sibling agent directories that are not in
this tree, while present ones were untracked noise.

Fix (narrowed during review, `REVIEW.md` R4): ignore only state that is unambiguously machine-local —
`.opencode/node_modules/` and `.gravitas/sessions/`. `.verification/*-LEDGER.md` is cited as evidence
by the tracked `GATES.md`, `.reticle.json` carries the Reticle `projectId`, `skills-lock.json` is a
lockfile for `npx skills add`, and `.opencode/{commands,skills,plugins}` contains tracked project
config — ignoring any of those would silently stop a deliberate file from ever being committed. Those
stay visible for the user to commit or ignore deliberately (see Remaining Gaps).

Test: `git status --porcelain` no longer lists the two state paths; `git check-ignore -v` confirms
the tracked paths are not ignored.

Gate: G15.

### F10 — Stale comments, mis-indentation, undocumented idle-poll change (Low)

Evidence: `VpnPacketLoop.kt` (DoH `else` block mis-indented; "pool capped at 10" comment),
`BrowserEventHandler` (stale "widened (null)" comment), `FocusAccessibilityService` /`WorkScheduler`
log tags naming removed scheduler classes.

Fix: correct the comments and indentation, no behaviour change. Additionally the unreviewed
`delay(100L)` → `delay(500L)` on the TUN zero-read path is **reverted to 100L**: it belongs to no
finding here, it was never measured, and it would add up to 400 ms of latency to DNS handling in the
zero-read path. Any idle-poll change belongs in its own change with a measurement (see Remaining
Gaps).

Test: compile + `VpnPacketLoopCacheTest` + `PackageFilterTest`.

Gate: G10, G11.

## Verification plan

| Gate | Command | Expected |
|---|---|---|
| G10 | `gradlew testDebugUnitTest --tests "*BrowserEvent*"` | BUILD SUCCESSFUL, 0 failures |
| G12 | `gradlew testDebugUnitTest --tests "*ScheduleNav*" --tests "*SessionConfirmationViewModel*"` | BUILD SUCCESSFUL |
| G13 | `gradlew testDebugUnitTest --tests "*ScheduleArmingFailure*" --tests "*ScheduleEditorViewModel*" --tests "*ScheduleFlowAdversarial*"` | BUILD SUCCESSFUL |
| G14 | `gradlew testDebugUnitTest --tests "*Onboarding*"` | BUILD SUCCESSFUL |
| G11 | `gradlew testDebugUnitTest --tests "*VpnPacketLoopCache*" --tests "*SimpleLruCache*" --tests "*DnsTtlParser*"` | BUILD SUCCESSFUL |
| G15 | `git check-ignore -v .opencode/commands/opsx-apply.md .reticle.json skills-lock.json .verification/LEDGER.md` | no output (none ignored) |
| G16 | `gradlew testDebugUnitTest` | BUILD SUCCESSFUL, 0 failures / 0 errors |
| G17 | `gradlew lintDebug assembleDebug` | BUILD SUCCESSFUL |

Evidence for every gate is recorded in `LEDGER.md`; the 8-point validation report (per
`projects/Validation rules.md`) is `VALIDATION.md`.

## Non-goals

- No change to `ScheduleEngine` evaluation, WorkManager policies, block-list contents, or keyword
  matching.
- No new screens, no route renames, no public API changes.
- No TUN idle-poll tuning, no DNS cache-size tuning beyond the existing entry bound, no sweeper
  replacement.

## Note on Reticle

Not run: every user-visible file in this change is Kotlin/Compose inside the Android app, which
Reticle cannot drive through a browser, and no in-scope change reaches the `backend/` web surface.
Verification is by unit tests, lint, and assemble.
