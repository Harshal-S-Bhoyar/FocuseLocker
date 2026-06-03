# FocusLock — Production Hardening Implementation Plan
**Created:** 2026-06-03
**Deployment Target:** Firebase Cloud Functions (backend) + Google Play (Android)
**Total Issues:** 4 Blockers + 5 High + 4 Medium + 1 Architecture Violation = **14 fixes**

---

## Execution Order

The plan is divided into **4 phases**, ordered by dependency and risk:

| Phase | Scope | Issues Fixed | Estimated Effort |
|---|---|---|---|
| **Phase 1** | Android — Compile Fix + Proguard + DB Safety | B-04, B-02, B-03, H-04, H-05, A-01, A-02, A-04 | ~45 min |
| **Phase 2** | Android — Clean Architecture Fix + Network Safety | CA-01, A-03 | ~30 min |
| **Phase 3** | Backend — Security Hardening + Config Validation | B-01, H-01, H-02, H-03 | ~25 min |
| **Phase 4** | Backend — Firebase Cloud Functions Migration | Deployment | ~30 min |

---

## PHASE 1 — Android: Compile Fix + Proguard + DB Safety

### Fix 1.1 — B-04: TrustedContactSetupScreen Compile Error
**File:** `app/src/main/java/com/focuslock/ui/onboarding/TrustedContactSetupScreen.kt`

**Problem:** Lines 94–100 reference `ImeAction`, `KeyboardActions`, `keyboardController`, and `viewModel` — none are in scope inside the stateless `TrustedContactSetupContent()` composable.

**Solution:** Add missing imports and use the `onSendOtpClicked` lambda that is already a parameter. Remove the dangling `viewModel` reference.

```kotlin
// ADD these imports at the top of the file (after existing imports):
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

// REPLACE lines 92–101 inside TrustedContactSetupContent with:
@OptIn(ExperimentalComposeUiApi::class) // needed for LocalSoftwareKeyboardController
@Composable
private fun TrustedContactSetupContent(
    email: String,
    isLoading: Boolean,
    errorMessage: String?,
    onEmailChanged: (String) -> Unit,
    onSendOtpClicked: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    // ... existing Column code ...

    // Inside OutlinedTextField, replace lines 92-101:
    keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Done
    ),
    keyboardActions = KeyboardActions(
        onDone = {
            keyboardController?.hide()
            onSendOtpClicked()  // use the lambda parameter, NOT viewModel
        }
    ),
```

**Verification:** Project compiles. `TrustedContactSetupScreen` renders correctly in Compose preview.

---

### Fix 1.2 — B-02: Proguard DTO Protection
**File:** `app/proguard-rules.pro`

**Problem:** Network DTOs in `data.remote.dto.**` are not protected from R8 name obfuscation. Release APK serializes wrong JSON keys.

**Solution:** Append these rules to `proguard-rules.pro`:

```proguard
# Keep all network DTOs — R8 must not rename JSON field names
-keep class com.focuslock.data.remote.dto.** { *; }

# Strip debug/verbose log calls in release builds (H-04)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
```

**Verification:** Build release APK with `./gradlew assembleRelease`. Decompile with `jadx` or `apktool` and confirm DTO field names are preserved (`email`, `otp`, `trustedContactEmail`).

---

### Fix 1.3 — B-03: Atomic OTP Attempt Counter
**Files:**
- `app/src/main/java/com/focuslock/data/local/dao/UnlockRequestDao.kt`
- `app/src/main/java/com/focuslock/data/repository/UnlockRepositoryImpl.kt`

**Problem:** `incrementOtpAttempts()` is a read-then-write — race condition on double-tap.

**Solution — DAO (atomic SQL):**

