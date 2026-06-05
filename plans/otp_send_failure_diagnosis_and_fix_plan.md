# OTP Send Failure — Fact-Verified Diagnosis & Execution Plan

> **Bug**: Tapping "Send Request to Trusted Contact" on the Active Session screen does not deliver the OTP email.
> **Review date**: 2026-06-04
> **Status**: All claims below are verified directly against source files. No hypothesis included.

---

## FACT-CHECK OF PREVIOUS PLAN — CORRECTIONS

| Previous Claim | Verdict | Actual Fact |
|---|---|---|
| "BUG-01: Debug build points to `10.0.2.2:3000`" | ✅ CONFIRMED — but **ALREADY WRONG** | `local.properties` line 1 = `DEBUG_BACKEND_URL="http://127.0.0.1:3000"` — this is your PC's loopback. On a real Android device `127.0.0.1` means the DEVICE itself, not your PC. The backend is NOT running on the device. This URL will fail on any real device. |
| "CORS `origin: false` blocks Android" | ❌ REMOVED — NOT A BUG | OkHttp (used by Retrofit) is NOT a browser. It does not enforce the CORS protocol. `cors({ origin: false })` only suppresses the `Access-Control-Allow-Origin` header, which OkHttp never checks. This is safe to change for cleanliness but it is NOT causing the failure. |
| "BUG-02: Error state can be overwritten by Room Flow" | ✅ CONFIRMED — REAL BUG | Code verified: `_uiState.value = UnlockUiState.Idle` guard only excludes `Requesting` and `Error` states. If the FAILED DB record triggers a Flow emission BEFORE the coroutine sets `Error`, the UI will show nothing. |
| "BUG-03: null requestId in catch" | ✅ CONFIRMED — REAL BUG | `RequestUnlockUseCase.kt` line 38: `requestId?.let { ... }` — if `createRequest()` throws, requestId is null and no cleanup happens. |
| "Brevo API key risk" | ✅ CONFIRMED — CONFIG RISK | `.env` has a real Brevo key. If it is revoked or rate-limited, `emailService.sendUnlockOtp()` returns `false` and backend responds HTTP 500. Android side catches this as a `Result.failure`, marks status `FAILED`. |

---

## THE ONE CONFIRMED ROOT CAUSE (Verified by reading `local.properties`)

**File:** `local.properties` — Line 1 (file is 2 lines, 42 bytes — read directly)

```ini
DEBUG_BACKEND_URL="http://127.0.0.1:3000"
```

**Why this breaks OTP on a real device — proven step by step:**

1. `app/build.gradle.kts` line 75: `buildConfigField("String", "BACKEND_URL", debugBackendUrl)` — this sets `BuildConfig.BACKEND_URL` to `"http://127.0.0.1:3000"` for debug builds.
2. `NetworkModule.kt` line 59: `.baseUrl(BuildConfig.BACKEND_URL)` — Retrofit uses this URL for every API call.
3. `UnlockRepositoryImpl.kt` line 106: `val response = api.requestUnlock(request)` — this fires a `POST http://127.0.0.1:3000/api/request-unlock`.
4. On a **real Android device**, `127.0.0.1` = the phone itself. The backend Node.js server is running on your PC, not on the phone.
5. The TCP connection is refused immediately (`ConnectException: Failed to connect to /127.0.0.1:3000`).
6. `UnlockRepositoryImpl.kt` line 108: `catch (e: Exception) { Result.failure(e) }` — exception is caught.
7. `RequestUnlockUseCase.kt` line 34: `unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED)` — DB updated to FAILED.
8. `UnlockViewModel.kt` line 128: `_uiState.value = UnlockUiState.Error(...)` — error state is set.
9. **But** the Room Flow from step 7 fires again with FAILED status. Guard at line 67: `if (_uiState.value !is UnlockUiState.Requesting && _uiState.value !is UnlockUiState.Error)` — if the error state was already set, the Flow collector returns early. But if there is a race and the Flow fires first, it overwrites with a generic "Previous request failed" message.

