# Twilio → Firebase Phone Auth Migration Plan
## FocusLock — SMS Provider Migration
## Created: 2026-06-02

---

> [!IMPORTANT]
> Firebase Phone Auth handles OTP generation, SMS delivery, AND verification — all on Google's servers.
> This migration **eliminates the entire Node.js backend**. No server to deploy or maintain.

---

## What Changes — High Level

| Component | Before (Twilio) | After (Firebase) |
|---|---|---|
| OTP generation | Backend `crypto.randomInt` | Firebase SDK (Google servers) |
| SMS delivery | Backend → Twilio API | Firebase SDK → Google SMS |
| OTP verification | Backend `bcrypt.compare` | Firebase SDK `signInWithCredential` |
| Backend server | Node.js on Railway | **DELETED — not needed** |
| Network calls | Retrofit → your backend | Firebase SDK → Google |
| Cost | ~$0.04/SMS (Twilio) | 10 free/day (Spark), then Blaze pay-as-you-go |

---

## Dependency Map — Files That Touch Twilio/Backend

Verified by grep + file reading. These are ALL files referencing `FocusLockApi`:

| File | What It Does | Migration Action |
|---|---|---|
| `di/NetworkModule.kt` | Provides Retrofit + OkHttp + `FocusLockApi` | **REPLACE** with `FirebaseModule.kt` |
| `data/remote/api/FocusLockApi.kt` | 4 Retrofit endpoints | **DELETE** |
| `data/remote/dto/VerifyContactRequest.kt` | Request/Response DTOs | **DELETE** |
| `data/remote/dto/ConfirmContactRequest.kt` | Request/Response DTOs | **DELETE** |
| `data/remote/dto/RequestUnlockRequest.kt` | Request/Response DTOs | **DELETE** |
| `data/remote/dto/VerifyUnlockOtpRequest.kt` | Request/Response DTOs | **DELETE** |
| `domain/usecase/SaveTrustedContactUseCase.kt` | Calls `api.verifyContact()` | **REWRITE** — use Firebase `PhoneAuthProvider` |
| `domain/usecase/VerifyContactOtpUseCase.kt` | Calls `api.confirmContact()` | **REWRITE** — use Firebase `signInWithCredential` |
| `domain/usecase/RequestUnlockUseCase.kt` | Calls `api.requestUnlock()` | **REWRITE** — use Firebase to send OTP to contact |
| `data/repository/UnlockRepositoryImpl.kt` | Calls `api.verifyUnlockOtp()` | **REWRITE** `verifyOtp()` — use Firebase |
| `app/build.gradle.kts` | Has `BACKEND_URL` BuildConfig | **REMOVE** `BACKEND_URL`, add Firebase deps |
| `backend/` folder | Entire Node.js server | **DELETE** or archive |

### Files That Do NOT Change
- All Room entities, DAOs, database
- All services (VPN, Accessibility, Overlay, DeviceAdmin)
- All UI screens (Compose)
- All ViewModels (they call UseCases, not API directly)
- WorkManager workers
- Navigation graph
- Domain models
- `proguard-rules.pro`, `AndroidManifest.xml` (minor addition only)

---

## Agent Roster

| Agent | Role |
|---|---|
| **Architect Agent** | Validate Firebase replaces all 4 Twilio flows |
| **Domain Agent** | Rewrite 3 UseCases to use Firebase |
| **Data Layer Agent** | Rewrite `UnlockRepositoryImpl.verifyOtp()`, new `FirebaseOtpRepository` |
| **Security Agent** | Verify OTP flow not bypassed, no credentials leaked |
| **Testing Agent** | Update all affected unit tests |
| **Code Reviewer Agent** | Final review |

## Skill Invocation (per 7_RULES.md)

| Phase | Skills |
|---|---|
| Planning | `@brainstorming` → `@concise-planning` (R1) |
| Firebase setup | `@mobile-developer` → `@firebase` (R2) |
| Architecture | `@software-architecture` (R4) |
| Security | `@mobile-security-coder` (R10, E1) |
| Tests | `@test-driven-development` → `@testing-patterns` (R12) |
| Review | `@uncle-bob-craft` → `@production-code-audit` (R14) |

---

## Task Breakdown

### Task 1 — Firebase Project Setup (No Code)

