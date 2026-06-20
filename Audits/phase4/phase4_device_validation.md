# FocusLock Phase 4 Device Validation

Run this checklist on a physical Android device after the Gradle verification pass.

## Setup

1. Install the latest debug APK.
2. Complete onboarding and grant accessibility, device-admin, overlay, VPN, and notification permissions.
3. Confirm the Continue button stays disabled until the four critical permissions are granted.

## Session Lifecycle

1. Start a 15-minute session.
2. Confirm the active-session screen opens within 5 seconds.
3. Confirm the persistent notification appears immediately.
4. Confirm the active screen reports VPN permission, accessibility monitoring, and uninstall protection accurately.

## VPN

1. Open an allowed website and confirm normal internet access still works.
2. Open a blocked domain and confirm DNS blocking occurs.
3. Repeat after temporarily making the primary DNS resolver unreachable to exercise fallback behavior.

## Overlay

1. Open a URL containing a blocked keyword in each supported browser available on the device.
2. Confirm the overlay appears with the matched reason and remaining session time.
3. Navigate to allowed content and confirm the overlay dismisses.
4. Open protected Android settings during a session and confirm the blocking overlay appears.

## Recovery And Expiry

1. Reboot during an active session.
2. Confirm the session countdown, VPN, workers, and notification recover.
3. Let a short test session expire.
4. Confirm VPN, overlay, workers, and notification stop and the completion screen opens.

## Device Admin Limitation

1. Attempt to disable Device Admin during an active session.
2. Confirm FocusLock returns the active-session warning.
3. Record the device model and Android version because consumer Device Admin is advisory, not kiosk-grade enforcement.

