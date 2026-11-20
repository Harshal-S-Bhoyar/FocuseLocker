# Review — harden blocking and arming (adversarial pass over PLAN.md)

Reviewed `PLAN.md` against the actual code, tests, spec deltas, and the two governing rule sets
(`projects/Validation rules.md` 8 points; `Development_Principles/CODEBASE_Principles_VALIDATION_AGENT.md`
Parts 1–3). Method: for every planned fix, re-read the target file, the tests that pin it, and the
callers, then ask what the fix does *not* cover and whether the claimed test can actually fail.

Corrections below are labelled `R#`. The plan and the change documents have been amended to match;
the corrections marked **applied** are implemented in code.

## R1 — F1: re-reading the guard before each effect does not close the window (High) — applied

The plan (and `design.md` decision 1) claimed that re-reading `latestUrl` immediately before each
side effect closes the guard-to-effect window, justified by "cancellation cannot interrupt an
in-flight `performGlobalAction`". That reasoning is unsound: `latestUrl` is written by the
accessibility callback thread while the check coroutine runs on `Dispatchers.Default`, so a write can
land *between* the re-read and the effect. Re-reading per effect changes nothing — the sequence is
still read-then-act across two threads, and a superseded verdict can still press BACK on the page the
user just navigated to. `@Volatile` fixes observability (the original F1 defect), not atomicity.

Correction: apply the verdict on the thread that publishes it. The guard read and the effects now run
inside `withContext(Dispatchers.Main.immediate)`; `handleBrowserEvent`, `dispose`, and the guard read
are all main-confined, main is single-threaded, and there is no suspension between the read and the
effects, so publication and application are serialized by construction. Matching stays on
`Dispatchers.Default` (unchanged cost). The move is safe: `SmartScanOrchestrator.startScan` is not a
suspend function and only launches into its own scope, and `performGlobalAction` / `startService` /
`stopService` are cheap main-thread calls.

`@Volatile` is kept as defense-in-depth with an accurate comment (the handler is also constructed in
tests, which publish from the test thread), but the correctness argument no longer depends on it.

## R2 — F4: the failure path rendered the success state and blocked retry (High) — applied

Reading `ScheduleConfirmScreen.kt:64-72` against the new error rendering showed the F4 fix was
half-done. `armScheduleAndCheckActivation()` returned `false` for two different situations — "armed,
nothing due now" (success terminal state) and "arming failed" — and the screen treated `false` as
success:

```kotlin
if (!activated) { isConfirmedArmed = true; onDone(null) }   // failure === armed banner
```

On an enqueue failure the user therefore saw the error text *and* the armed banner, with the button
disabled (`enabled = !isArming && !isConfirmedArmed`), i.e. no way to retry — while the spec says
arming is reported as failed. `onDone(null)` also kept the user on the same screen, so the error text
was the only signal that anything was wrong.

Correction: arming returns a typed outcome (`ScheduleArmOutcome.ArmedIdle` / `LiveWindow(remaining)` /
`Failed`) and the screen switches on it. `Failed` leaves `isConfirmedArmed` false, so the banner does
not appear and the button re-enables for a retry; `ArmedIdle` is the only terminal success; the
`liveRemainingMillis` state flow is deleted because the outcome now carries the value (one source of
truth instead of two). This keeps the earlier `assertFalse(activated)`-style assertions meaningful by
making "failed" an explicit value rather than an overloaded boolean.

## R3 — F4: an unguarded storage read crashed instead of failing (Medium) — applied

`scheduleRepository.getWindowsSync()` was the one arming step with no `runCatching`, so a storage
failure escaped the `suspend` function into `rememberCoroutineScope().launch`, where an uncaught
exception is not a failed arming but a crash — the opposite of Validation point 4 (boundary/failure
handling). The existing instrumented test `ScheduleConfirmArmingTest.doneReEnablesAfterThrowingArm`
throws from exactly that call, so it could not have passed against the pre-review code.