```kotlin
// UnlockRequestDao.kt — REPLACE the existing updateOtpAttempts method:

// OLD (remove):
// @Query("UPDATE unlock_requests SET otp_attempts = :attempts WHERE id = :requestId")
// suspend fun updateOtpAttempts(requestId: Long, attempts: Int)

// NEW (atomic increment in a single SQL statement):
@Query("UPDATE unlock_requests SET otp_attempts = otp_attempts + 1 WHERE id = :requestId")
suspend fun incrementOtpAttempts(requestId: Long)

@Query("SELECT otp_attempts FROM unlock_requests WHERE id = :requestId")
suspend fun getOtpAttempts(requestId: Long): Int
```

**Solution — Repository (simplified):**

```kotlin
// UnlockRepositoryImpl.kt — REPLACE incrementOtpAttempts:

override suspend fun incrementOtpAttempts(requestId: Long): Int {
    unlockRequestDao.incrementOtpAttempts(requestId)
    return unlockRequestDao.getOtpAttempts(requestId)
}
```

**Verification:** Unit test with two concurrent coroutines calling `incrementOtpAttempts` — counter must reach 2, not stay at 1.

---

### Fix 1.4 — H-05: Proper Email Validation
**File:** `app/src/main/java/com/focuslock/ui/onboarding/TrustedContactSetupScreen.kt`

**Problem:** Button enabled condition is `email.length >= 8` which is not a real email check.

**Solution:** Replace line 124:

```kotlin
// OLD:
enabled = !isLoading && email.length >= 8,

// NEW:
enabled = !isLoading && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches(),
```

**Note:** This is client-side UX improvement only. The backend Zod schema remains the authoritative validator.

---

### Fix 1.5 — A-01 + A-02: Database Unique Constraints (Schema Migration v3→v4)
**Files:**
- `app/src/main/java/com/focuslock/data/local/entity/TrustedContactEntity.kt`
- `app/src/main/java/com/focuslock/data/local/FocusDatabase.kt`
- `app/src/main/java/com/focuslock/di/DatabaseModule.kt`

**Problem:** No database-level uniqueness on `trusted_contacts.email` or single-ACTIVE-session guard.

**Solution — Entity annotation:**

```kotlin
// TrustedContactEntity.kt — add unique index:
@Entity(
    tableName = "trusted_contacts",
    indices = [Index(value = ["email"], unique = true)]
)
data class TrustedContactEntity(...)
```

**Solution — Migration:**

```kotlin
// DatabaseModule.kt — add MIGRATION_3_4:
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Unique email constraint
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_trusted_contacts_email ON trusted_contacts(email)"
        )
        // Single-active-session partial unique index
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_sessions_single_active ON sessions(status) WHERE status = 'ACTIVE'"
        )
    }
}
```

**Solution — Database version bump:**

```kotlin
// FocusDatabase.kt:
@Database(
    entities = [...],
    version = 4,  // was 3
    exportSchema = true
)

// DatabaseModule.kt — add migration:
.addMigrations(MIGRATION_2_3, MIGRATION_3_4)
```

**Verification:** Install v3 app → create a contact → upgrade to v4 → confirm data survives migration. Attempt to insert two ACTIVE sessions via `adb shell` — SQLite must reject.

---

### Fix 1.6 — A-04: AtomicReference for Thread-Safe Lists
**File:** `app/src/main/java/com/focuslock/service/accessibility/FocusAccessibilityService.kt`

**Problem:** `@Volatile` List is a JMM data race between IO coroutine (writer) and main thread (reader).

**Solution:**

```kotlin
// REPLACE lines 54-55:
// OLD:
// @Volatile private var activeKeywords: List<String> = emptyList()
// @Volatile private var activeDomains: List<String> = emptyList()

// NEW:
import java.util.concurrent.atomic.AtomicReference

private val activeKeywords = AtomicReference<List<String>>(emptyList())
private val activeDomains = AtomicReference<List<String>>(emptyList())

// Then update all reads to use .get() and writes to use .set():
// In onServiceConnected collectors:
activeDomains.set(getActiveBlockedDomainsUseCase())
activeDomains.set(emptyList())
activeKeywords.set(keywords)

// In handleBrowserEvent:
val result = checkUrlForBlockedContentUseCase(url, activeKeywords.get(), activeDomains.get())
```

