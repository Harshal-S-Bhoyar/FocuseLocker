# Global Exception Handling & File Logging Implementation Plan

## Goal Description
Implement a robust global exception handling mechanism across the entire FocuseLocker application. This will capture all uncaught exceptions (crashes), log them, and prevent silent failures. In addition, we will introduce a structured logging system that intercepts all logs (including standard app logs and crash reports) and writes them to a dedicated `log` folder.

## User Review Required
> [!IMPORTANT]
> **Log Folder Location:** Android security restrictions prevent apps from writing directly to the literal "root" of the device filesystem (e.g., `/log`). We will create the `log` folder in the app's external files directory (e.g., `Android/data/com.focuslock/files/log/`). This ensures the app has write permissions without requiring dangerous MANAGE_EXTERNAL_STORAGE permissions. Please confirm if this location is acceptable.

## Open Questions
> [!WARNING]
> **Crash Recovery Behavior:** When a global exception is caught, the app will inevitably crash. Should we simply log the crash and let the app terminate, or should we attempt to restart the app/show a custom crash screen after saving the log? (Default plan: Log it, then terminate normally so the OS knows it crashed).

## Proposed Changes

### 1. Build Configuration
#### [MODIFY] `app/build.gradle.kts`
- Add the `Timber` dependency (`com.jakewharton.timber:timber:5.0.1`) to standardize logging and allow routing logs to both Logcat and a file.

---

### 2. Logging & Exception Handling Infrastructure
#### [NEW] `app/src/main/java/com/focuslock/utils/FileLoggingTree.kt`
- Create a custom Timber `Tree` that intercepts all log messages.
- Format logs with timestamps, thread info, and tags.
- Append logs to a file in the `log` directory (e.g., `app_logs_YYYY_MM_DD.txt`).

#### [NEW] `app/src/main/java/com/focuslock/utils/GlobalExceptionHandler.kt`
- Create a class implementing `Thread.UncaughtExceptionHandler`.
- In the `uncaughtException` callback, use Timber to log the fatal error with its full stack trace.
- Delegate to the original system `UncaughtExceptionHandler` so the app terminates properly after the log is saved.

---

### 3. Application Setup
#### [MODIFY] `app/src/main/java/com/focuslock/FocusLockApplication.kt`
- Initialize Timber in `onCreate()`. Plant a `DebugTree` (for Logcat) and our custom `FileLoggingTree` (for saving to the `log` folder).
- Register the `GlobalExceptionHandler` by calling `Thread.setDefaultUncaughtExceptionHandler()`.

---

### 4. Codebase Refactoring
#### [MODIFY] `Various Files`
- Replace existing `Log.d`, `Log.e`, etc., with `Timber.d`, `Timber.e` to ensure all existing logs are routed to the file system.
- Affected files identified: `VpnPacketLoop.kt`, `FocusVpnService.kt`, `BlockingOverlayService.kt`, `BootReceiver.kt`, `FocusAccessibilityService.kt`, `FocusDeviceAdminReceiver.kt`.

## Verification Plan

### Automated Tests
- N/A for global crashes, but we will write unit tests for the `FileLoggingTree` logic.

### Manual Verification
1. Run the app and perform basic actions. Check the `log` directory on the device (`Android/data/com.focuslock/files/log/`) to ensure logs are being written correctly.
2. Introduce an intentional crash (e.g., throw a `RuntimeException` on a button click).
3. Verify that the app crashes, but the `log` folder contains the full stack trace of the intentional crash.