**Steps:**
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create project "FocusLock"
3. Add Android app with package name `com.focuslock`
4. Download `google-services.json` → place in `app/` folder
5. Enable **Phone Authentication** in Firebase Console → Authentication → Sign-in method
6. Add test phone numbers for development (Firebase Console → Auth → Phone → Test numbers)

**Files created:**
- `app/google-services.json` (downloaded)

**Acceptance:**
- [ ] Firebase project exists
- [ ] Phone Auth enabled
- [ ] `google-services.json` in `app/` directory
- [ ] Test phone number added for dev

---

### Task 2 — Update Gradle Dependencies

**File:** `build.gradle.kts` (root)

```diff
 plugins {
+    id("com.google.gms.google-services") version "4.4.2" apply false
 }
```

**File:** `app/build.gradle.kts`

```diff
 plugins {
+    id("com.google.gms.google-services")
 }

 // REMOVE these lines:
-buildConfigField("String", "BACKEND_URL", "\"https://focuslock-backend.railway.app\"")
-buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:3000\"")

 dependencies {
+    // Firebase
+    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
+    implementation("com.google.firebase:firebase-auth-ktx")

     // REMOVE Retrofit + OkHttp (no longer needed):
-    implementation("com.squareup.retrofit2:retrofit:2.9.0")
-    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
-    implementation("com.squareup.okhttp3:okhttp:4.12.0")
-    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
 }
```

> [!NOTE]
> Keep `kotlinx-serialization-json` — Room uses it. Only remove Retrofit/OkHttp.

**Acceptance:**
- [ ] `./gradlew assembleDebug` builds with Firebase deps
- [ ] No Retrofit in dependency tree

---

### Task 3 — Create Firebase OTP Repository

**New file:** `data/repository/FirebaseOtpRepository.kt`

This wraps all Firebase Phone Auth calls behind a clean interface:

```kotlin
interface OtpRepository {
    suspend fun sendOtp(phoneNumber: String, activity: Activity): Result<String>  // Returns verificationId
    suspend fun verifyOtp(verificationId: String, otp: String): Result<Boolean>
    suspend fun sendUnlockOtp(phoneNumber: String, activity: Activity): Result<String>
}
```

**Key difference from Twilio:**
- Firebase needs an `Activity` reference for reCAPTCHA verification
- Firebase returns a `verificationId` that you pair with the OTP code later
- The `verificationId` must be stored temporarily (in ViewModel state or passed through)

**Implementation uses:**
- `PhoneAuthProvider.verifyPhoneNumber()` — triggers SMS
- `PhoneAuthProvider.getCredential(verificationId, otp)` — creates credential
- `FirebaseAuth.signInWithCredential(credential)` — verifies OTP

**Why we DON'T use Firebase sign-in for the app user:**
- FocusLock has no user accounts
- We use Firebase ONLY as an OTP delivery mechanism
- After verification succeeds → immediately `FirebaseAuth.signOut()` so Firebase session doesn't persist

---

### Task 4 — Rewrite SaveTrustedContactUseCase

**File:** `domain/usecase/SaveTrustedContactUseCase.kt`

**Before (Twilio):**
```kotlin
// Calls: api.verifyContact(VerifyContactRequest(phoneNumber = trimmed))
```

**After (Firebase):**
```kotlin
class SaveTrustedContactUseCase @Inject constructor(
    private val trustedContactRepository: TrustedContactRepository,
    private val sessionRepository: SessionRepository,
    private val otpRepository: OtpRepository  // was: FocusLockApi
) {
    suspend operator fun invoke(phoneNumber: String, activity: Activity): SaveContactResult {
        // ... same validation ...
        trustedContactRepository.saveContact(trimmed)
        val result = otpRepository.sendOtp(trimmed, activity)
        return if (result.isSuccess) {
            SaveContactResult.OtpSent(verificationId = result.getOrThrow())
        } else {
            SaveContactResult.Error("Failed to send OTP")
        }
    }
}
```

**Key change:** Returns `verificationId` which the ViewModel must store for Task 5.

---

### Task 5 — Rewrite VerifyContactOtpUseCase

**File:** `domain/usecase/VerifyContactOtpUseCase.kt`

