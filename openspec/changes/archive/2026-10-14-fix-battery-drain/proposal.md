# Proposal

## Why

The app heats the phone and drains battery fast during and after focus sessions. Live-device evidence shows a VPN packet-loop exception storm, one HTTPS request per DNS lookup with no caching, per-line disk logging, enforcement services running with no session, and unaligned periodic workers. Fixing these removes the drain without changing any blocking behavior.

## What Changes

- Cache DNS-over-HTTPS verdicts by domain plus query type within server TTL bounds (30s min, 300s max); serve cache hits with zero network by rebuilding the response per query.
- Batch file logging to periodic flushed writes; keep logcat immediate and crash flush intact.
- Start the permission watchdog on boot only when a persisted active session exists.
- Replace the widened accessibility package filter with an explicit watched-package set during enforcement.
- Add flex windows to periodic workers so the scheduler batches wakeups.
- Back off the VPN idle-loop poll interval.
- Diagnose the packet-loop exception storm root cause and the watchdog-running-without-session leak; fix both.

## Capabilities

### New Capabilities

- `battery-efficiency`: durable efficiency budgets for enforcement. Covers DNS verdict caching bounds, idle-state service silence, log I/O batching, worker wakeup alignment, and event-filter narrowing. Owns future battery and heat requirements as the enforcement stack evolves.

### Modified Capabilities

- None. No existing requirement text changes; all block, fire, unlock, and schedule semantics stay identical.

## Impact

- Affected code: `service/vpn/VpnPacketLoop.kt`, `service/vpn/FocusVpnService.kt`, `util/FileLoggingTree.kt`, `receiver/BootReceiver.kt`, `service/accessibility/FocusAccessibilityService.kt`, `data/worker/WorkScheduler.kt`, plus storm and zombie-leak diagnosis targets.
- No API, data model, navigation, or dependency changes. One small DNS answer-section TTL parser added.
- Tests: existing unit suites plus new tests for DNS cache hit/miss/TTL, log batching flush, boot guard routing, explicit filter contents, and storm/zombie regression.
