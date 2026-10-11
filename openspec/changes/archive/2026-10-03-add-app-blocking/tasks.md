# Tasks

## 1. Session data model

- [x] 1.1 Extend `SessionEntity/Session` with `selectedAppKeys` CSV, add Room AutoMigration 8->9 with backfill default `''`, verify `./gradlew :app:assembleDebug` + migration test passes
- [x] 1.2 Update `SessionRepository.startSession()` + `SessionDao` to persist app keys and expose `getBlockedAppsFlow()` / active blocked set, verify unit test for start/get/clear round-trip passes

## 2. App inventory domain

- [x] 2.1 Implement `GetLaunchableAppsUseCase` via launcher-intent query with exclusion (self, launcher via `resolveActivity(HOME)`, settings/system safelist), sorted + search filter, verify unit test for filtering/exclusion passes
- [x] 2.2 Add per-setup selection holder + `IsAppBlockedUseCase` backed by active session keys, verify unit test for toggle ON/OFF and empty-selection no-op passes

## 3. App Block UI + navigation

- [x] 3.1 Create `AppBlockScreen` (Scaffold + TopAppBar + LazyColumn Card+Row+Switch + search + loading/empty states, theme/dimens/strings reuse) mirroring `CategorySelectionScreen`, verify Compose preview renders and manual navigation shows list
- [x] 3.2 Create `AppBlockViewModel` (`@HiltViewModel`, `StateFlow<List<AppUi>>`, `onAppToggled`, off-main load with icon cache), verify ViewModel unit test for fresh-OFF, retain-across-nav, and toggle persistence passes
- [x] 3.3 Add `Screen.AppBlockSelection` route and splice chain `Category -> AppBlock -> Keyword`, preserve `popUpTo` + back behavior, verify nav integration test / manual flow Category->AppBlock->Keyword->Timer passes

## 4. Hard-block enforcement

- [x] 4.1 Extend `FocusAccessibilityService` with `blockedAppPackages` flow (`flatMapLatest` on active session), package filter + `TYPE_WINDOW_STATE_CHANGED` branch doing `BACK` + `launchOverlayService(pkg, APP_BLOCKED)` before browser check, add `BlockReason.APP_BLOCKED`, verify unit/instrumented test for blocked-intercept and unblocked-allow passes
- [x] 4.2 Reuse `BlockingOverlayService` for app blocks (label + remaining time, sanitized), no VPN change, verify manual test: launch blocked Instagram/YouTube shows overlay <1s, unblocked app unaffected, no-session allows all

## 5. Lifecycle, permissions, manifest

- [x] 5.1 Extend `SessionWorkerScheduler` / health workers (`SessionTimerWorker`, `AccessibilityHealthWorker`, `PermissionWatchdogService`) to assert enforcement alive and clear on `stopRuntime()`, verify `SessionExpiryWorker` releases block within 2s on expiry in test
- [x] 5.2 Add `<queries>` launcher-intent block to `AndroidManifest.xml` (avoid `QUERY_ALL_PACKAGES`), extend `PermissionChecker`/setup only if new permission required, verify `assembleDebug` + permission flow manual check passes

## 6. Verification

- [x] 6.1 Add/extend unit tests (UseCases, ViewModel, Session keys, Accessibility branch logic) and verify `./gradlew :app:testDebugUnitTest` green
- [x] 6.2 Physical-device UAT: fresh session picks (all OFF), toggle 2 apps, start session, launch blocked (overlay + BACK), launch allowed (no interference), end session (release <2s), next setup fresh OFF, verify checklist signed off