Correction: arming is now total — every step is guarded and mapped to an outcome, and `Failed` is
reported with an error message. The instrumented test's intent (Done stays enabled) is preserved and
now provable.

## R4 — F9: the ignore list would silently hide deliberate project files (Medium) — applied

The plan's first revision added `.opencode/`, `.gravitas/`, `.verification/`, `.reticle.json`, and
`skills-lock.json` to `.gitignore`. Checking each against the repo:

- `.opencode/` **contains tracked files** (`commands/opsx-*.md`, `opencode.json`,
  `skills/**/SKILL.md`), and `.opencode/gravitas-load-check.mjs` is cited as gate evidence in the
  tracked `GATES.md`. Ignoring the directory stops new shared config from ever being committed.
- `.verification/*-LEDGER.md` is cited by that same tracked `GATES.md` as gate evidence.
- `.reticle.json` carries the Reticle `projectId` (`backend-0047a1c5`) — project config, not state.
- `skills-lock.json` is a lockfile for `npx skills add`, i.e. the same class of artifact as
  `package-lock.json`.

Correction: ignore only unambiguously machine-local state — `.opencode/node_modules/` and
`.gravitas/sessions/`. The rest stay visible so the user can commit or ignore them deliberately; that
decision is listed under Remaining Gaps rather than made by a drive-by ignore rule. This is the
"don't delete/disable to satisfy a rule" instruction in Part 3 applied to a build-config file.

## R5 — Plan/code mismatch: the cache rule in `tasks.md` contradicted the implemented one (Low) — applied

`tasks.md` 2.3 still said "refuse to cache when the query carries additional records (`ARCOUNT != 0`)",
which `design.md` decision 3 had already rejected (EDNS is on most real queries; refusing them would
have silently reduced a performance optimisation to near-zero). The code implements the fingerprint.
The task text is rewritten to match, because a stale task that contradicts the code is exactly the
kind of artifact that later reads as "implemented, verified".

## R6 — Undocumented behaviour change found in the diff: TUN idle poll raised 100 ms → 500 ms (Medium) — reverted

