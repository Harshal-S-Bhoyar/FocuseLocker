# Tasks

## 1. Diagnose live anomalies first

- [x] 1.1 Capture 30s of `adb logcat -s SVC_VPN_PACKET` on the test device and record the first exception message plus throw site, verified by a note naming the exact file and line that throws in the packet loop
- [x] 1.2 Trace `SessionRepository.getActiveSessionSync` read path and record why the watchdog polls with zero session rows, verified by a note naming the source of the non-null read or the missed stop path

## 2. Logging batch plus quiet packet path

- [x] 2.1 Replace per-line file open-write-close in `FileLoggingTree` with queue plus 5s scheduled flush, keeping logcat immediate and crash flush intact, verified by unit test asserting batched writes plus flush-on-demand and by `testDebugUnitTest` green
- [x] 2.2 Demote per-packet `DoH fwd` and `DoH OK` logs to verbose, verified by logcat showing no per-query lines at debug level during browsing

## 3. DNS payload cache with TTL bounds

- [x] 3.1 Add DNS answer-section TTL parser returning minimum TTL, verified by unit tests on crafted responses including multi-answer, CNAME chain, and malformed input falling back to uncached forwarding
- [x] 3.2 Add payload cache in `VpnPacketLoop` keyed by domain plus qtype with 30s min and 300s max clamp, rebuilding responses per hit via the existing response builder, verified by unit tests for hit-miss-expiry, TXID correctness per query, and qtype isolation
- [x] 3.3 Wire cache lookup before `forwardDnsViaDoH` and store on DoH success, keeping blocklisted domains on the local NXDOMAIN path, verified by integration test showing repeated lookups emit one HTTPS request and block verdicts stay identical

## 4. Idle silence plus scoped events

- [x] 4.1 Gate watchdog start in `BootReceiver` on persisted active session while keeping schedule rearm unconditional, verified by unit test for both boot states and by device reboot showing no watchdog without a session
- [x] 4.2 Replace null package filter with explicit watched set (browsers plus settings plus blocked apps plus own package) under enforcement, verified by unit test asserting set contents and by device test showing blocked-app intercept plus browser URL block still firing

## 5. Worker alignment plus idle backoff

- [x] 5.1 Add 5-minute flex windows to the three 15-minute periodic workers in `WorkScheduler`, verified by unit test asserting request parameters and unchanged check frequency
- [x] 5.2 Raise VPN idle-loop backoff from 100ms to 500ms, verified by code inspection plus `testDebugUnitTest` green

## 6. Validate and land

- [x] 6.1 Run full `testDebugUnitTest` plus lint plus `assembleDebug` after the last edit, verified by pasted BUILD SUCCESSFUL output with zero failures
- [ ] 6.2 Run device loop confirming cooler steady state: watchdog silent with no session, single DoH request per domain per TTL, and block paths firing, verified by logcat excerpts plus battery stats note (also covers the device halves of 2.2, 4.1, 4.2)
