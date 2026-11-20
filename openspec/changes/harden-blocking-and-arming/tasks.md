# Tasks

## 1. URL verdict guard (spec: app-blocking)

- [ ] 1.1 Make the latest-URL guard visible across threads in `BrowserEventHandler` (`@Volatile`), and verify the burst-event test still applies only the latest verdict
- [ ] 1.2 Apply the verdict on the thread that publishes the URL (`Dispatchers.Main.immediate`), so the guard read and the effects it guards cannot be interleaved by a newer publication, and verify a test where the effects are absent until the main dispatcher runs and applied once it does *plus* a test that the latest blocked page is still enforced
- [ ] 1.3 Confirm `dispose()` still cancels both the in-flight check and the scope, and verify no pending check outlives the service

## 2. DNS cache bounds (spec: app-blocking)

- [ ] 2.1 Replace the unbounded `ConcurrentHashMap` DNS cache with the existing bounded `SimpleLruCache`, and verify repeated distinct lookups never exceed the configured entry cap
- [ ] 2.2 Delete the 60s sweeper coroutine and confirm expired entries are still dropped lazily on read, and verify a run loop with many distinct lookups launches no sweeper
- [ ] 2.3 Key each entry on a fingerprint of the query's post-header bytes (question section + additional records) so a stored answer is only replayed to a matching query, and verify with unit tests that a query differing in question casing, and a query differing in additional records, both miss the cache
- [ ] 2.4 Cover the bound itself with a `SimpleLruCache` unit test (eviction at the cap, recency on `get`), since the cache is now a bounded container rather than an unbounded map

## 3. Schedule arming (spec: scheduled-sessions)

- [ ] 3.1 Surface a `scheduleScheduleChecks()` failure as an arming failure before `setOnboardingComplete()`, and verify a test asserting arming reports `Failed` and onboarding stays incomplete
- [ ] 3.2 Surface a `setOnboardingComplete()` failure without discarding the armed schedule, and verify a test asserting the schedule stays enabled and the error is exposed
- [ ] 3.3 Replace the boolean with `ScheduleArmOutcome` (`ArmedIdle` / `LiveWindow` / `Failed`) so failure is a distinct state from the terminal success, and verify the screen keeps its retryable state on `Failed`
- [ ] 3.4 Guard the saved-window read so a storage failure reports `Failed` instead of escaping the suspend function, and verify with a test where `getWindowsSync()` throws

## 4. Session confirmation scoping (spec: scheduled-sessions)

- [ ] 4.1 Add the `live` flag to the session-confirmation route with a default of `false`, build the schedule hand-off destination with `live=true`, and verify the navigation tests pass for both shapes
- [ ] 4.2 Require `liveWindow` for the already-active shortcut, and verify a test that a one-shot start with an unrelated active session does not short-circuit and still reports the requested duration
- [ ] 4.3 Keep the schedule hand-off shortcut working, and verify a test that a live window still reports success without a second activation

## 5. Startup probe (spec: app-startup)

- [ ] 5.1 Re-probe the permission state once on timeout before degrading to not-granted, and verify a test where the first probe times out and the second succeeds reports granted permissions
- [ ] 5.2 Replace the two unobservable `runCatching`/`getOrThrow` captures with plain awaits inside `supervisorScope`, and verify a test where the status call fails still surfaces the error

## 6. Housekeeping (no spec change)

- [ ] 6.1 Re-indent the DoH `else` block in `VpnPacketLoop.runLoop` and correct the "pool capped at 10" comment, and verify the file compiles and the loop tests pass
- [ ] 6.2 Correct the stale "widened (null)" comment and the `SessionWorkerScheduler:` log tags, and verify the package filter test passes
- [ ] 6.3 Guard `releaseBuffer` against recycling non-pool-sized arrays, and verify the buffer-pool test passes
- [ ] 6.4 Ignore only machine-local agent state (`.opencode/node_modules/`, `.gravitas/sessions/`) without touching tracked files, and verify with `git check-ignore` that project config and gate evidence (`.opencode/commands`, `.reticle.json`, `skills-lock.json`, `.verification/`) are *not* ignored
- [ ] 6.5 Revert the unreviewed TUN idle-poll change (`delay(500L)` back to `delay(100L)`), and verify the loop tests pass

## 7. Integration verification

- [ ] 7.1 Run the full `testDebugUnitTest` suite and verify BUILD SUCCESSFUL with zero failures, then `lintDebug` and `assembleDebug` and verify both BUILD SUCCESSFUL
- [ ] 7.2 Record the evidence (test counts, build results) in `LEDGER.md` and the 8-point `VALIDATION.md`, and list unresolved findings under Remaining Gaps
