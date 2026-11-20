# Design

## Context

See proposal.md Why. Init is already 1.9s; the remaining launch cost is window draw plus first-frame GPU plus the 1500ms recheck when accessibility is off. In-app cost centers are the per-second full-tree countdown, unkeyed-by-content lists, Main-thread matching, and debug-only measurement noise.

## Goals / Non-Goals

**Goals:**
- Worst-case launch-to-content under 4s on the reference device.
- Countdown ticks plus list updates plus match bursts hold 60fps budgets.

**Non-Goals:**
- No verdict, schedule, unlock, or enforcement behavior changes.
- No new permissions or background work.

## Decisions

- Hoist countdown state into a leaf timer composable over screen-wide collection: one subscriber repaints, the tree skips. Alternative of derivedStateOf at top level still repaints parents.
- Defer Lottie composition plus gradient blend past first paint over removing them: keeps brand, pays GPU after content is visible.
- Single-flight shared matching coroutine with cached normalized set over per-event rebuild: verdicts identical, allocation per burst near zero.
- Release-build measurement with baseline profiles over debug tuning: debug JIT noise would misdirect every optimization.
- Keep the 1500ms accessibility recheck but start content paint before it resolves: perceived start drops even when the probe still runs.

## Risks / Trade-offs

- [Risk] Scoped timer misses a state transition shown beside it → Mitigation: state changes still flow through existing event channels, only the tick is scoped.
- [Risk] Cached domain set goes stale after list sync → Mitigation: version the cache on repository revision bump, already emitted by sync paths.