---

## PHASE 2 — Android: Clean Architecture Fix + Network Safety

### Fix 2.1 — CA-01: Domain Layer Importing Data Layer (3 UseCases)
**Files:**
- `domain/usecase/SaveTrustedContactUseCase.kt`
- `domain/usecase/VerifyContactOtpUseCase.kt`
- `domain/usecase/RequestUnlockUseCase.kt`

**Problem:** These UseCases import `com.focuslock.data.remote.api.FocusLockApi` and `com.focuslock.data.remote.dto.*` directly — a **Clean Architecture violation**. Domain must never know about Retrofit or DTOs.

**Solution:** Move the API calls into `TrustedContactRepositoryImpl` and `UnlockRepositoryImpl` behind repository interfaces.

**Step 1 — Expand TrustedContactRepository interface:**

```kotlin
// domain/repository/TrustedContactRepository.kt — ADD:
suspend fun sendVerificationOtp(email: String): Boolean
suspend fun confirmOtp(email: String, otp: String): Boolean
```

**Step 2 — Implement in TrustedContactRepositoryImpl:**

```kotlin
// data/repository/TrustedContactRepositoryImpl.kt — ADD:
@Singleton
class TrustedContactRepositoryImpl @Inject constructor(
    private val trustedContactDao: TrustedContactDao,
    private val api: FocusLockApi  // ADD this dependency
) : TrustedContactRepository {

    // ... existing methods ...

    override suspend fun sendVerificationOtp(email: String): Boolean {
        return try {
            api.verifyContact(VerifyContactRequest(email = email))
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun confirmOtp(email: String, otp: String): Boolean {
        return try {
            val response = api.confirmContact(ConfirmContactRequest(email = email, otp = otp))
            response.verified
        } catch (e: Exception) {
            false
        }
    }
}
```

**Step 3 — Refactor SaveTrustedContactUseCase:**

```kotlin
// domain/usecase/SaveTrustedContactUseCase.kt — REPLACE:
class SaveTrustedContactUseCase @Inject constructor(
    private val trustedContactRepository: TrustedContactRepository,
    private val sessionRepository: SessionRepository
    // REMOVED: private val api: FocusLockApi
) {
    suspend operator fun invoke(email: String): SaveContactResult {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            return SaveContactResult.Error("Email cannot be empty")
        }
        if (sessionRepository.hasActiveSession()) {
            return SaveContactResult.Error("Cannot change trusted contact during an active session")
        }
        return try {
            trustedContactRepository.saveContact(trimmed)
            val sent = trustedContactRepository.sendVerificationOtp(trimmed)
            if (sent) SaveContactResult.OtpSent
            else SaveContactResult.Error("Failed to send verification email.")
        } catch (e: java.io.IOException) {
            SaveContactResult.Error("Network error. Check your connection.")
        } catch (e: Exception) {
            SaveContactResult.Error("An unexpected error occurred.")
        }
    }
}
```

**Step 4:** Apply the same pattern to `VerifyContactOtpUseCase` and `RequestUnlockUseCase` — move API calls behind repository methods. **No domain file should import anything from `com.focuslock.data.remote`.**

**Verification:** `grep -r "import com.focuslock.data.remote" app/src/main/java/com/focuslock/domain/` returns zero results.

---

### Fix 2.2 — A-03: Network Error Handling in UnlockRepositoryImpl.verifyOtp()
**Files:**
- `domain/repository/UnlockRepository.kt`
- `data/repository/UnlockRepositoryImpl.kt`

**Problem:** `verifyOtp()` returns raw `Boolean` and throws uncaught `HttpException`/`IOException`.