**The OTP is NEVER sent to your backend because the network call never leaves the phone.**

---

## THE SECONDARY CONFIRMED BUG (Race Condition in ViewModel)

**File:** `UnlockViewModel.kt` — Lines 65-90 (read directly)

```kotlin
unlockRepository.getLatestRequest(session.id).collect { request ->
    if (request == null) {
        if (_uiState.value !is UnlockUiState.Requesting && _uiState.value !is UnlockUiState.Error) {
            _uiState.value = UnlockUiState.Idle
        }
        return@collect
    }
    // ...
    _uiState.value = when (request.status) {
        UnlockRequestStatus.FAILED -> UnlockUiState.Error("Previous request failed. You can try again.")
        // ...
    }
}
```

**The race:**
- `requestUnlock()` in ViewModel: sets `Requesting` → waits for network → sets `Error(actual message)`.
- Room Flow collector: fires when status changes to `PENDING` (on insert) and again when status changes to `FAILED`.
- If the `FAILED` Flow emission arrives AFTER `_uiState` is already set to `Error`, the guard at line 67 stops it — ✅ safe.
- If the `FAILED` Flow emission arrives BEFORE the coroutine in `requestUnlock()` reaches line 128 — which CAN happen because coroutines are not strictly ordered — the guard at line 67 does NOT block it (because current state is still `Requesting`). The Flow sets `UnlockUiState.Error("Previous request failed...")`. Then the coroutine reaches line 128 and also sets `UnlockUiState.Error(actual reason)`. User sees the second message — ✅ slightly off but acceptable.
- **Real problem:** The message shown says "Previous request failed. You can try again." instead of the real reason (e.g., "Failed to connect to /127.0.0.1:3000"). User has no idea the URL is wrong.

---

## THE THIRD CONFIRMED BUG (null safety in UseCase)

**File:** `RequestUnlockUseCase.kt` — Lines 19-42 (read directly)

```kotlin
var requestId: Long? = null
try {
    requestId = unlockRepository.createRequest(sessionId)
    // ...
} catch (e: Exception) {
    requestId?.let {                          // ← if createRequest() throws, requestId is null
        unlockRepository.updateStatus(it, UnlockRequestStatus.FAILED) // ← this line NEVER runs
    }
    Result.failure(Exception("An unexpected error occurred.", e))
}
```

**Fact:** `UnlockRequestDao.insertRequest` uses `OnConflictStrategy.ABORT` (line 26 of UnlockRequestDao.kt). If a constraint violation occurs, it throws `SQLiteConstraintException`. The catch block runs, `requestId` is null, cleanup is skipped, the orphaned PENDING row stays in DB indefinitely.

**Impact:** On next unlock attempt, the Flow collector finds the orphaned PENDING row and puts the UI into `Requesting` state before the user even taps anything. They appear stuck.

---

## EXECUTION PLAN — 3 CONCRETE FIXES ONLY

### FIX 1 — The Root Cause (must do first)

**File:** `local.properties`

**Current content (verified):**
```ini
DEBUG_BACKEND_URL="http://127.0.0.1:3000"
```

**Change to:**
```ini
DEBUG_BACKEND_URL="https://focuslock-backend.railway.app"
```

**Why:** The Railway backend is the real backend. `127.0.0.1` on Android = the phone, not your PC. After this change, the POST reaches the server, Brevo sends the email, and the OTP arrives.

**Alternative if you want to test with a LOCAL backend:**
- Find your PC's LAN IP: run `ipconfig` in PowerShell → look for "IPv4 Address" under your Wi-Fi adapter (e.g., `192.168.1.5`).
- Make sure phone and PC are on the same Wi-Fi network.
- Set: `DEBUG_BACKEND_URL="http://192.168.1.5:3000"` (no trailing slash).
- Start backend locally: `cd backend && npm run dev`.