`git diff` on `VpnPacketLoop` contains `delay(100L)` → `delay(500L)` on the zero-read path, which no
finding, task, or spec covers and no measurement justifies. It is reachable in the steady state (the
loop's `length == 0` branch) and can add up to 400 ms to DNS handling latency, so it is a user-visible
latency/energy trade-off that does not belong in a correctness change. Committed history (the
battery-drain change) has 100L.

Correction: reverted to 100L. A poll-interval change belongs in its own change with a measurement, and
is listed under Remaining Gaps.

## R7 — F2/F7/F8: the planned tests do not exist, and one of them is not writable as planned (High, plan defect)

`PLAN.md` first revision claimed `VpnPacketLoopCacheTest` covers "repeated distinct lookups never
exceed the cap", "no sweeper is launched", and "oversized buffers are not recycled". Reading the file:
none of those tests exist. Worse, two of them are not practical through the loop's real path —
observing the cap requires resolving `MAX_CACHE_ENTRIES + 1` distinct domains through a socket-pair
integration loop, and the oversized-buffer path needs a DoH response larger than 32 KiB, which the
mock never produces.

Correction, so the plan's test claims are executable and honest:

- The bound itself is covered by a new `SimpleLruCacheTest` (eviction at the cap, recency on `get`,
  remove/clear). This is the container the VPN cache now uses, and it was previously untested in a
  codebase where four features depend on it — an Easy-Testability gap (Part 2.D) that this change now
  closes.
- The VPN wiring stays covered by the existing behavioural tests (hit, TXID/port rewrite, qtype
  isolation, fingerprint mismatch, blocked-domain bypass), which exercise the same `dnsCache` path.
- The buffer guard gets a real test by calling the private `releaseBuffer` reflectively on the
  instance built in `@Before` and asserting the pool only accepts pool-sized arrays. Reflection is
  already the repo's convention for this (`PackageFilterTest` pokes private fields).
- The "no sweeper" claim is dropped: with the sweeper deleted there is nothing to observe, and the
  plan must not claim a test that cannot fail.

## R8 — F1 test design: a thread-identity assertion would not be reliable (Medium, plan defect)

The first idea for proving "effects run on the publishing thread" was to record `Thread.currentThread()`
inside the mock. Under `UnconfinedTestDispatcher` (or `Main.immediate` with a test main dispatcher)
the block runs inline on the *caller's* thread, so the assertion would be measuring the test harness,
not the code. It would also be green for a wrong implementation.

Correction: the test asserts the property through dispatch instead — with a `StandardTestDispatcher`
as Main, the effects must be **absent** while Main has not run (after the check has demonstrably
finished, signalled from the check mock on a real dispatcher) and must be **applied** once Main runs.
That fails against the pre-fix code (which applied them on the `Dispatchers.Default` worker) and does
not depend on thread identity. A second, simpler test pins the positive path the change must keep: a
blocked verdict for the latest URL still presses BACK and launches the overlay — the spec has a
scenario for it and no test covered it.

## R9 — F3: residual behaviour left in place (Low, accepted)

The live-window shortcut skips activation when a session is already running. If that session ends
between arming and the user tapping lock-in, `hasActiveSession()` is false and a *new* session is
started with the arm-time remaining duration rather than a recomputed one. Recomputing would mean
re-reading the schedule at tap time and re-deriving the window, which is a bigger change than this
finding warrants. Accepted and recorded; the alternative (time-tolerance heuristic) was already
rejected for being fragile.

## R10 — F3: the `live=true` typed read has no navigation-level test (Low, accepted+recorded)

`SessionConfirmationViewModelTest` constructs `SavedStateHandle` directly, so it pins the *keys*
(`live`, `durationMillis`), and `AppNavigation` declares `live` as `NavType.BoolType` with a default,
so a route without the query string still resolves. What is not covered by an executable test here is
Navigation-Compose actually mapping `?live=true` into a `Boolean` in the handle: that needs an
instrumented navigation test (no emulator in this environment). Stated as a residual in the
validation report instead of being presented as verified.

## R11 — F6: worst-case startup cost of the re-probe (Low, accepted)

Two probes means up to `2 × 8 s` before degrading. It is bounded, it only runs on the timeout path
(the path that previously produced a wrong answer), and the startup spec already requires every wait
to be bounded. Accepted; the alternative (a shorter single timeout) was rejected earlier because it
trades a false negative for a different one.

## R12 — Scope and regression check against the rules (no change)

Cross-checked the corrected plan against Part 2 and the 8 points:

- **Point 2 (structures/complexity):** `SimpleLruCache` (`@Synchronized LinkedHashMap`) is O(1)
  amortised for `get`/`put` with a hard entry bound; the fingerprint is a single O(query) pass, no new
  allocation, and it replaces a per-entry string key with the same shape. The removed sweeper removes
  a per-`runLoop` coroutine. No unbounded structure remains in the changed file.
- **Point 3 (resources):** no new I/O on main; the one new main-thread hop is the verdict step, which
  was already service-call-heavy and now also removes cross-thread effects. Existing `try/finally` /
  `use` discipline in the loop is untouched.
- **Point 6 (architecture):** UI→ViewModel→Repository→DAO boundaries hold; the new outcome type is a
  ViewModel-layer value returned to the screen, no repository or DAO knowledge leaks into Compose.
- **Point 7 (regressions):** no public signature changes outside internal VM/test call sites; the
  route change is backward compatible via a defaulted nav argument; no schema change.
- **Point 5 (simplicity/YAGNI):** the deleted `liveRemainingMillis` flow and the deleted sweeper are
  net removals; no new abstraction beyond one sealed outcome that replaces an overloaded boolean.
- **Point 1 (security):** the DNS cache is now bounded on attacker-influenced keys (the one genuine
  denial-of-service surface in the finding set), the URL guard no longer acts on a superseded verdict,
  and no credential/logging surface is touched.
