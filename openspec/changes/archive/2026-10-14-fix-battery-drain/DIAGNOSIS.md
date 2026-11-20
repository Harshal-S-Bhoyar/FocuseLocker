# Diagnosis notes (tasks 1.1 + 1.2)

## 1.1 Packet-loop storm throw sites (static mapping, device silent at capture time)

Device ee64c019 post force-stop: 30s `logcat -s SVC_VPN_PACKET SVC_WATCHDOG` returned zero lines.
Force-stop holds; START_STICKY does not resurrect after explicit force-stop. The earlier
storm (00:33, E-level with full coroutine stack traces repeating inside one second) came
from the live stack only.

Candidate throw sites in `app/src/main/java/com/focuslock/service/vpn/VpnPacketLoop.kt`,
ranked by cost per fire (throwable-carrying logs build a trace plus write twice):

1. Line 164 `Timber.tag(TAG).w(e, "Error parsing individual DNS packet — skipping")` —
   fires per malformed packet inside the packet-rate loop. Prime suspect.
2. Line 170 `Timber.tag(TAG).e(e, "Packet loop error")` — loop-level death; pairs with
   START_STICKY restart churn if the service keeps restarting into the same failure.
3. Line 245 `Timber.tag(TAG).e(e, "DoH EXC ...")` — fires per failed DoH forward; pairs
   with offline phone plus active VPN (every query throws after retry).
4. Lines 204/210/243 carry no throwable; cheap by comparison.

Live confirmation deferred to task 6.2 device loop (needs a live session to run the VPN).
Mitigations in this change dull all four paths regardless: batched file writes (2.1),
demoted packet logs (2.2), DNS payload cache removing most DoH traffic (3.x).

## 1.2 Watchdog polling with zero session rows

Read path: `PermissionWatchdogService.kt:125` calls
`SessionRepository.getActiveSessionSync()` (`data/repository/SessionRepository.kt:23`),
which returns `SessionDao.getActiveSessionSync()` (`data/local/dao/SessionDao.kt:19`):
`SELECT * FROM sessions WHERE status = ACTIVE LIMIT 1`, null when no row.

Stop conditions in the loop (`PermissionWatchdogService.kt:129-133`): null session or
expired session calls `stopSelf()` plus break. So steady polling with a truly empty
table should be impossible on a clean read. Two hypotheses for the observed 30s polling
with zero rows found at dump time:

- H1: a live non-expired ACTIVE row existed during the 00:33 polls and was removed
  (end/delete path) before the DB dump.
- H2: the sync query threw each tick (lock/contention), driving the outer catch plus
  30s retry path instead of the stop path.

Boot-time unconditional start is confirmed separately (`BootReceiver.kt:99`) and is
fixed by task 4.1 regardless of H1/H2. Runtime disambiguation needs a live session
and is deferred to task 6.2.