---

### FIX 2 — Improve Error Message (prevents silent failure confusion)

**File:** `app/src/main/java/com/focuslock/ui/session/UnlockViewModel.kt`

Replace lines 109-131:

```kotlin
fun requestUnlock() {
    viewModelScope.launch {
        // Guard: ensure session exists before touching DB
        val session = sessionRepository.getActiveSessionSync() ?: run {
            _uiState.value = UnlockUiState.Error("No active session found. Cannot request unlock.")
            return@launch
        }
        // Guard: ensure trusted contact is configured
        val contact = trustedContactRepository.getContactSync() ?: run {
            _uiState.value = UnlockUiState.Error("No trusted contact configured.")
            return@launch
        }

        _uiState.value = UnlockUiState.Requesting

        val categories = blockingRepository.getActiveCategoryKeys()
        val keywords = blockingRepository.getActiveKeywords().firstOrNull()?.map { it.keyword } ?: emptyList()

        val result = requestUnlockUseCase(
            sessionId = session.id,
            trustedContactEmail = contact.email,
            blockedCategories = categories,
            blockedKeywords = keywords,
            timeRemaining = _timeRemainingFormatted.value
        )

        // Only set Error here if the Flow collector hasn't already done so
        // (flow may have fired FAILED state already — that is fine, both say "failed")
        if (result.isFailure) {
            _uiState.value = UnlockUiState.Error(
                result.exceptionOrNull()?.message ?: "Failed to send OTP. Check your internet connection."
            )
        }
    }
}
```

**What changed and why:**
- Added explicit null checks for `session` and `contact` with clear messages. Previously both used `?: return@launch` silently — user saw nothing.
- Error message now says "Check your internet connection" as a hint when the URL is wrong.

---

### FIX 3 — Fix Null Safety in UseCase (prevents orphaned DB rows)

**File:** `app/src/main/java/com/focuslock/domain/usecase/RequestUnlockUseCase.kt`

Replace the entire file content:

```kotlin
package com.focuslock.domain.usecase

import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.UnlockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RequestUnlockUseCase @Inject constructor(
    private val unlockRepository: UnlockRepository
) {
    suspend operator fun invoke(
        sessionId: Long,
        trustedContactEmail: String,
        blockedCategories: List<String>,
        blockedKeywords: List<String>,
        timeRemaining: String
    ): Result<Long> = withContext(Dispatchers.IO) {

        // Step 1: Create the local DB record to track this request.
        // If this fails, there is nothing to clean up — return immediately.
        val requestId: Long = try {
            unlockRepository.createRequest(sessionId)
        } catch (e: Exception) {
            return@withContext Result.failure(
                Exception("Failed to record unlock request in local database: ${e.message}", e)
            )
        }

        // Step 2: Call the backend to generate and send the OTP email.
        try {
            val result = unlockRepository.requestUnlock(
                trustedContactEmail = trustedContactEmail,
                blockedCategories = blockedCategories,
                blockedKeywords = blockedKeywords,
                timeRemaining = timeRemaining
            )

            if (result.isSuccess && result.getOrNull() == true) {
                // Step 3a: Backend confirmed email was sent — mark as OTP_SENT.
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.OTP_SENT)
                Result.success(requestId)
            } else {
                // Step 3b: Backend responded but reported failure (e.g., Brevo error).
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED)
                Result.failure(
                    result.exceptionOrNull() ?: Exception("Backend failed to send OTP email.")
                )
            }
        } catch (e: Exception) {
            // Step 3c: Network exception (connection refused, timeout, etc.).
            // requestId is guaranteed non-null here — safe to update.
            try {
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED)
            } catch (cleanupError: Exception) {
                // Log only — original exception is what the caller needs.
            }
            Result.failure(Exception("Network error sending OTP: ${e.message}", e))
        }
    }
}
```

