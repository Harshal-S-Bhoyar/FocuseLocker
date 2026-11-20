# Tasks

## 1. Offload OEM IPC off the main thread

- [ ] 1.1 Wrap the `OemAutostartHelper.getAutostartIntentsList(context)` and `OemBatteryOptimizationHelper.getBatteryOptimizationIntentsList(context)` calls in `withContext(Dispatchers.IO)` inside `refreshPermissions()` in `app/src/main/java/com/focuslock/ui/onboarding/PermissionSetupViewModel.kt`, keeping state emission, the 3-attempt retry loop, and the `allCriticalGranted` early return untouched, and verify `./gradlew.bat :app:assembleDebug` succeeds
- [ ] 1.2 Verify zero behavior change by running the existing permission-setup unit tests via `./gradlew.bat :app:testDebugUnitTest --tests "com.focuslock.ui.onboarding.*"` with zero failures and `./gradlew.bat :app:lintDebug` clean
