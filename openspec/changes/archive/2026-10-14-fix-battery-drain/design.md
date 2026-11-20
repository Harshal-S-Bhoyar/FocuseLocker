# Design: fix-battery-drain

## Context

See proposal.md Why. Current state verified by code inspection plus a live device: `VpnPacketLoop` forwards every non-blocked DNS query over HTTPS with `cache(null)`; `FileLoggingTree` opens, appends, and closes a file per log line; `BootReceiver` starts the watchdog unconditionally; the accessibility filter widens to all packages under enforcement; three 15-minute workers run unaligned; logcat shows a `SVC_VPN_PACKET` exception storm; the watchdog polls with zero session rows present. No WakeLocks or exact alarms exist, so the drain is CPU plus radio plus disk.

## Goals / Non-Goals

- Goals: cut radio, CPU, and disk cost of enforcement with byte-identical verdicts; silence all idle-state work; diagnose the two live anomalies (packet-loop storm, session-less watchdog polling).
- Non-Goals: no change to block, fire, unlock, schedule, or onboarding semantics; no VPN protocol change; no new permissions; no server-side changes.

## Decisions

- **Cache DNS payloads, not TUN packets.** Caching full response packets breaks on two axes: the DNS transaction ID differs per query and the swapped ports belong to the original socket. So the cache stores response payload bytes keyed by domain plus qtype, and each hit runs the existing `buildTunDnsResponse` path (array copies plus checksum, no network). Rejected alternative: full-packet cache (fastest, incorrect). Rejected alternative: OkHttp HTTP cache (wrong layer; TUN responses are synthesized per query).
- **Small TTL parser over DNS answers.** No answer-section parser exists today. Parse RRs on DoH success, take the minimum TTL, clamp to 30s..300s. Cap is conservative: more queries than honoring long TTLs, bounded worst case. Rejected alternative: fixed TTL for all entries (simplest, over-queries short-lived records and under-refreshes long ones).
- **Log batching with queue plus scheduled flush.** `ConcurrentLinkedQueue` drained every 5s in one open-write-close. Logcat stays immediate. Existing `flush()` plus the `GlobalExceptionHandler` call already covers crash safety. Rejected alternative: larger per-line buffering without scheduler (unbounded loss window). Follow-up: demote per-packet `DoH fwd` / `DoH OK` logs to verbose, else batching only delays identical byte volume.
- **Boot guard on the existing session flag.** Gate only the watchdog start on `isActiveSession`; schedule rearm stays unconditional so future windows survive reboot. Rejected alternative: gating all boot work (would lose schedule rearm).
- **Explicit watched-package set instead of null filter.** Union of browsers, settings, blocked set, own package. Null-filter behavior is preserved exactly because the handler only consumes those classes today. Rejected alternative: keeping null (free, keeps the event firehose).
- **Worker flex windows, not worker merge.** Add a 5-minute flex to each 15-minute periodic request so the scheduler batches wakeups. Merging into one worker was rejected: one crash would kill all three checks and complicate retry semantics.
- **Idle backoff 100ms to 500ms.** Near no-op (TUN reads block), kept as trivial hygiene.

## Risks / Trade-offs

- [Risk] TTL parser misreads exotic RR layouts and caches a wrong TTL. Mitigation: clamp bounds plus cache key includes qtype; failures fall back to uncached forwarding, never to wrong verdicts.
- [Risk] Stale DNS verdict inside TTL serves a recently unblocked domain as blocked (max 300s). Mitigation: bound is short and documented; blocklist hits bypass the cache entirely.
- [Risk] 5s log buffer loses the last seconds on hard kill (not crash). Mitigation: accepted for a diagnostics log; logcat retains immediacy.
- [Risk] Watchdog boot guard misses a session that starts between boot and first check. Mitigation: session start path launches the watchdog directly, independent of boot.
- [Risk] Exception-storm root cause unknown until logcat capture. Mitigation: diagnosis is a tracked task that runs before the VPN changes land, so the cache is sized against real failure modes.

## Migration Plan

- Ship in phase order: logging, DNS cache, boot guard plus filter, workers plus backoff. Each phase is independently releasable and rollback is a plain revert with no data migration. No spec sync needed until archive; `battery-efficiency` spec syncs to main specs at archive time.

## Open Questions

- None that change specs, approach, or tasks. The storm throw site and the session-less watchdog read path are tracked as diagnosis tasks with concrete logcat and repository-inspection steps.