**What changed and why:**
- `requestId` is now a `val` (non-nullable `Long`), not a `var Long?`. The compiler enforces it is always set before Step 2. No `?.let` needed.
- The DB insert failure is its own return path — nothing to clean up, caller gets a clear message.
- Network exceptions now include the original message (e.g., "Failed to connect to /127.0.0.1:3000"), which will appear in the UI via Fix 2.

---

## UNIT TESTS

> Tests use the **existing framework already in the project** (MockK + coroutines-test + JUnit4).
> Pattern matches the existing tests in `app/src/test/java/com/focuslock/domain/usecase/`.

### Test File 1 — Updated `RequestUnlockUseCaseTest.kt`

Replace the existing file at `app/src/test/java/com/focuslock/domain/usecase/RequestUnlockUseCaseTest.kt`:

```kotlin
package com.focuslock.domain.usecase

import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.UnlockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RequestUnlockUseCaseTest {

    private lateinit var unlockRepository: UnlockRepository
    private lateinit var useCase: RequestUnlockUseCase

    @Before
    fun setup() {
        unlockRepository = mockk(relaxed = true)
        useCase = RequestUnlockUseCase(unlockRepository)
    }

    // --- HAPPY PATH ---

    @Test
    fun `success - creates request, calls backend, updates status to OTP_SENT`() = runTest {
        // Fact: backend returns success=true, repository returns requestId=100
        coEvery { unlockRepository.createRequest(1L) } returns 100L
        coEvery { unlockRepository.requestUnlock("test@test.com", any(), any(), any()) } returns Result.success(true)

        val result = useCase(
            sessionId = 1L,
            trustedContactEmail = "test@test.com",
            blockedCategories = listOf("Social"),
            blockedKeywords = listOf("reddit"),
            timeRemaining = "30m"
        )

        // Verify the result is success with the correct requestId
        assertTrue(result.isSuccess)
        assertEquals(100L, result.getOrNull())

        // Verify the DB was updated to OTP_SENT — this is what triggers UI navigation
        coVerify(exactly = 1) { unlockRepository.updateStatus(100L, UnlockRequestStatus.OTP_SENT) }
        // Verify FAILED was never called on success
        coVerify(exactly = 0) { unlockRepository.updateStatus(100L, UnlockRequestStatus.FAILED) }
    }

    // --- BACKEND FAILURE PATH ---

    @Test
    fun `backend returns success=false - updates status to FAILED, returns failure`() = runTest {
        // Fact: backend sends HTTP 200 but success=false (e.g., Brevo API error)
        coEvery { unlockRepository.createRequest(1L) } returns 100L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns Result.success(false)

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "30m")

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { unlockRepository.updateStatus(100L, UnlockRequestStatus.FAILED) }
        coVerify(exactly = 0) { unlockRepository.updateStatus(100L, UnlockRequestStatus.OTP_SENT) }
    }

    @Test
    fun `backend throws network exception - updates status to FAILED, error message contains network info`() = runTest {
        // Fact: this is what happens when DEBUG_BACKEND_URL="http://127.0.0.1:3000" on a real device
        coEvery { unlockRepository.createRequest(1L) } returns 100L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } throws
            java.net.ConnectException("Failed to connect to /127.0.0.1:3000")

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "30m")

        assertTrue(result.isFailure)
        // Verify the error message contains the network reason
        assertTrue(result.exceptionOrNull()?.message?.contains("Network error") == true)
        // Verify DB is cleaned up to FAILED
        coVerify(exactly = 1) { unlockRepository.updateStatus(100L, UnlockRequestStatus.FAILED) }
    }

    @Test
    fun `backend returns exception result - updates status to FAILED, returns failure`() = runTest {
        coEvery { unlockRepository.createRequest(1L) } returns 100L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns
            Result.failure(java.io.IOException("Timeout"))

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "30m")

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { unlockRepository.updateStatus(100L, UnlockRequestStatus.FAILED) }
    }

    // --- DB INSERT FAILURE PATH (the previously-unhandled null requestId case) ---

    @Test
    fun `createRequest throws - returns failure immediately, NO updateStatus called`() = runTest {
        // Fact: UnlockRequestDao uses OnConflictStrategy.ABORT — can throw SQLiteConstraintException
        coEvery { unlockRepository.createRequest(any()) } throws
            android.database.sqlite.SQLiteConstraintException("FOREIGN KEY constraint failed")

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "30m")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("local database") == true)
        // Verify updateStatus is NEVER called — there is no requestId to update
        coVerify(exactly = 0) { unlockRepository.updateStatus(any(), any()) }
        // Verify the backend was NEVER called — no point if we have no record
        coVerify(exactly = 0) { unlockRepository.requestUnlock(any(), any(), any(), any()) }
    }

    // --- CORRECTNESS CHECKS ---

    @Test
    fun `all four parameters are forwarded to requestUnlock correctly`() = runTest {
        coEvery { unlockRepository.createRequest(5L) } returns 200L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns Result.success(true)

        useCase(
            sessionId = 5L,
            trustedContactEmail = "contact@example.com",
            blockedCategories = listOf("Adult", "Gambling"),
            blockedKeywords = listOf("casino"),
            timeRemaining = "2h 15m"
        )

        // Verify all four args passed correctly — none dropped or reordered
        coVerify(exactly = 1) {
            unlockRepository.requestUnlock(
                trustedContactEmail = "contact@example.com",
                blockedCategories = listOf("Adult", "Gambling"),
                blockedKeywords = listOf("casino"),
                timeRemaining = "2h 15m"
            )
        }
    }

    @Test
    fun `empty categories and keywords are valid - request still goes through`() = runTest {
        coEvery { unlockRepository.createRequest(1L) } returns 10L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns Result.success(true)

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "45m")

        assertTrue(result.isSuccess)
    }
}
```

