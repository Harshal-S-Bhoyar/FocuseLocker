# Session Lifecycle Hardening — Execution Plan
> Status: COMPLETED  
> Created: 2026-06-04  
> Scope: Fix 2 architectural issues found in post-fix audit of "Session Still Active" bug  
> Depends on: `session_activation_lifecycle_fix` — ✅ ALREADY APPLIED (BUILD SUCCESSFUL)

---

## Context

The "Session Still Active" core bug is fixed and build passes.  
Two follow-up architectural issues remain from the `@master-skill` code review audit:

| # | Issue | Severity |
|---|---|---|
| 1 | Circular dependency risk — `ActivateSessionUseCase` + `IsSessionActiveUseCase` directly depend on `DeactivateSessionUseCase` | Medium |
| 2 | Inconsistent stale cleanup — `CheckOnboardingStatusUseCase` calls `hasActiveSession()` which returns `false` for stale sessions but does NOT purge the stale ACTIVE DB row | Low |

---

## Fix #1 — Extract `CleanupStaleSessionUseCase`

### Why
`ActivateSessionUseCase` and `IsSessionActiveUseCase` both now depend on `DeactivateSessionUseCase`.
If any future use case creates `DeactivateSessionUseCase` → `ActivateSessionUseCase`, Hilt will fail with a circular dependency compile error.

Single-responsibility fix: extract the stale-check + cleanup logic into one dedicated use case.

---

### Step 1 — CREATE `CleanupStaleSessionUseCase.kt`

**Path:**
```
app/src/main/java/com/focuslock/domain/usecase/CleanupStaleSessionUseCase.kt
```

**Full content:**
```kotlin
package com.focuslock.domain.usecase

import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * Checks if the currently active session has expired.
 * If yes, performs full cleanup: DB status update + cancel WorkManager timers + stop runtime.
 * Returns true if a stale session was found and cleaned, false otherwise.
 *
 * Use this instead of calling DeactivateSessionUseCase directly from activation
 * or routing logic, to avoid tight coupling.
 */
class CleanupStaleSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val deactivateSessionUseCase: DeactivateSessionUseCase
) {
    suspend operator fun invoke(): Boolean {
        // Get any session currently marked as ACTIVE in the database
        val activeSession = sessionRepository.getActiveSessionSync() ?: return false

        // If the session hasn't expired yet, it's legitimately active — do nothing
        if (activeSession.expiresAt > System.currentTimeMillis()) return false

        // Session is stale — clean up DB + WorkManager timers + VPN runtime
        deactivateSessionUseCase(activeSession.id, SessionStatus.COMPLETED)
        return true
    }
}
```

---

### Step 2 — MODIFY `ActivateSessionUseCase.kt`

**File:** `app/src/main/java/com/focuslock/domain/usecase/ActivateSessionUseCase.kt`

**Change 1 — Constructor:** Replace `DeactivateSessionUseCase` with `CleanupStaleSessionUseCase`

```diff
 class ActivateSessionUseCase @Inject constructor(
     private val sessionRepository: SessionRepository,
     private val sessionWorkerScheduler: SessionWorkerScheduler,
     private val sessionRuntimeController: SessionRuntimeController,
-    private val deactivateSessionUseCase: DeactivateSessionUseCase
+    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase
 )
```

**Change 2 — Stale check block:** Replace manual stale check with `CleanupStaleSessionUseCase` call

```diff
-        val activeSession = sessionRepository.getActiveSessionSync()
-        if (activeSession != null) {
-            if (activeSession.expiresAt <= System.currentTimeMillis()) {
-                // Session is stale, clean it up completely (DB + Workers + Runtime)
-                deactivateSessionUseCase(activeSession.id, SessionStatus.COMPLETED)
-            } else {
-                return Result.failure(IllegalStateException("An active session already exists."))
-            }
-        }
+        // Clean up any expired session first.
+        // If nothing was cleaned AND a live session exists → block creation.
+        val cleaned = cleanupStaleSessionUseCase()
+        if (!cleaned && sessionRepository.hasActiveSession()) {
+            return Result.failure(IllegalStateException("An active session already exists."))
+        }
```