**Before:** `api.confirmContact(ConfirmContactRequest(phoneNumber, otp))`
**After:** `otpRepository.verifyOtp(verificationId, otp)`

```kotlin
class VerifyContactOtpUseCase @Inject constructor(
    private val trustedContactRepository: TrustedContactRepository,
    private val otpRepository: OtpRepository
) {
    suspend operator fun invoke(verificationId: String, otp: String): VerifyOtpResult {
        if (otp.length != 6 || !otp.all { it.isDigit() }) return VerifyOtpResult.InvalidOtp

        val result = otpRepository.verifyOtp(verificationId, otp)
        return if (result.isSuccess && result.getOrThrow()) {
            val contact = trustedContactRepository.getContactSync()
            contact?.let { trustedContactRepository.markVerified(it.id) }
            VerifyOtpResult.Verified
        } else {
            VerifyOtpResult.InvalidOtp
        }
    }
}
```

---

### Task 6 — Rewrite RequestUnlockUseCase

**File:** `domain/usecase/RequestUnlockUseCase.kt`

**Before:** Calls `api.requestUnlock()` — backend sends SMS with context
**After:** Calls `otpRepository.sendUnlockOtp()` — Firebase sends SMS

> [!WARNING]
> **Critical trade-off:** With Twilio, the backend sent a **custom SMS message** containing context (what categories were blocked, time remaining). Firebase Phone Auth sends a **generic OTP SMS** like "123456 is your verification code". The trusted contact will NOT see the context message.

**Mitigation options:**
1. Accept the trade-off — contact gets a plain OTP, user must explain verbally
2. Add a **separate notification** system (e.g., email or push notification) to send context
3. Show context on the user's screen for them to share (screenshot/verbal)

**For MVP:** Option 1 is simplest. The trusted contact gets the OTP code, the user explains context.

---

### Task 7 — Rewrite UnlockRepositoryImpl.verifyOtp()

**File:** `data/repository/UnlockRepositoryImpl.kt`

**Before (lines 85–91):**
```kotlin
override suspend fun verifyOtp(trustedContactNumber: String, otp: String): Boolean {
    val request = VerifyUnlockOtpRequest(trustedContactNumber, otp)
    return api.verifyUnlockOtp(request).valid
}
```

**After:**
```kotlin
override suspend fun verifyOtp(verificationId: String, otp: String): Boolean {
    return otpRepository.verifyOtp(verificationId, otp).getOrDefault(false)
}
```

**Also update interface** `UnlockRepository.kt` — change `verifyOtp` signature from `(trustedContactNumber, otp)` to `(verificationId, otp)`.

---

### Task 8 — Delete Backend + Retrofit Infrastructure

**Files to DELETE:**
```
data/remote/api/FocusLockApi.kt
data/remote/dto/VerifyContactRequest.kt
data/remote/dto/ConfirmContactRequest.kt
data/remote/dto/RequestUnlockRequest.kt
data/remote/dto/VerifyUnlockOtpRequest.kt
di/NetworkModule.kt
```

**Folder to archive/delete:**
```
backend/          (entire folder)
```

**New file:**
```
di/FirebaseModule.kt    (provides OtpRepository via Hilt)
```

---

### Task 9 — Update Proguard Rules

**File:** `app/proguard-rules.pro`

```diff
-# Keep Retrofit interfaces
--keep interface com.focuslock.data.remote.api.** { *; }

+# Keep Firebase Auth
+-keep class com.google.firebase.auth.** { *; }
+-keep class com.google.android.gms.** { *; }
```

---

### Task 10 — Update RepositoryModule

**File:** `di/RepositoryModule.kt`

Remove `FocusLockApi` injection from `UnlockRepositoryImpl`, inject `OtpRepository` instead.

---

### Task 11 — Update Unit Tests

**Tests to update:**
| Test File | Change |
|---|---|
| `SaveTrustedContactUseCaseTest.kt` | Mock `OtpRepository` instead of `FocusLockApi` |
| `VerifyContactOtpUseCaseTest.kt` | Mock `OtpRepository`, pass `verificationId` |
| `RequestUnlockUseCaseTest.kt` | Mock `OtpRepository` |
| `VerifyUnlockOtpUseCaseTest.kt` | Mock `OtpRepository` |

**Tests that do NOT change:** All blocking, session, category, keyword tests.

---