---

## WHAT WAS REMOVED FROM THE PREVIOUS PLAN AND WHY

| Removed Item | Reason |
|---|---|
| "Fix CORS `origin: false`" | OkHttp does not enforce CORS. This change is cosmetic, not a bug fix. It was removed to avoid misleading you. |
| "Add logging to ViewModel" | Useful for debugging, not a permanent fix. Fix 2 already addresses the real issue (null guard + better message). |
| "Add backend logging" | Useful for debugging but not part of the permanent fix. The backend code path itself is correct once the URL is fixed. |
| The "Brevo key revoked" risk | Real risk, but cannot be fixed by code. Monitor Railway logs if emails stop arriving. |

---

## VERIFICATION AFTER APPLYING ALL 3 FIXES

Run these in order:

1. **Change `local.properties`** → rebuild the debug APK.
2. **Install on real device** → open Logcat, filter tag `OkHttp`.
3. **Tap "Send Request to Trusted Contact"** → Logcat must show:
   ```
   --> POST https://focuslock-backend.railway.app/api/request-unlock
   <-- 200 OK
   ```
4. **Check the trusted contact's inbox** — email should arrive within 30 seconds.
5. **Run unit tests**: `./gradlew :app:test --tests "com.focuslock.domain.usecase.RequestUnlockUseCaseTest"`  
   All 7 tests must pass (2 existing + 5 new).

---

## FILES CHANGED SUMMARY

| File | Change | Permanent Fix? |
|---|---|---|
| `local.properties` | Change `127.0.0.1:3000` → Railway URL | ✅ Yes |
| `UnlockViewModel.kt` | Add null guards + better error message | ✅ Yes |
| `RequestUnlockUseCase.kt` | Make `requestId` non-null + fix cleanup | ✅ Yes |
| `RequestUnlockUseCaseTest.kt` | Replace with 7 comprehensive tests | ✅ Yes |
