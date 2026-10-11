# Design

## Context

See proposal.md Why. Current enforcement: `FocusAccessibilityService` monitors 10 browser URL bars + Settings guard, checks `CheckUrlForBlockedContentUseCase`, on `Blocked` does `GLOBAL_ACTION_BACK` + `BlockingOverlayService`. `FocusVpnService` does DNS-level domain blocking. `SessionRepository/SessionRuntimeController` own session lifecycle; `SessionWorkerScheduler` schedules `SessionTimerWorker` (15min periodic) + `SessionExpiryWorker` (exact). UI setup chain: `Category -> Keyword -> Timer -> Confirmation` with `Scaffold + TopAppBar + Card+Row+Switch + PrimaryButton` pattern. Room DB v8, `SessionEntity.selectedCategoryKeys` CSV. No app-package blocking exists; non-browser packages early-return.

Constraints from exploration (graphify god nodes `BlockingRepository`, `FocusAccessibilityService`; agent-memory: physical device required, battery optimization #1 killer; code-review-graph: nav + common-ui patterns):

- No DeviceOwner, so no true kill; must reuse BACK + overlay.
- WorkManager 15min minimum unsuitable for real-time launch detection.
- Avoid `QUERY_ALL_PACKAGES` Play restriction; use launcher-intent query.
- Must not block self/launcher/settings.

## Goals / Non-Goals

**Goals:**
- Per-session (fresh each setup) hard block of user-launchable apps, placed after Category.
- Blend UI with existing setup style; offline inventory with cached icons.
- Reuse overlay + session lifecycle; minimal new permissions.

**Non-Goals:**
- Global persistent block list, per-app time limits, usage stats/analytics.
- VPN-level app blocking (out of scope, DNS only).
- Root/DeviceOwner kill, uninstall prevention beyond existing DeviceAdmin.
- Backend changes (pure on-device).

## Decisions

### 1. Enforcement primary: extend AccessibilityService, not new Worker for detection

- **Decision:** Add `blockedAppPackages: AtomicReference<Set<String>>` observed from `Session.selectedAppKeys` via `BlockingRepository.getBlockedAppsFlow()` (parallel to `getDomainsByCategoriesFlow`), using `flatMapLatest` on `getActiveSession()`. New branch in `onAccessibilityEvent()` before browser check: if `eventType == TYPE_WINDOW_STATE_CHANGED && blockedApps.contains(packageName)` -> `BACK` + `launchOverlayService(packageName, APP_BLOCKED)`. Include blocked packages in `serviceInfo.packageNames` filter. `BlockReason` gains `APP_BLOCKED`.
- **Alternatives:** ForegroundService + `UsageStatsManager` polling (1-2s lag, needs `PACKAGE_USAGE_STATS` + settings intent, battery + OEM-kill cost) — keep as fallback only if Accessibility disabled; WorkManager periodic — rejected for detection (15min floor), but new `AppLaunchMonitor` logic lives as scheduler/watchdog extension (ensure service alive, like `AccessibilityHealthWorker`).
- **Rationale:** Meets <1s hard-block spec, reuses tested BACK+overlay path, no new runtime permission, battery-cheap.

### 2. Session data: extend Session with selectedAppKeys CSV

- **Decision:** Add `selectedAppKeys: String` (CSV package names) to `SessionEntity/Session`, backfill empty via `AutoMigration 8->9` (like `selectedCategoryKeys` 7->8). `startSession(expiresAt, categoryKeys, appKeys)` extended; `SessionRepository.getActiveSession()` flows it; Accessibility + future VPN ignore it. No new table; no global list.
- **Alternatives:** New `blocked_apps` table with FK — rejected: per-session fresh requirement makes global table overhead + carryover risk; standalone prefs — rejected: loses transactional tie to session end.
- **Rationale:** Mirrors proven category pattern, atomic release on session end, minimal DAO churn.

### 3. Inventory: launcher-intent query, exclude-list, off-main-thread with cache

- **Decision:** New `GetLaunchableAppsUseCase` using `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` + `<queries>` manifest block (no `QUERY_ALL_PACKAGES`). Filter out self (`applicationId`), resolved launcher (`resolveActivity(MAIN+HOME)`), and `Settings/SYSTEMUI/Phone` safelist. Sort A-Z, search filter in VM. Icons via Coil + LRU, loaded off-main.
- **Alternatives:** `getInstalledApplications() + QUERY_ALL_PACKAGES` — rejected (Play review risk, includes non-launchable noise). `PACKAGE_USAGE_STATS` inventory — unnecessary.
- **Rationale:** Satisfies "only user-launchable" + self-lockout exclusion + Play-safe.

### 4. UI + nav: mirror CategorySelection after Category

- **Decision:** `ui/setup/AppBlockScreen.kt + AppBlockViewModel.kt`: `Scaffold(topBar, bottomBar=PrimaryButton Next)` + `LazyColumn` of `Card+Row+Switch` (copy `CategoryItem`), search `OutlinedTextField`, counter + empty/loading states. VM `@HiltViewModel`, `StateFlow<List<AppUi>>`, `onAppToggled(pkg, Boolean)` in `viewModelScope`. Nav: `Screen.AppBlockSelection("app_block_selection")`, chain `Category -> AppBlock -> Keyword -> Timer`, `popUpTo Category inclusive` unchanged on confirm.
- **Alternatives:** Standalone settings screen outside setup — rejected per user: must be after Category, per-session.
- **Rationale:** Style blend, minimal nav diff, testable VM like `CategorySelectionViewModel`.

### 5. Overlay reuse, no VPN change

- **Decision:** `BlockingOverlayService.showOverlay(matchedTerm=appLabel/package, blockReason=APP_BLOCKED, timeRemaining)` reused as-is (sanitized, `take(256)`). `FocusVpnService` untouched.
- **Rationale:** Hard-block spec satisfied without new overlay; keeps change surface small.

## Risks / Trade-offs

- [Risk] Accessibility event latency on heavy OEM skins -> Mitigation: throttle 500ms already exists; test on POCO/MIUI target; fallback UsageStats poll if missed.
- [Risk] OEM battery kill stops enforcement -> Mitigation: reuse `SessionTimerWorker` + `AccessibilityHealthWorker` + `PermissionWatchdogService` checks; battery-opt exemption already in onboarding.
- [Risk] Launcher/settings mis-exclusion bricks UX -> Mitigation: hard-coded exclusion + `resolveActivity` dynamic launcher detect; instrumented test for self-launch.
- [Risk] Icon list jank on 300+ apps -> Mitigation: paging/cached icons, `collectAsStateWithLifecycle`, off-main query.
- [Risk] User disables Accessibility mid-session to bypass -> Mitigation: existing `PermissionEnforcementService` repair flow triggers overlay + navigate to repair; extend `PermissionChecker` if new perm added.
- [Risk] Stale package (uninstalled after selection) -> Mitigation: filter at enforcement (`contains` check tolerates missing), prune on load.

## Migration Plan

- Room AutoMigration 8->9 with `onPostMigrate` default `''` for `selectedAppKeys`; rollback: downgrade clears column (session-scoped, safe).
- Deploy: feature behind setup chain; empty selection = no-op, so old sessions unaffected.
- Rollback: revert nav splice; enforcement branch no-ops on empty set; DB column ignored.
- Verification: unit (VM toggle, UseCase filter/exclude, Session keys), integration (Accessibility branch + overlay intent, nav chain), manual on physical device (block Instagram/YouTube, allow dialer/launcher, expiry releases).

## Open Questions

- Exact exclusion safelist (which Settings/Phone packages per OEM)? Deferrable: start with self + launcher + `com.android.settings`, expand via field reports without spec change.
- Search UX: prefix vs substring, diacritics? Deferrable: default substring, no spec impact.
