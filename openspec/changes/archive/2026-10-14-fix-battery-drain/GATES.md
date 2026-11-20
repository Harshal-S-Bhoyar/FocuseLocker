# Gates: fix-battery-drain (battery-efficiency)

Scope: Cut enforcement CPU, radio, and disk cost with byte-identical verdicts. No block, fire, unlock, schedule, or onboarding change.

- [x] G1: Live anomalies diagnosed (tasks 1.1+1.2)
  CHECK: none (device logcat + code trace, manual)
  EXPECT: note names packet-loop throw site (file:line) and watchdog non-null read source
  EVIDENCE: DIAGNOSIS.md — VpnPacketLoop.kt:164 prime suspect plus 170/245 ranked; 30s logcat silence post force-stop; watchdog H1/H2 with SessionDao.kt:19 read path; live confirm deferred to 6.2

- [x] G2: Logging batched plus packet path quiet (tasks 2.1+2.2)
  CHECK: .\gradlew.bat :app:testDebugUnitTest --tests "com.focuslock.util.FileLoggingTreeTest"
  EXPECT: BUILD SUCCESSFUL, new tests pass
  EVIDENCE: BUILD SUCCESSFUL; FileLoggingTreeBatchTest 2/2 green; 2.2 verified no-op (DoH fwd/OK already verbose VpnPacketLoop.kt:190,214); device half in G7

- [x] G3: DNS payload cache with TTL bounds (tasks 3.1+3.2+3.3)
  CHECK: .\gradlew.bat :app:testDebugUnitTest --tests "com.focuslock.service.vpn.*"
  EXPECT: BUILD SUCCESSFUL, hit-miss-expiry plus TXID plus qtype tests green
  EVIDENCE: DnsTtlParserTest 9/9, VpnPacketLoopCacheTest 3/3 green (hit serves current TXID/ports with 1 DoH; blocked bypasses; A/AAAA isolated); full suite 529/529 green

- [x] G4: Idle silence plus scoped events (tasks 4.1+4.2)
  CHECK: .\gradlew.bat :app:testDebugUnitTest --tests "com.focuslock.receiver.*" --tests "com.focuslock.service.accessibility.*"
  EXPECT: BUILD SUCCESSFUL, boot-guard plus filter tests green
  EVIDENCE: BootGuardTest, PackageFilterTest 4/4 green; existing BootReceiverTest unaffected (active-session cases); device halves in G7

- [x] G5: Worker alignment plus idle backoff (tasks 5.1+5.2)
  CHECK: .\gradlew.bat :app:testDebugUnitTest --tests "com.focuslock.data.worker.*"
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: WorkerFlexTest 2/2 green (15min interval + 5min flex); existing WorkSchedulerTest unaffected

- [x] G6: Full verification fresh after last edit (task 6.1)
  CHECK: .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  EXPECT: BUILD SUCCESSFUL, 0 failures
  EVIDENCE: BUILD SUCCESSFUL in 9m 37s; 529 tests 0 fail 0 error 0 skip; lintDebug clean; assembleDebug current

- [ ] G7: Device loop confirms cooler steady state (task 6.2)
  CHECK: none (logcat + battery stats, manual)
  EXPECT: watchdog silent without session, one DoH per domain per TTL, blocks firing
  EVIDENCE: pending
