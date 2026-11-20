# Design

## Context

Second review pass over the uncommitted work. Current state, verified against code:

- `BrowserEventHandler`: `latestUrl` and `checkJob` are plain `var`s written on the accessibility
  (main) thread and read inside a coroutine on `Dispatchers.Default`. The `url != latestUrl`
  guard, and the guard-to-effect gap, are not safe.
- `VpnPacketLoop`: `dnsCache` is a `ConcurrentHashMap` with a 60s lazy sweep and **no entry cap**;
  the keys come from DNS names the visited site chooses. The sweeper is launched per `runLoop()`.
- `ScheduleEditorViewModel.armScheduleAndCheckActivation`: `setScheduleEnabled` failure returns
  early, but `scheduleScheduleChecks` failure is only logged, so the flow can report an armed
  schedule whose periodic worker was never enqueued.
- `SessionConfirmationViewModel`: `if (sessionRepository.hasActiveSession()) { Success; return }`
  applies to every entry path, including the one-shot/Timer path, and discards `durationMillis`.
- `OnboardingViewModel`: `probePermissions()` degrades to all-false on an 8s binder timeout, which
  routes to the permission-repair flow on a merely slow device; and the two `runCatching` halves
  in `initialize()` cannot both be observed because `getOrThrow()` aborts on the first failure.
- `.gitignore` ignores sibling agent directories (`.jetro/`, `.codex/`, `.agents/`, `.planning/`,
  `.claude-flow/`) but not the ones actually present in the tree.

## Goals / Non-Goals

**Goals:** the verdict guard is observable from the thread that reads it and is re-checked before
each effect; the DNS cache is bounded in entries with a single owner; arming that cannot enqueue
its checks reports failure; the live-window shortcut is scoped to the schedule hand-off; a slow
binder does not present a false missing-permissions flow; misleading comments and indentation
corrected.

**Non-Goals:** no new screens; no change to WorkManager policies (periodic interval, constraints,
unique-work names); no change to keyword matching, `NormalizedMatcher`, or the block list; no
change to the `scheduled-sessions` evaluation maths (`ScheduleEngine`); no Hilt module
restructuring; no changes to the previously-landed `fix-review-findings` behaviour.

## Decisions

1. **Publish the guard, then apply the verdict on the publishing thread** (`BrowserEventHandler`).
   Make `latestUrl` `@Volatile` and keep `checkJob` confined to the main thread, then run the guard
   read *and* the effects inside `withContext(Dispatchers.Main.immediate)`. Rationale: `@Volatile`
   establishes the happens-before edge the current code lacks, and confining read-and-act to the
   thread that publishes the URL makes the pair a single serialized step — no suspension runs
   between them and main is single-threaded, so a newer URL cannot land between the guard and the
   effect.
   *Revised during plan review.* The earlier revision re-read the guard immediately before each side
   effect while staying on `Dispatchers.Default`. That was rejected: re-reading does not close the
   window, because the read and the effect are still on a different thread from the write, so a
   superseded verdict could still press BACK on the page the user just opened. Matching
   (`checkUrlForBlockedContentUseCase`) still runs off Main, so the cost is unchanged; each effect is
   a cheap main-thread call (`startScan` is not suspend and only launches into its own scope).
   Alternative (a Channel/actor pipeline) rejected: more machinery than the problem needs.

2. **Bound the DNS cache by entries, and delete the sweeper** (`VpnPacketLoop`). Reuse the existing
   `com.focuslock.domain.util.SimpleLruCache` (synchronized `LinkedHashMap`, bounded `maxSize`)
   instead of a `ConcurrentHashMap`, and drop the 60s sweeper coroutine. Rationale: `SimpleLruCache`
   already provides exactly the needed bound and is `@Synchronized`, so removal of the sweeper also
   removes the per-`runLoop` sweeper-accumulation concern; expired entries are already dropped
   lazily on read. Alternative (hand-rolled size check + `removeIf` on a `ConcurrentHashMap`)
   rejected: duplicates an existing utility and keeps the second moving part.

3. **Key the cache on a fingerprint of the query, not just the domain** (`VpnPacketLoop`). The key
   becomes `domain|qtype|<hash of the query's post-header bytes>`; the 12-byte DNS header is
   excluded because it holds the per-query transaction ID. Rationale: replaying a stored answer is
   only sound when the *whole* query after the header is identical — that covers the question
   casing (0x20) and the EDNS options together, without modelling either. Two queries differing in
   casing or EDNS therefore get different keys and can never receive each other's answer, while
   the common case (one app resolving the same name repeatedly) still hits.
   *Revised during plan review.* An earlier revision refused to cache any query with
   `ARCOUNT != 0`. That was rejected: EDNS is present on most real queries, so it would have
   silently reduced a performance optimisation to near-zero while presenting itself as a
   correctness fix. Alternative (key on `domain|qtype|ARCOUNT`) rejected: does not capture OPT
   contents or question casing, so it would still replay a non-matching reply.