**Note:** After reviewing `VerifyUnlockOtpUseCase.kt`, the UseCase already wraps `verifyOtp()` in a try-catch for `IOException` and `HttpException` (lines 53–58). Therefore the existing call chain **is safe from crashing**. However, the repository signature `suspend fun verifyOtp(): Boolean` does not communicate that it throws. The correct pattern is to return `Result<Boolean>`:

**Solution — Interface:**

```kotlin
// domain/repository/UnlockRepository.kt — CHANGE:
suspend fun verifyOtp(trustedContactEmail: String, otp: String): Result<Boolean>
```

**Solution — Implementation:**

```kotlin
// data/repository/UnlockRepositoryImpl.kt — CHANGE:
override suspend fun verifyOtp(trustedContactEmail: String, otp: String): Result<Boolean> {
    return try {
        val request = VerifyUnlockOtpRequest(
            trustedContactEmail = trustedContactEmail,
            otp = otp
        )
        val response = api.verifyUnlockOtp(request)
        Result.success(response.valid)
    } catch (e: java.io.IOException) {
        Result.failure(e)
    } catch (e: retrofit2.HttpException) {
        Result.failure(e)
    }
}
```

**Solution — UseCase update:**

```kotlin
// domain/usecase/VerifyUnlockOtpUseCase.kt — CHANGE the verifyOtp call:
val otpResult = unlockRepository.verifyOtp(trustedContactEmail, otp)
val isValid = otpResult.getOrElse {
    return@withContext when (it) {
        is java.io.IOException -> VerifyUnlockResult.Error("Network error. Please try again.")
        is retrofit2.HttpException -> VerifyUnlockResult.Error("Server error (${it.code()}).")
        else -> VerifyUnlockResult.Error("An unexpected error occurred.")
    }
}
```

---

## PHASE 3 — Backend: Security Hardening + Config Validation

### Fix 3.1 — B-01: Rate Limiting on OTP Endpoints
**Files:**
- `backend/package.json` (add dependency)
- `backend/src/app.ts` (apply middleware)

**Step 1 — Install:**

```bash
cd backend
npm install express-rate-limit
npm install -D @types/express-rate-limit
```

**Step 2 — Apply in `app.ts`:**

```typescript
// backend/src/app.ts — ADD after helmet():
import rateLimit from "express-rate-limit";

// OTP generation endpoints: max 3 requests per 10 minutes per IP
const otpGenerationLimiter = rateLimit({
  windowMs: 10 * 60 * 1000,
  max: 3,
  standardHeaders: true,
  legacyHeaders: false,
  message: { success: false, message: "Too many OTP requests. Try again later." },
});

// OTP verification endpoints: max 5 requests per 10 minutes per IP
const otpVerificationLimiter = rateLimit({
  windowMs: 10 * 60 * 1000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  message: { success: false, message: "Too many verification attempts." },
});

// Apply BEFORE route mounting:
app.use("/api/verify-contact", otpGenerationLimiter);
app.use("/api/request-unlock", otpGenerationLimiter);
app.use("/api/confirm-contact", otpVerificationLimiter);
app.use("/api/verify-unlock-otp", otpVerificationLimiter);
```

**Firebase note:** `express-rate-limit` uses in-memory store by default, which works per Cloud Function instance. For multi-instance deployments, use `rate-limit-firestore` or Firebase App Check instead.

---

### Fix 3.2 — H-01: Restrictive CORS
**File:** `backend/src/app.ts`

**Problem:** `app.use(cors())` allows any origin.

**Solution:**

```typescript
// REPLACE app.use(cors()):
app.use(cors({
  origin: false, // Mobile apps don't send Origin headers — block browser callers
  methods: ["POST"],
  allowedHeaders: ["Content-Type"],
}));
```

**Why `origin: false`:** A native Android app using OkHttp does NOT send an `Origin` header. Setting `origin: false` blocks all browser-initiated CORS preflight requests while allowing the mobile app through. This eliminates the entire class of CSRF-from-browser attacks.

---

### Fix 3.3 — H-02: SIGTERM Handling
**File:** `backend/src/index.ts`

