# Proposal

## Why

FocusLocker currently blocks distracting websites (via Accessibility + VPN DNS) but users can still open native distracting apps (Instagram, YouTube, games) during a focus session. This gap breaks the core promise that a session enforces discipline. App blocking closes the loop.

## What Changes

- New **App Block selection screen** in setup flow, placed immediately after Category selection, listing only user-launchable installed apps with per-app toggle (ON = block for this session).
- Per-session app selection: user picks fresh every session; selection stored with the session (e.g. `selectedAppKeys`), cleared on session end; no global persistent block list.
- Hard block enforcement during active session: when a blocked package comes to foreground, navigate back and show existing non-dismissible blocking overlay until session ends; no soft bypass.
- App inventory limited to user-launchable apps (launcher-intent query), excluding FocusLocker itself, launcher, and critical system settings to avoid self-lockout.
- Navigation updated: `Category -> AppBlock -> Keyword -> Timer -> Confirmation`; back/forward and `popUpTo` preserve existing behavior.

## Capabilities

### New Capabilities

- `app-blocking`: per-session selection of user-launchable apps to hard-block, enforcement via foreground-app detection and blocking overlay during active session.

### Modified Capabilities

None — no existing shipped specs to modify (specs inventory is empty). Behavior extends session setup and enforcement without changing URL/DNS blocking requirements.

## Impact

- UI: new `AppBlockScreen` + `AppBlockViewModel`, `AppNavigation` route, strings/dimens, theme reuse (Scaffold + Card + Switch pattern from CategorySelection).
- Domain: new use cases `GetLaunchableAppsUseCase`, `ToggleAppSelection` (in-memory per-setup or via Session keys), `IsAppBlockedUseCase`.
- Data/Session: extend `SessionEntity/Session` with `selectedAppKeys`; `SessionRepository.startSession` signature; `FocusDatabase` migration; `BlockingRepository` or new `AppBlockingRepository` flow for active blocked packages.
- Enforcement: `FocusAccessibilityService` new blocked-package branch (before browser check) + `serviceInfo.packageNames` filter; `BlockCheckResult.BlockReason.APP_BLOCKED`; `BlockingOverlayService` reuse (no UI change); `SessionWorkerScheduler` / health workers ensure monitor stays alive.
- Permissions/Config: launcher-intent inventory (avoids `QUERY_ALL_PACKAGES`); no `PACKAGE_USAGE_STATS` if Accessibility path chosen; `PermissionChecker` / watchdog extended only if new permission required; `AndroidManifest` queries block.
- Risks: Accessibility latency, OEM kills, icon-loading perf, Play policy if broad package query used.