4. **Scoped shortcut via an explicit route flag** (`SessionConfirmation`). The schedule hand-off
   builds `session_confirmation/{duration}?live=true`; the route declares `live` with default
   `false`, and the shortcut requires `liveWindow && hasActiveSession()`. Rationale: the two entry
   paths are otherwise indistinguishable (both are just a `Long`), and an explicit flag is honest
   and testable, where a time-tolerance heuristic would be fragile. Backward compatible: `live`
   defaults to `false`, so existing callers and routes keep their behaviour. Alternative (compare
   the active session's remaining time to the requested duration within a tolerance) rejected:
   magic tolerance, and it silently mis-scopes if the tolerance is wrong.

5. **Arming returns a typed outcome, and a failure is not the terminal success**
   (`ScheduleEditorViewModel`, `ScheduleConfirmScreen`). Arming returns `ScheduleArmOutcome`:
   `ArmedIdle` (armed, no window due now), `LiveWindow(remainingMillis)` (armed, window live now),
   `Failed` (nothing armed, `error` explains why). A `scheduleScheduleChecks()` failure sets the
   error state and returns `Failed` before `setOnboardingComplete()`, mirroring the existing
   `setScheduleEnabled` handling: without the periodic worker the schedule cannot fire, so it must
   not be reported as armed. A failure of `setOnboardingComplete()` only sets the error state and
   continues — the schedule *is* armed and its checks *are* enqueued, so the flag failure is
   surfaced without discarding working state. The saved-window read is guarded too, so a storage
   failure returns `Failed` instead of escaping the suspend function as a crash. The screen switches
   on the outcome: `Failed` leaves the confirm screen with its button enabled (the error text is
   already rendered above the windows), so the user can retry; `ArmedIdle` is the only terminal
   success. `liveRemainingMillis` state is deleted — the outcome carries the value, so there is one
   source of truth instead of two.
   *Revised during plan review.* The earlier revision kept the boolean and only added the error
   text. That was rejected: `false` meant both "armed, nothing due" and "failed", so the screen
   rendered the armed banner on a failure *and* disabled the button, leaving no way to retry an
   arming that never happened. The saved-window read was also unguarded, so a storage failure
   crashed the screen's coroutine rather than failing the arming.

6. **Re-probe once before degrading** (`OnboardingViewModel.probePermissions`). On
   `TimeoutCancellationException`, probe a second time; only if that also times out return the
   all-false status. Rationale: keeps the existing contract and the repair-flow fallback (no UI or
   signature change) while requiring 2× the timeout of genuine binder slowness to produce a false
   negative.

7. **Delete the unobservable result capturing** (`OnboardingViewModel.initialize`). Replace the two
   `runCatching`/`getOrThrow` pairs with plain `await()`s inside the existing `supervisorScope`.
   Rationale: `statusResult.getOrThrow()` throws before `permissionsResult.getOrThrow()` is ever
   reached, so the second capture cannot be observed and the comment overstates what it buys;
   `supervisorScope` is what actually prevents sibling cancellation, and it stays.

8. **`releaseBuffer` only recycles pool-sized buffers.** Guard on the pool's buffer size so an
   oversized array (produced when a response exceeds the pool size) is not returned to the pool.
   Rationale: the pool exists to avoid allocations; recycling foreign sizes defeats that and can
   hand out a larger block than intended.

9. **Housekeeping without behaviour change.** Correct the stale "widened (null)" comment and the
   `SessionWorkerScheduler:` log tags, fix the "pool capped at 10" comment, re-indent the DoH
   `else` block in `runLoop`, and ignore the machine-local agent state in `.gitignore`. The TUN
   idle-poll interval (`delay`) is deliberately left at its committed value: an unreviewed raise to
   500 ms was found in the diff, and an idle-poll change belongs in its own change with a
   measurement rather than inside a correctness fix.
   *Narrowed during plan review.* `.gitignore` ignores only `.opencode/node_modules/` and
   `.gravitas/sessions/`. Ignoring `.opencode/`, `.verification/`, `.reticle.json` and
   `skills-lock.json` was rejected: `.opencode/{commands,skills}` is tracked project config,
   `.opencode/gravitas-load-check.mjs` and `.verification/*-LEDGER.md` are cited as gate evidence by
   the tracked `GATES.md`, `.reticle.json` carries the Reticle project id, and `skills-lock.json` is
   a lockfile. Ignoring any of them would silently prevent a deliberate file from being committed.

## Risks / Trade-offs

- [Risk] Capping the DNS cache changes hit rates for pathological pages. → Mitigation: the cap is
  sized for normal browsing; a miss only costs a DoH round trip, and `serveCachedResponse` already
  treats a miss as "fall through to DoH".
- [Risk] Fingerprinting the query means EDNS-carrying and differently-cased queries for the same
  domain no longer share a cache entry, so hit rate drops versus a domain-only key. → Mitigation:
  that is the cost of a *sound* replay; identical queries (the repeated-resolution case the cache
  exists for) still hit, and a miss only costs one DoH round trip. Pinned by a test asserting a
  differently-cased query is not served the cached answer.
- [Risk] Adding `?live=true` to a navigation route could break existing route matching.
  → Mitigation: the parameter is declared with a default, so a route without it still resolves;
  `FirstTimeFlowTest`/`ScheduleNavTest` pin both shapes.
- [Risk] Making arming fail on a `scheduleScheduleChecks` error turns a previously silent state
  into a user-visible failure. → Mitigation: this is the intended correction (a schedule that
  cannot fire is not armed); covered by a test asserting onboarding stays incomplete.
- [Risk] Re-probing adds up to one extra timeout of startup latency in the worst case.
  → Mitigation: the re-probe only runs after a timeout, i.e. on the path that previously produced
  a wrong "permissions missing" answer.
