# Design

## Context

See proposal.md Why. Launch init is one serial coroutine in `OnboardingViewModel`, the seeded flag updates zero rows, and loading paints Lottie-or-nothing. Design resolves ordering, atomicity, and fallback rendering before coding.

## Goals / Non-Goals

**Goals:**
- Launch-to-content under two seconds on warm start, bounded worst case on cold start.
- Seed side effects exactly once across kills and concurrency.

**Non-Goals:**
- No change to blocking verdicts, schedule firing, unlock, or enforcement behavior.
- No new permissions or background work.

## Decisions

- Flag upsert plus single-flight seed mutex over bare `UPDATE`: the mutex serializes concurrent inits and the upsert makes the flag survive regardless of row pre-existence. Alternative of checking before inserting keeps the race.
- `async` sibling branches with per-branch `withTimeout` over serial chain: cleanup, settings, seed gate, and sync scheduling share no data, so they run together and the slowest branch, not the sum, sets the floor.
- Permission probe moves after first content: navigation no longer waits on six binder calls, repair flow still gates protected destinations.
- Text-first loader over Lottie-first: static text plus determinate-capable progress paints on frame one, Lottie becomes decoration layered on top.
- Navigate-then-consume over consume-then-navigate: delivery survives recomposition, idempotent destinations make redelivery harmless.

## Risks / Trade-offs

- [Risk] Timeout too tight on very slow devices aborts a healthy seed → Mitigation: generous cold-start budget plus resume-from-clean retry, measured on the slowest test device.
- [Risk] Parallel branches contend on one SQLite writer → Mitigation: single-flight seed plus batched inserts already chunk at 1000 rows, writers stay ordered.
