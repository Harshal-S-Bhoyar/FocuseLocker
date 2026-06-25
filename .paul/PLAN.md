# Plan: Accessibility Reboot Survival — 5-Layer Defense

## Acceptance Criteria
- [x] AC-1: Given a device reboot during an active session, When accessibility is dead, Then a full-screen SYSTEM_ALERT_WINDOW overlay locks the phone and forces user to re-enable accessibility. Overlay only dismisses after confirmed re-enable.
- [x] AC-2: Given the enforcement overlay is shown, When user goes to settings and presses back WITHOUT enabling, Then overlay reappears and stays locked.
- [x] AC-3: Given the enforcement overlay is shown, When user actually enables accessibility, Then overlay auto-dismisses within 2 seconds (polling) and session resumes normally.
- [x] AC-4: Given NO active session, When accessibility is missing, Then normal PermissionSetup/Repair flow handles it (no overlay).
- [x] AC-5: Given FocusAccessibilityService is running, When the OS considers killing it, Then it resists via foreground notification.
- [x] AC-6: Given an active session, When a periodic health check detects accessibility is dead, Then the enforcement overlay is launched.
- [x] AC-7: Given the PermissionSetup screen, When on an OEM device, Then user can tap to open autostart settings.

## Tasks

### [x] Task 1: PermissionEnforcementService (Core — full-screen lock overlay)
- **What**: New foreground service using SYSTEM_ALERT_WINDOW (same pattern as BlockingOverlayService). Creates undismissable full-screen overlay. Shows "FocusLock protection is disabled — enable accessibility to continue your session." Has one button: "Enable Now" → opens accessibility settings. Polls PermissionChecker every 2 seconds. Auto-dismisses only when accessibility is confirmed granted.
- **Files**: New `service/overlay/PermissionEnforcementService.kt`, new Compose screen `ui/overlay/PermissionEnforcementScreen.kt`
- **Verify**: Build succeeds. Service creates overlay, button opens accessibility settings, overlay dismisses when permission is granted.

### [x] Task 2: BootReceiver → Launch enforcement overlay
- **What**: When BootReceiver detects active session + dead accessibility → start PermissionEnforcementService instead of just logging.
- **Files**: `BootReceiver.kt`
- **Verify**: Build succeeds. Reboot with active session → overlay appears.

### [x] Task 3: OnboardingViewModel — Fix routing bug
- **What**: Current bug: `hasActiveSession` always routes to ActiveSession, even if accessibility is dead. Fix: `hasActiveSession && !accessibilityGranted` → launch PermissionEnforcementService + navigate to ActiveSession. This handles the case when user opens the app manually after reboot.
- **Files**: `OnboardingViewModel.kt`
- **Verify**: Build succeeds. Open app with active session + dead accessibility → enforcement overlay appears.

### [x] Task 4: AccessibilityHealthWorker — Periodic enforcement
- **What**: New WorkManager PeriodicWorkRequest (15 min). If active session + dead accessibility → start PermissionEnforcementService. Scheduled when session starts, cancelled when session ends.
- **Files**: New `data/worker/AccessibilityHealthWorker.kt`, modify `SessionWorkerScheduler.kt`
- **Verify**: Build succeeds. Worker runs, detects dead accessibility, launches enforcement.

### [x] Task 5: FocusAccessibilityService → Foreground notification (resist OEM kill)
- **What**: In onServiceConnected(), call startForeground() with a dedicated low-priority notification channel. Makes OS treat the service as "in use" → harder to kill.
- **Files**: `FocusAccessibilityService.kt`
- **Verify**: Build succeeds. Accessibility service shows persistent notification.

### [x] Task 6: Layer 4 OEM Autostart Helper
- **What**: New `OemAutostartHelper` object. Uses Build.MANUFACTURER to deep-link into Xiaomi/POCO/Samsung/Oppo/Vivo/Huawei/Realme/OnePlus autostart settings. One tap opens exact OEM autostart settings page. User whitelists FocusLock → service survives reboots.
- **Files**: New `util/OemAutostartHelper.kt`
- **Verify**: Build succeeds. On Xiaomi, opens autostart settings.

### [x] Task 7: Rigorous Unit Testing (Loop & Fix)
- **What**: Ensure all edge cases of the new enforcement architecture are covered via Robolectric tests. Run tests via `./gradlew testDebugUnitTest`. If any test fails, iteratively fix logic (like Robolectric OutOfMemoryError on ComposeView) and re-test until stable.
- **Files**: `PermissionEnforcementServiceTest.kt`, `AccessibilityHealthWorkerTest.kt`, `OemAutostartHelperTest.kt`.
- **Verify**: 100% test pass rate for all unit tests.

## Skills
- @master-skill
- @ponytail ultra
- @kotlin-coroutines-expert
- @fable (verification-driven execution)
- @caveman (compressed output)

## Execution Order
Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6
(Task 1 is the foundation — Tasks 2,3,4 all depend on PermissionEnforcementService existing)

