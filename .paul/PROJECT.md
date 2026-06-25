# Project: FocuseLocker — Accessibility Reboot Survival

## Requirements
- After device reboot, if active session exists + accessibility is dead → LOCK the entire phone screen with SYSTEM_ALERT_WINDOW overlay forcing user to re-enable accessibility
- Overlay is undismissable — back button does nothing, home button brings user back to overlay
- Overlay auto-dismisses ONLY when PermissionChecker confirms accessibility is re-enabled (2-second polling)
- If no active session → normal onboarding/PermissionRepair flow (no overlay enforcement)
- Periodic health monitoring during active sessions to detect accessibility death mid-session → launch same enforcement overlay
- FocusAccessibilityService runs as foreground service to resist OEM killing
- OEM-specific autostart deep links for Xiaomi/POCO/Samsung/Oppo/Vivo/Huawei

## Constraints
- Android cannot programmatically re-enable accessibility services (security restriction)
- Must use existing DI (Hilt), existing patterns (WorkManager, SYSTEM_ALERT_WINDOW overlay via ComposeView)
- Reuse BlockingOverlayService pattern for PermissionEnforcementService
- Foreground notification from accessibility service must use separate channel/ID from VPN notification
- Minimum code changes — reuse PermissionChecker, existing overlay infrastructure