### Task 12 — Update ViewModels (verificationId Pass-Through)

Firebase returns a `verificationId` when OTP is sent. This ID must be stored and passed when verifying.

**ViewModels affected:**
| ViewModel | Change |
|---|---|
| `TrustedContactSetupViewModel` | Store `verificationId` in state after `SaveTrustedContactUseCase` returns it |
| `VerifyContactViewModel` | Pass `verificationId` to `VerifyContactOtpUseCase` |
| `UnlockRequestViewModel` | Store `verificationId` from `RequestUnlockUseCase`, pass to `VerifyUnlockOtpUseCase` |

---

### Task 13 — Security Audit

**Mandatory checks (Rule A3):**
- [ ] User cannot bypass OTP verification to unlock session
- [ ] `verificationId` is not logged or persisted to disk
- [ ] Firebase `signOut()` called after every verification (no lingering auth state)
- [ ] `google-services.json` is in `.gitignore`
- [ ] No phone numbers logged even in debug
- [ ] reCAPTCHA verification works on physical device

---

### Task 14 — Code Review + Lint + Commit

- [ ] No `!!` in modified code
- [ ] No `data.remote` imports in domain layer
- [ ] `gradlew lintDebug` clean
- [ ] `gradlew testDebugUnitTest` all pass
- [ ] `gradlew assembleDebug` builds
- [ ] Commit: `[MIGRATE] Replace Twilio backend with Firebase Phone Auth`

---

## Execution Order

```
T1 (Firebase Setup) → T2 (Gradle) → T3 (OtpRepository)
                                         ↓
              T4 (SaveContact) + T5 (VerifyContact) + T6 (RequestUnlock) + T7 (UnlockRepo)
                                         ↓
                            T8 (Delete old files) + T9 (Proguard) + T10 (DI Module)
                                         ↓
                         T11 (Tests) → T12 (ViewModels) → T13 (Security) → T14 (Review)
```

---

## Risk Matrix

| Risk | Impact | Mitigation |
|---|---|---|
| Firebase needs `Activity` reference in UseCase (architecture violation) | 🔴 High | Pass `Activity` from ViewModel → UseCase, or use `PhoneAuthOptions.Builder` |
| No custom SMS message to contact (loses context) | 🟡 Medium | Show context on user's screen; contact gets plain OTP |
| Firebase 10 SMS/day limit on Spark | 🟡 Medium | Upgrade to Blaze ($0.01-0.06/verification) for production |
| reCAPTCHA fails on some devices | 🟡 Medium | Firebase handles fallback to audio CAPTCHA |
| `google-services.json` accidentally committed | 🔴 High | Add to `.gitignore` immediately |
| Auto-verification intercepts OTP before contact sees it | 🟡 Medium | Disable auto-retrieval — user must manually enter code |

---

## Estimated Effort

| Task | Time |
|---|---|
| T1 — Firebase Console Setup | 15 min |
| T2 — Gradle Dependencies | 15 min |
| T3 — FirebaseOtpRepository | 1 hour |
| T4–T7 — Rewrite 3 UseCases + 1 Repo | 2 hours |
| T8–T10 — Delete old files + DI | 30 min |
| T11 — Update Tests | 1.5 hours |
| T12 — ViewModel Changes | 1 hour |
| T13 — Security Audit | 30 min |
| T14 — Review + Lint | 30 min |
| **Total** | **~7.5 hours** |

---

## Critical Trade-offs to Accept

1. **No context SMS** — Twilio sent "Time remaining: 1h. Blocked: Gambling." Firebase sends "123456 is your code." The trusted contact loses context.
2. **10 SMS/day free** — Production needs Blaze plan ($0.01–0.06 per verification). Still cheaper than Twilio.
3. **Google Play Services required** — Firebase Auth requires Google Play Services on device. Devices without it (Huawei, some custom ROMs) won't work.
4. **Activity reference** — Firebase `verifyPhoneNumber()` needs an `Activity`. This leaks Android framework into domain/UseCase layer. Workaround: wrap in a repository interface.

> [!WARNING]
> Trade-off #1 (no context SMS) is the biggest UX downgrade. The whole point of sending context to the trusted contact was so they could make an informed decision about sharing the OTP. With Firebase, they just get a random 6-digit code with no explanation.