**Problem:** Only `SIGINT` is handled. Firebase Cloud Functions and Railway both send `SIGTERM`.

**Solution:**

```typescript
// REPLACE lines 24-30:
const gracefulShutdown = (signal: string) => {
  console.log(`Received ${signal}. Shutting down gracefully...`);
  server.close(() => {
    console.log("Closed out remaining connections.");
    process.exit(0);
  });

  // Force exit after 10 seconds if connections don't close
  setTimeout(() => {
    console.error("Forced exit after timeout.");
    process.exit(1);
  }, 10_000).unref();
};

process.on("SIGINT", () => gracefulShutdown("SIGINT"));
process.on("SIGTERM", () => gracefulShutdown("SIGTERM"));
```

---

### Fix 3.4 — H-03: Fail-Fast Config Validation with Zod
**File:** `backend/src/config/unifiedConfig.ts`

**Problem:** `BREVO_API_KEY || ""` silently creates a broken client.

**Solution — Replace entire file:**

```typescript
import dotenv from "dotenv";
import { z } from "zod";

dotenv.config();

// Validate ALL required environment variables at module load time.
// If any are missing or invalid, the process crashes immediately
// with a clear error message — no silent degradation.
const envSchema = z.object({
  BREVO_API_KEY: z.string().min(1, "BREVO_API_KEY is required"),
  BREVO_SENDER_EMAIL: z.string().email("BREVO_SENDER_EMAIL must be a valid email"),
  BREVO_SENDER_NAME: z.string().default("FocusLock"),
  OTP_EXPIRY_MINUTES: z.coerce.number().int().positive().default(10),
  OTP_MAX_ATTEMPTS: z.coerce.number().int().positive().default(3),
  PORT: z.coerce.number().int().positive().default(3000),
});

// This throws a ZodError at startup if validation fails — crash early, crash loud
const env = envSchema.parse(process.env);

export const config = {
  brevo: {
    apiKey: env.BREVO_API_KEY,
    senderEmail: env.BREVO_SENDER_EMAIL,
    senderName: env.BREVO_SENDER_NAME,
  },
  otp: {
    expiryMinutes: env.OTP_EXPIRY_MINUTES,
    maxAttempts: env.OTP_MAX_ATTEMPTS,
  },
  server: {
    port: env.PORT,
  },
};
```

**Then remove the redundant check in `index.ts`** (lines 4–15) since `unifiedConfig.ts` now validates at import time.

---

## PHASE 4 — Firebase Cloud Functions Deployment

### 4.1 — Initialize Firebase in the backend directory

```bash
cd backend
npx -y firebase-tools init functions
# Select: Use an existing project → your Firebase project
# Language: TypeScript
# Do NOT overwrite existing files
```

### 4.2 — Create the Cloud Function entry point

Create `backend/src/functions.ts`:

```typescript
import { onRequest } from "firebase-functions/v2/https";
import app from "./app";

// Export the Express app as a single Cloud Function
// The function auto-scales, handles SIGTERM, and routes all /api/* paths
export const api = onRequest(
  {
    region: "asia-south1",    // Mumbai — closest to Indian users
    memory: "256MiB",
    timeoutSeconds: 60,
    minInstances: 0,          // Scale to zero when idle (cost optimization)
    maxInstances: 10,         // Prevent runaway scaling
  },
  app
);
```

### 4.3 — Update `package.json` for Firebase

```json
{
  "scripts": {
    "test": "jest",
    "build": "tsc",
    "start": "node dist/index.js",
    "dev": "tsx watch src/index.ts",
    "deploy": "npm run build && firebase deploy --only functions"
  },
  "engines": {
    "node": "20"
  }
}
```

Add to dependencies:
```json
"firebase-admin": "^13.0.0",
"firebase-functions": "^6.0.0"
```

### 4.4 — Set Firebase environment secrets

```bash
firebase functions:secrets:set BREVO_API_KEY
firebase functions:secrets:set BREVO_SENDER_EMAIL
```