**Change 3 — Remove unused imports:**
- Remove `import com.focuslock.domain.model.SessionStatus` if it is only used in the stale-check block
  (it is still used in `rollbackActivation` → keep it)

---

### Step 3 — MODIFY `IsSessionActiveUseCase.kt`

**File:** `app/src/main/java/com/focuslock/domain/usecase/IsSessionActiveUseCase.kt`

**Replace entire file content:**
```kotlin
package com.focuslock.domain.usecase

import com.focuslock.domain.repository.SessionRepository
import javax.inject.Inject

class IsSessionActiveUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase
) {
    suspend operator fun invoke(): Boolean {
        // Clean up any stale session first. If cleaned, session is no longer active.
        val cleaned = cleanupStaleSessionUseCase()
        if (cleaned) return false

        // Check if a genuinely active (non-expired) session exists in DB
        return sessionRepository.hasActiveSession()
    }
}
```

> Note: `SessionStatus` import removed — no longer needed here.

---

## Fix #2 — Add Stale Cleanup in `InitializeAppUseCase`

### Why
`CheckOnboardingStatusUseCase` calls `sessionRepository.hasActiveSession()` on every app launch.
`hasActiveSession()` correctly returns `false` for expired sessions, but the stale ACTIVE row stays in the database.
Workers can still pick it up on their next execution cycle and behave incorrectly.

Adding `cleanupStaleSessionUseCase()` to `InitializeAppUseCase` ensures the DB is always clean before any navigation routing happens.

---

### Step 4 — VIEW `InitializeAppUseCase.kt` first

**File:** `app/src/main/java/com/focuslock/domain/usecase/InitializeAppUseCase.kt`

> Must be viewed before editing to understand current structure and all existing logic.

---

### Step 5 — MODIFY `InitializeAppUseCase.kt`

After viewing, apply:

**Constructor change:** Add `CleanupStaleSessionUseCase` injection  
**Logic change:** Call `cleanupStaleSessionUseCase()` as the FIRST operation inside `invoke()`

```kotlin
// Inside invoke() — ADD as first line:
cleanupStaleSessionUseCase()  // Purge any stale ACTIVE sessions from DB before routing
```

This ensures:
- Any ACTIVE session with `expiresAt < now` is marked `COMPLETED`
- WorkManager timers are cancelled
- VPN runtime is stopped
- `hasActiveSession()` called anywhere after this is guaranteed accurate

---

## Execution Order

```
Step 1  →  Step 2  →  Step 3  →  Step 4 (view)  →  Step 5  →  Build  →  Test
```

Each step must be applied sequentially. Do not parallelize.

---

## Build Verification

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

---

## Manual Test Checklist

- [ ] Start a 15-minute session → let it expire naturally
- [ ] Kill app and reopen → verify no "Session Still Active" error
- [ ] Verify new session can be started immediately after expiry
- [ ] Verify Onboarding screen does NOT navigate to ActiveSession after expiry
- [ ] Reboot device after session expiry → app starts cleanly, no stale session restored
- [ ] Start session → force-expire (adjust device clock) → reopen app → confirm cleanup fires

---

## Success Criteria

| Criteria | How to Verify |
|---|---|
| `CleanupStaleSessionUseCase` created | File exists, compiles |
| No circular dependency | `./gradlew assembleDebug` passes without Hilt graph errors |
| `ActivateSessionUseCase` no longer imports `DeactivateSessionUseCase` | Code review |
| `IsSessionActiveUseCase` no longer imports `DeactivateSessionUseCase` | Code review |
| Stale ACTIVE DB rows purged on app launch | Manual test — DB inspection |
| New session starts cleanly after expiry | Manual test |

---

## Rule Compliance (per `#6_CONTEXT_AND_RULES.md`)

| Rule | Status |
|---|---|
| No `!!` | ✅ Elvis + safe calls only |
| No business logic in ViewModel | ✅ All logic in UseCases |
| No `runBlocking` | ✅ Suspend functions only |
| DAO reads for reactive data return `Flow<T>` | ✅ Not changing DAO |
| Single-active-session constraint | ✅ Strengthened |
| Keywords stored in lowercase | N/A |
| Backend URL from `BuildConfig` | N/A |
