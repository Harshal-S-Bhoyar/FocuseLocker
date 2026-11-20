# Verification ledger — fix-battery-drain
## Spec
- AC1: Repeated DNS lookup inside TTL emits zero network traffic with identical verdict (spec: DNS cache bounds)
- AC2: Boot with no session starts no enforcement services; schedule checks still rearm (spec: idle silence)
- AC3: File logs batch to disk; logcat immediate; crash flushes (spec: log batching)
- AC4: Enforcement events scoped to watched set; all block paths fire (spec: scoped events)
- AC5: Workers batch wakeups at unchanged frequency (spec: shared wakeups)
- AC6 (regression): Block, fire, unlock, schedule, onboarding semantics identical
## Critical paths
- P1 [happy] cache hit serves current TXID plus ports, zero DoH (AC1)
- P2 [boundary] expired entry evicted then revalidates (AC1)
- P3 [state] blocklisted domain bypasses cache plus network (AC1, AC6)
- P4 [error] malformed DNS falls back to uncached forwarding (AC1)
- P5 [state] many logs plus one flush land in one file; empty flush writes nothing (AC3)
- P6 [state] boot null session skips watchdog, rearms checks (AC2)
- P7 [state] filter sets across 4 session/guard combos, never null (AC4)
- P8 [boundary] flex 5min on 15min interval, frequency unchanged (AC5)
## Commands
- test: `.\gradlew.bat :app:testDebugUnitTest`
- lint: `.\gradlew.bat :app:lintDebug`
- build: `.\gradlew.bat :app:assembleDebug`
- baseline (before change): full suite state predates change; targeted pre-change run not taken
## Adversarial cases
| id | path | category | attacks | expected (AC) | test id | result |
| A1 | P1 | state | same domain new TXID plus port | current TXID/port, 1 DoH total | VpnPacketLoopCacheTest second identical lookup | PASS |
| A2 | P1 | boundary | TTL 0 passes to clamp layer | clamped, not zero-serve | DnsTtlParserTest zero ttl | PASS |
| A3 | P2 | boundary | truncated response | null, uncached forward | DnsTtlParserTest truncated | PASS |
| A4 | P3 | security | blocked domain | NXDOMAIN, 0 DoH | VpnPacketLoopCacheTest blocked bypass | PASS |
| A5 | P1 | api-misuse | A vs AAAA same domain | 2 DoH, isolated | VpnPacketLoopCacheTest qtype isolated | PASS |
| A6 | P5 | state | 25 logs one flush | 1 file 25 lines | FileLoggingTreeBatchTest many logs | PASS |
| A7 | P5 | boundary | flush empty buffer | no files | FileLoggingTreeBatchTest empty flush | PASS |
| A8 | P6 | state | boot null session | no watchdog, checks rearmed | BootGuardTest boot without session | PASS |
| A9 | P7 | state | 4 filter combos | explicit sets, never null | PackageFilterTest all four | PASS |
| A10 | P8 | regression | flex params | 15min interval 5min flex | WorkerFlexTest both | PASS |
| A11 | P4 | input | CNAME chain, multi-answer | min TTL | DnsTtlParserTest cname plus multi | PASS |
| A12 | P4 | property | offset windows | same parse | DnsTtlParserTest offset respected | PASS |
## Traces
| path | input | branches | state | actual | expected | match |
| P1 | cached.com A, entry live | serveCached→build+TXID patch+checksum | 1 DoH total | current TXID/port | AC1 | ✅ (code trace) |
| P2 | entry expired | remove+return false→forward | revalidate | fresh DoH | AC1 | ✅ (code trace) |
| P3 | blocked.com | isDomainBlocked first→NXDOMAIN | 0 DoH | NXDOMAIN | AC1+AC6 | ✅ (code trace) |
| P4 | malformed | parser null→skip cache | forward | uncached | AC1 | ✅ (code trace) |
| P5 | 25 logs→flush | drain→single write | 1 file | 25 lines | AC3 | ✅ (code trace) |
| P6 | boot, null session | isActiveSession false→skip | checks rearmed | no watchdog | AC2 | ✅ (code trace) |
| P7 | 4 combos | computeWatchedPackages | explicit sets | no null | AC4 | ✅ (code trace) |
| P8 | builders | 15+5 flex | same frequency | batched window | AC5 | ✅ (code trace) |
## Runs (latest after last edit)
| command | exit | passed/failed | key lines |
| `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` | 0 | 529 passed / 0 failed | `BUILD SUCCESSFUL in 9m 37s`, `62 actionable tasks` |
## Repair log
| R# | failure | root cause (line + why) | fix |
| R1 | WorkerFlexTest 2/2 AbstractMethodError | static mock used mockkStatic only; companion dispatch needs mockkObject (existing suite pattern) | mirrored existing setup plus imports |
| R2 | VpnPacketLoopCacheTest hit-log miss (flaky 2/3 runs) | test assert raced loop thread: socket read proves write, not the later log line | bounded 3s poll for log line; reverted prod debug marker |
| R3 | WorkerFlexTest slot capture on exactly=2 verify | mockk slot captures once | capture to list, assert on last |
## Verdict: NOT VERIFIED — G7 device loop pending (device ee64c019 disconnected)
