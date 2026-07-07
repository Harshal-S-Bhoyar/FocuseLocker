# Plan: Phase 2 - Bulletproof Permission Enforcement System

## Acceptance Criteria
- [ ] AC-1: Device reboots with active session → enforcement overlay appears WITHOUT user opening app
- [ ] AC-2: Accessibility toggled OFF while user is in Chrome → overlay appears within 5 seconds
- [ ] AC-3: User clicks "Grant Accessibility" → sees Settings → presses Back → overlay re-appears within 3 seconds
- [ ] AC-4: User clicks "Grant Accessibility" → sees Settings → opens Recent Apps → overlay is visible on top
- [ ] AC-5: AutoStart button shows tick after user returns from OEM settings
- [ ] AC-6: No enforcement overlay when no active session exists
- [ ] AC-7: Overlay dismisses immediately when missing permission is granted

## Tasks
- [ ] Task 1: Create `PermissionWatchdogService`
- [ ] Task 2: Add `SettingsGuardMode` to `FocusAccessibilityService`
- [ ] Task 3: Modify `PermissionEnforcementService` grace period (30s -> 3s)
- [ ] Task 4: Modify `BootReceiver` to start watchdog
- [ ] Task 5: Fix AutoStart UI in `PermissionSetupScreen` & ViewModel
