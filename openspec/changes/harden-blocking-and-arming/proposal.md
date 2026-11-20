# Proposal

## Why

A second review pass over the uncommitted work (scheduled sessions, VPN/DNS blocking, startup)
found that several guards added in the first pass are correct in intent but unsound in execution:
a verdict guard that cannot see the value it guards, a cache with no upper bound on
attacker-influenced input, an "already started" shortcut that silently drops the user's chosen
duration, and arming that reports success when the background checks it depends on were never
scheduled. Each is small; together they leave flows that *look* correct and are not.

## What Changes

- **URL blocking (batch 1):** publish the URL-verdict guard across threads, and apply the verdict on
  the thread that publishes the URL (re-verification plus effects serialized with publication), so a
  superseded verdict can neither be applied nor race the effect that applies it.
- **VPN DNS cache (batch 2):** bound the cache by entry count (not just by elapsed time), keep a
  single sweeper, and refuse to cache responses whose reply would not match the query
  (query carrying additional records such as EDNS).
- **Schedule arming (batch 3):** a failure to enqueue the scheduled checks SHALL surface as an
  arming failure instead of being logged and ignored, matching how an enable failure is treated.
  Arming SHALL report a typed outcome rather than a boolean, so "armed, nothing due now" and
  "arming failed" are distinct: a failure must not render the armed confirmation, and it must leave
  the screen retryable. A failure to read the saved schedule SHALL fail the arming instead of
  escaping as a crash.
- **Session confirmation (batch 4):** the "a live window is already running" shortcut SHALL apply
  only to the schedule hand-off, so starting a one-shot session does not silently adopt an
  unrelated running session or discard the requested duration.
- **Startup probe (batch 5):** the permission probe SHALL re-probe once before degrading, so a
  slow binder does not present a false "permissions missing" repair flow, and the redundant
  result capturing in `initialize()` is removed.
- **Housekeeping (batch 6):** misleading comments and the un-indented DoH block are corrected;
  the buffer pool only recycles buffers of its own size; agent tooling state is ignored by git.

## Capabilities

### New Capabilities

None. Every change tightens an existing specified behaviour.

### Modified Capabilities

- `app-blocking`: verdict publication SHALL be safe to read from the checking thread; the DNS
  cache SHALL be bounded in entries; responses SHALL NOT be cached when the query's additional
  records make the cached reply non-matching.
- `scheduled-sessions`: arming SHALL fail loudly when scheduled checks cannot be enqueued; the
  live-window shortcut SHALL apply only to the schedule hand-off.
- `app-startup`: the permission probe SHALL tolerate a slow binder without reporting permissions
  as missing.

## Impact

- Files: `BrowserEventHandler`, `VpnPacketLoop`, `ScheduleEditorViewModel` (+ its outcome type),
  `ScheduleConfirmScreen`, `SessionConfirmationViewModel` (+ its route/call site),
  `OnboardingViewModel`, `.gitignore`, and the corresponding test classes.
- No public API changes. Two intentional guarded behaviour changes: arming a schedule that cannot
  enqueue its checks now reports failure, and the session-confirmation shortcut no longer absorbs
  a one-shot start. Both are called out in the design and covered by tests.
- Verification: full `testDebugUnitTest` green after the change, plus `lintDebug` and
  `assembleDebug`.
