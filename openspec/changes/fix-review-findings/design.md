# Design

## Context

See proposal.md (Why) and specs deltas for requirements. Current state, verified against code:
- `seedOnceWithinBudget` wraps only the seed call in `withTimeout`; the `SEED_MUTEX.withLock` wait is unbounded.
- `OnboardingViewModel.initialize()` launches fire-and-forget; `retry()` can start a second concurrent sequence.
- `BrowserEventHandler.handlerScope` is an independent scope never cancelled; `FocusAccessibilityService.onDestroy` cancels only `serviceScope`.
- `armScheduleAndCheckActivation` swallows `setScheduleEnabled` failure and still runs `setOnboardingComplete()`.
- `ScheduleRepository` is `@Inject @Singleton` — Hilt always satisfies it; the nullable fallback is dead.

## Goals / Non-Goals

**Goals:** Bound every wait with a timeout; single-flight init and URL checks; arming failures stop the flow loudly; contact state never read before load.

**Non-Goals:** No new screens, no navigation changes, no WorkManager policy changes, no AhoCorasick/keyword-matcher changes, no Hilt module restructuring.

## Decisions

1. **Timeout outside the seed mutex** (`withTimeout(SEED_TIMEOUT_MS) { SEED_MUTEX.withLock { ... } }`), fast-path `isDomainSeeded()` check kept outside. Rationale: bounds queue wait; the flag read is a cheap local read that must not consume budget. Alternative (separate lock-wait timeout) rejected: two clocks, same guarantee, more code.
2. **Retained init Job, cancel-before-launch** in `OnboardingViewModel`. Rationale: `viewModelScope` cancels on clear anyway; a field-level Job only adds retry-safety. Alternative (Mutex around init) rejected: serializes but lets stale runs finish and write last.
3. **Cancel-previous + URL-identity discard** in `BrowserEventHandler`: keep a `checkJob: Job?`, cancel before each launch, capture the URL, compare after the suspend call, drop on mismatch. Rationale: cancellation alone has a race window (a check past its last suspension point still delivers); identity check closes it. Alternative (actor/Channel pipeline) rejected: heavier than the problem.
4. **`dispose()` + `onDestroy` wiring**: `fun dispose() = handlerScope.cancel()`, called next to `serviceScope.cancel()`. Rationale: matches the existing teardown shape; hard process death needs no handling (process death kills coroutines).
5. **Arming failure propagation**: `setScheduleEnabled` failure returns early (or throws to the caller) before `scheduleScheduleChecks`/`setOnboardingComplete`. Rationale: onboarding-complete is a point of no return for setup; partial arming must not reach it. Chosen over per-step booleans: fewer states.
6. **`isContactLoaded` flag (Option B)**: keep `Boolean` contact state, add load flag, Done disabled until loaded. Rationale: `onDone(Boolean)` feeds `AppNavigation` which expects non-null; no ripple. Alternative (`Boolean?`) rejected: wider diff for the same guarantee.
7. **Injectable clock for arming**: `now: LocalDateTime = LocalDateTime.now()` parameter on `armScheduleAndCheckActivation` (default keeps production call sites unchanged). Rationale: tests pass fixed time; zero production diff. Alternative (Clock interface) rejected: heavier DI for one call site.
8. **Delete dead `as? Set` cast only**; keep both `www.` strips. Rationale: cast provably never succeeds (param is `List`); removal is behavior-neutral.

## Risks / Trade-offs

- [Risk] Cancelling URL checks under burst events could starve slow checks forever → Mitigation: identity-discard (not pure cancel) means the latest URL always gets a verdict; debounce already bounds event rate.
- [Risk] `withTimeout` around the mutex cancels the *holder's* lock acquisition — the holder itself keeps seeding; a second waiter timing out while holder finishes is fine (flag check on retry). → Mitigation: early-return recheck after lock acquisition already exists.
- [Risk] Init Job cancellation mid-`initializeAppUseCase` leaves partial seed → Mitigation: seed flag is only set after completion, so retry reseeds (existing spec scenario covers this).