### 4.5 — Update `functions.ts` to use Firebase secrets

```typescript
import { defineSecret } from "firebase-functions/params";

const brevoApiKey = defineSecret("BREVO_API_KEY");
const brevoSenderEmail = defineSecret("BREVO_SENDER_EMAIL");

export const api = onRequest(
  {
    region: "asia-south1",
    memory: "256MiB",
    timeoutSeconds: 60,
    secrets: [brevoApiKey, brevoSenderEmail],
  },
  app
);
```

### 4.6 — Update Android `BACKEND_URL` in `build.gradle.kts`

```kotlin
// defaultConfig — REPLACE the production URL:
buildConfigField(
    "String", "BACKEND_URL",
    "\"https://asia-south1-YOUR_PROJECT_ID.cloudfunctions.net/api\""
)
```

### 4.7 — Deploy

```bash
cd backend
npm run deploy
```

### 4.8 — Verify deployment

```bash
curl https://asia-south1-YOUR_PROJECT_ID.cloudfunctions.net/api/health
# Expected: {"status":"ok"}
```

---

## Post-Deployment Verification Checklist

| # | Check | Method | Pass Criteria |
|---|---|---|---|
| 1 | App compiles | `./gradlew assembleDebug` | Exit code 0 |
| 2 | Release APK builds | `./gradlew assembleRelease` | Exit code 0, DTOs not obfuscated |
| 3 | All 21 backend tests pass | `npm test` | Exit code 0, no open handles |
| 4 | Rate limiter works | `curl` 4 rapid POSTs to `/api/verify-contact` | 4th returns 429 |
| 5 | CORS blocks browsers | Open browser console, `fetch()` the endpoint | Blocked by CORS |
| 6 | Config fails on missing key | Remove `BREVO_API_KEY` from `.env`, start server | Process crashes with ZodError |
| 7 | Log.d stripped in release | `adb logcat \| grep FocusAccessibility` on release build | Zero output |
| 8 | Email validation | Type `"aaaaaaa@"` in trusted contact screen | Send button stays disabled |
| 9 | OTP increment is atomic | Double-tap "Verify" rapidly | Counter increments correctly |
| 10 | DB unique constraint | `adb shell` → SQLite → `INSERT` duplicate active session | UNIQUE constraint error |
| 11 | Firebase health check | `curl .../api/health` | `{"status":"ok"}` |
| 12 | OTP email arrives | Register real email, receive OTP | Email received within 30s |
| 13 | Clean Architecture | `grep -r "data.remote" domain/` | Zero results |

---

## Summary — What This Plan Fixes

| ID | Severity | Description | Phase |
|---|---|---|---|
| B-04 | 🔴 Critical | TrustedContactSetupScreen compile error | 1 |
| B-02 | 🔴 Critical | DTOs unprotected from R8 obfuscation | 1 |
| B-03 | 🔴 Critical | Non-atomic OTP attempt counter race condition | 1 |
| B-01 | 🔴 Critical | No rate limiting on OTP generation | 3 |
| H-04 | 🟠 High | Log.d() not stripped in release | 1 |
| H-05 | 🟡 Medium | Weak email validation (length >= 8) | 1 |
| A-01 | 🟡 Medium | No UNIQUE constraint on trusted_contacts.email | 1 |
| A-02 | 🟡 Medium | No DB-level single-ACTIVE-session guard | 1 |
| A-04 | 🟡 Low | @Volatile List → AtomicReference | 1 |
| CA-01 | 🟠 High | Domain layer imports data layer (3 UseCases) | 2 |
| A-03 | 🟡 Medium | verifyOtp() throws raw exceptions | 2 |
| H-01 | 🟠 High | CORS is wildcard open | 3 |
| H-02 | 🟠 High | SIGTERM not handled | 3 |
| H-03 | 🟠 High | BREVO_API_KEY silently empty | 3 |
