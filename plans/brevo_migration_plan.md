# Twilio → Brevo Email Migration Plan
## FocusLock — OTP Provider Migration
## Created: 2026-06-02

---

> [!IMPORTANT]
> This is the **recommended** migration. Brevo keeps the backend, preserves context messages, and gives 300 free emails/day.

---

## Why Brevo Wins Over Firebase

| Factor | Firebase Phone Auth | Brevo Email |
|---|---|---|
| Free tier | 10 SMS/day | **300 emails/day** |
| Custom message with context | ❌ Generic OTP only | ✅ Full HTML email with context |
| Backend needed | No (deleted) | Yes (kept — 1 file swap) |
| Android SDK changes | Heavy (new SDK + Activity ref) | **None** |
| Google Play Services | Required | Not required |
| Architecture violation | Activity leaks into domain | **None** |
| Code effort | ~7.5 hours (rewrite 6+ files) | **~4 hours (swap 1 backend file + rename fields)** |

---

## What Changes — High Level

| Layer | What Changes | Effort |
|---|---|---|
| **Backend** | Replace `sms.service.ts` → `email.service.ts` (Brevo SDK) | 1 file swap |
| **Backend** | Update config: Twilio vars → Brevo API key | 1 file edit |
| **Backend** | Update schemas: `phoneNumber` → `email` in contact routes | 1 file edit |
| **Backend** | Update routes: field names `phoneNumber` → `email` | 2 file edits |
| **Android DTOs** | Rename `phoneNumber` → `email`, `trustedContactNumber` → `trustedContactEmail` | 4 file edits |
| **Android Entity** | Add `email` column, Room migration | 2 file edits |
| **Android Domain** | Update model + repo interface + 3 UseCases | 6 file edits |
| **Android UI** | Phone input → Email input, labels, keyboard type | 4 file edits |
| **Android Tests** | Update mocks to use email | 4 file edits |

**What does NOT change:** Retrofit, OkHttp, NetworkModule, FocusLockApi interface (same 4 endpoints), OTP service logic, all blocking logic, VPN, Accessibility, DeviceAdmin.

---

## Complete Dependency Map

### Backend Files (6 files touched)

| File | Action | Detail |
|---|---|---|
| `services/sms.service.ts` | **REPLACE** → `services/email.service.ts` | Swap Twilio SDK for Brevo `@getbrevo/brevo` |
| `config/unifiedConfig.ts` | **EDIT** | Remove `twilio` block, add `brevo: { apiKey, senderEmail }` |
| `schemas/request.schemas.ts` | **EDIT** | Change `phoneNumber` → `email` (use `z.string().email()`) |
| `routes/contact.routes.ts` | **EDIT** | Rename destructured `phoneNumber` → `email` |
| `routes/unlock.routes.ts` | **EDIT** | Rename `trustedContactNumber` → `trustedContactEmail` |
| `.env` / `.env.example` | **EDIT** | Remove `TWILIO_*`, add `BREVO_API_KEY`, `BREVO_SENDER_EMAIL` |

### Android Files (20 files touched)

| File | Action | Detail |
|---|---|---|
| **DTOs (4)** | | |
| `dto/VerifyContactRequest.kt` | EDIT | `phoneNumber` → `email` |
| `dto/ConfirmContactRequest.kt` | EDIT | `phoneNumber` → `email` |
| `dto/RequestUnlockRequest.kt` | EDIT | `trustedContactNumber` → `trustedContactEmail` |
| `dto/VerifyUnlockOtpRequest.kt` | EDIT | `trustedContactNumber` → `trustedContactEmail` |
| **Entity + DAO + Migration (3)** | | |
| `entity/TrustedContactEntity.kt` | EDIT | Add `email` column, keep `phoneNumber` optional |
| `dao/TrustedContactDao.kt` | EDIT | No structural change (queries by `id`, not by phone) |
| `local/FocusLockDatabase.kt` | EDIT | Bump version, add migration |
| **Domain Models (1)** | | |
| `model/TrustedContact.kt` | EDIT | Add `email: String`, make `phoneNumber` optional |
| **Domain Repository Interfaces (2)** | | |
| `repository/TrustedContactRepository.kt` | EDIT | `saveContact(email: String)` |
| `repository/UnlockRepository.kt` | EDIT | `verifyOtp(email, otp)` |
| **Domain UseCases (3)** | | |
| `usecase/SaveTrustedContactUseCase.kt` | EDIT | Validate email, pass email to API |
| `usecase/VerifyContactOtpUseCase.kt` | EDIT | Pass `email` not `phoneNumber` |
| `usecase/RequestUnlockUseCase.kt` | EDIT | Pass `trustedContactEmail` |
| **Data Repository Impl (2)** | | |
| `repository/TrustedContactRepositoryImpl.kt` | EDIT | Map `email` field |
| `repository/UnlockRepositoryImpl.kt` | EDIT | Pass `email` to DTO |
| **UI (4)** | | |
| `onboarding/TrustedContactSetupScreen.kt` | EDIT | Email input field + keyboard |
| `onboarding/TrustedContactSetupViewModel.kt` | EDIT | Rename state field |
| `onboarding/VerifyContactViewModel.kt` | EDIT | Pass email |
| `session/UnlockViewModel.kt` | EDIT | Pass email |
| **strings.xml (1)** | EDIT | Update labels |

---

## Agent Roster

| Agent | Tasks |
|---|---|
| **Architect Agent** | T1 — Validate migration plan |
| **Backend API Agent** | T2–T5 — All backend changes |
| **Data Layer Agent** | T6–T8 — Entity, DAO, migration, repo impls |
| **Domain Agent** | T9–T11 — Model, interfaces, UseCases |
| **Android Compose Agent** | T12 — UI screen updates |
| **Security Agent** | T13 — Audit email not logged, Brevo key safe |
| **Testing Agent** | T14 — Update all unit tests |
| **Code Reviewer Agent** | T15 — Final review |

## Skills (per 7_RULES.md)

| Phase | Skill |
|---|---|
| Architecture | `@software-architecture` (R4) |
| Backend | `@dotnet-backend` adapted for Node.js (R7) |
| Database | `@database-migrations-sql-migrations` (R9) |
| Security | `@mobile-security-coder` (R10) |
| Tests | `@test-driven-development` (R12) |
| Review | `@production-code-audit` (R14) |

---

## Task Breakdown

### T1 — Architecture Validation
**Agent:** Architect Agent
**Action:** Confirm Brevo swap is a 1:1 replacement for Twilio at the delivery layer. No structural changes to backend or Android API contract beyond field renames.

**Acceptance:**
- [ ] All 4 endpoints remain identical in structure
- [ ] Only `sms.service.ts` is replaced, `otp.service.ts` is untouched
- [ ] Field renames documented

---

### T2 — Replace SMS Service with Email Service

**DELETE:** `backend/src/services/sms.service.ts`
**CREATE:** `backend/src/services/email.service.ts`

```typescript
// Uses @getbrevo/brevo SDK
// sendVerificationOtp(to: email, otp) → sends verification email
// sendUnlockOtp(to: email, otp, context) → sends CONTEXT-RICH email
//   Subject: "FocusLock: Unlock Request"
//   Body: "Someone wants to end their session early.
//          Time remaining: {context.timeRemaining}
//          Blocked: {context.categories}
//          OTP: {otp}
//          Share only if you think it's justified."
```

> [!TIP]
> This is the **key advantage** over Firebase — the trusted contact gets the full context email explaining WHY they're receiving the OTP.

**Acceptance:**
- [ ] Email sends via Brevo API
- [ ] Unlock email contains time remaining + blocked categories
- [ ] No SMS/Twilio imports remain

---

### T3 — Update Backend Config

**File:** `backend/src/config/unifiedConfig.ts`

```diff
 export const config = {
-  twilio: {
-    accountSid: process.env.TWILIO_ACCOUNT_SID || "",
-    authToken: process.env.TWILIO_AUTH_TOKEN || "",
-    phoneNumber: process.env.TWILIO_PHONE_NUMBER || "",
-  },
+  brevo: {
+    apiKey: process.env.BREVO_API_KEY || "",
+    senderEmail: process.env.BREVO_SENDER_EMAIL || "noreply@focuslock.app",
+    senderName: process.env.BREVO_SENDER_NAME || "FocusLock",
+  },
   otp: { ... },  // UNCHANGED
   server: { ... },  // UNCHANGED
 };
```

**File:** `backend/.env.example`
```diff
-TWILIO_ACCOUNT_SID=your_account_sid
-TWILIO_AUTH_TOKEN=your_auth_token
-TWILIO_PHONE_NUMBER=+1234567890
+BREVO_API_KEY=your_brevo_api_key
+BREVO_SENDER_EMAIL=noreply@focuslock.app
+BREVO_SENDER_NAME=FocusLock
```

**Acceptance:**
- [ ] No Twilio env vars remain
- [ ] Brevo config loads correctly

---

### T4 — Update Backend Schemas (phone → email)

**File:** `backend/src/schemas/request.schemas.ts`

```diff
-const e164PhoneRegex = /^\+[1-9]\d{1,14}$/;
 
 export const verifyContactSchema = z.object({
   body: z.object({
-    phoneNumber: z.string().regex(e164PhoneRegex, "Invalid E.164 phone number"),
+    email: z.string().email("Invalid email address"),
   }),
 });

 export const confirmContactSchema = z.object({
   body: z.object({
-    phoneNumber: z.string().regex(e164PhoneRegex),
+    email: z.string().email("Invalid email address"),
     otp: z.string().regex(otpRegex, "OTP must be exactly 6 digits"),
   }),
 });

 export const requestUnlockSchema = z.object({
   body: z.object({
-    trustedContactNumber: z.string().regex(e164PhoneRegex),
+    trustedContactEmail: z.string().email("Invalid email address"),
     blockedCategories: z.array(z.string()),
     ...
   }),
 });

 export const verifyUnlockOtpSchema = z.object({
   body: z.object({
-    trustedContactNumber: z.string().regex(e164PhoneRegex),
+    trustedContactEmail: z.string().email("Invalid email address"),
     otp: z.string().regex(otpRegex),
   }),
 });
```

---

### T5 — Update Backend Routes (field renames)

**File:** `backend/src/routes/contact.routes.ts` — rename `phoneNumber` → `email`
**File:** `backend/src/routes/unlock.routes.ts` — rename `trustedContactNumber` → `trustedContactEmail`

Same logic, same flow, just different field names. Import `emailService` instead of `smsService`.

**Acceptance:**
- [ ] `npm run build` — no TypeScript errors
- [ ] `npm test` — OTP store tests still pass (they don't touch SMS)
- [ ] Manual test: POST `/api/verify-contact` with `{ email: "test@example.com" }` sends email

---

### T6 — Update Android DTOs (field renames)

4 files, simple `@SerialName` changes:

| File | Change |
|---|---|
| `VerifyContactRequest.kt` | `phoneNumber` → `email` |
| `ConfirmContactRequest.kt` | `phoneNumber` → `email` |
| `RequestUnlockRequest.kt` | `trustedContactNumber` → `trustedContactEmail` |
| `VerifyUnlockOtpRequest.kt` | `trustedContactNumber` → `trustedContactEmail` |

---

### T7 — Update Android Entity + Domain Model + Room Migration

**File:** `entity/TrustedContactEntity.kt`
```diff
 data class TrustedContactEntity(
     @PrimaryKey(autoGenerate = true) val id: Long = 0,
-    @ColumnInfo(name = "phone_number") val phoneNumber: String,
+    @ColumnInfo(name = "email") val email: String,
     @ColumnInfo(name = "is_verified") val isVerified: Boolean = false,
     ...
 )
```

**File:** `model/TrustedContact.kt`
```diff
 data class TrustedContact(
     val id: Long,
-    val phoneNumber: String,
+    val email: String,
     val isVerified: Boolean,
     ...
 )
```

**Room Migration (in `FocusLockDatabase.kt`):**
```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trusted_contacts ADD COLUMN email TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE trusted_contacts SET email = phone_number")
        // Keep phone_number column for backward compat, or drop if fresh install
    }
}
```

> [!NOTE]
> For a pre-release app with no users, a **destructive migration** (`fallbackToDestructiveMigration()`) is simpler than a proper migration. Choose based on whether anyone has installed the app yet.

---

### T8 — Update Repository Interfaces + Implementations

**File:** `repository/TrustedContactRepository.kt`
```diff
-suspend fun saveContact(phoneNumber: String): Long
+suspend fun saveContact(email: String): Long
```

**File:** `repository/UnlockRepository.kt`
```diff
-suspend fun verifyOtp(trustedContactNumber: String, otp: String): Boolean
+suspend fun verifyOtp(trustedContactEmail: String, otp: String): Boolean
```

**File:** `repository/TrustedContactRepositoryImpl.kt` — update `saveContact` to set `email` field
**File:** `repository/UnlockRepositoryImpl.kt` — update DTO field name in `verifyOtp`

---

### T9 — Update UseCases (3 files)

**`SaveTrustedContactUseCase.kt`:**
```diff
-suspend operator fun invoke(phoneNumber: String): SaveContactResult {
-    val trimmed = phoneNumber.trim()
-    if (trimmed.isBlank()) return SaveContactResult.Error("Phone number cannot be empty")
+suspend operator fun invoke(email: String): SaveContactResult {
+    val trimmed = email.trim().lowercase()
+    if (trimmed.isBlank()) return SaveContactResult.Error("Email cannot be empty")
+    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
+        return SaveContactResult.Error("Please enter a valid email address")
+    }
     trustedContactRepository.saveContact(trimmed)
-    api.verifyContact(VerifyContactRequest(phoneNumber = trimmed))
+    api.verifyContact(VerifyContactRequest(email = trimmed))
     SaveContactResult.OtpSent
```

**`VerifyContactOtpUseCase.kt`:**
```diff
-suspend operator fun invoke(phoneNumber: String, otp: String): VerifyOtpResult {
+suspend operator fun invoke(email: String, otp: String): VerifyOtpResult {
     ...
-    api.confirmContact(ConfirmContactRequest(phoneNumber = phoneNumber, otp = otp))
+    api.confirmContact(ConfirmContactRequest(email = email, otp = otp))
```

**`RequestUnlockUseCase.kt`:**
```diff
-trustedContactNumber: String,
+trustedContactEmail: String,
 ...
-val request = RequestUnlockRequest(trustedContactNumber = trustedContactNumber, ...)
+val request = RequestUnlockRequest(trustedContactEmail = trustedContactEmail, ...)
```

> [!IMPORTANT]
> **Architecture fix included:** `RequestUnlockUseCase` currently imports `FocusLockApi` directly (violation of Clean Architecture). During this migration, move the API call into `UnlockRepositoryImpl` where it belongs.

---

### T10 — Update UI Screens (4 files)

**`TrustedContactSetupScreen.kt`:**
- Change input label: "Phone Number" → "Email Address"
- Change keyboard type: `KeyboardType.Phone` → `KeyboardType.Email`
- Change placeholder: "+1 234 567 8900" → "contact@email.com"
- Change helper text to explain email-based OTP

**`TrustedContactSetupViewModel.kt`:** Rename `phoneNumber` state → `email`
**`VerifyContactViewModel.kt`:** Pass `email` to UseCase
**`UnlockViewModel.kt`:** Pass `email` to UseCase

**`strings.xml`:**
```diff
-<string name="trusted_contact_hint">Enter phone number</string>
+<string name="trusted_contact_hint">Enter email address</string>
-<string name="otp_sent_message">OTP sent to your trusted contact\'s phone</string>
+<string name="otp_sent_message">OTP sent to your trusted contact\'s email</string>
```

---

### T11 — Update Backend Dependencies

**File:** `backend/package.json`

```diff
-"twilio": "^4.x.x",
+"@getbrevo/brevo": "^2.x.x",
```

Run: `cd backend && npm uninstall twilio && npm install @getbrevo/brevo`

---

### T12 — Brevo Account Setup (No Code)

1. Sign up at [brevo.com](https://www.brevo.com) (free)
2. Get API key from Settings → SMTP & API → API Keys
3. Verify sender email (or use default)
4. Set env vars: `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`

**Free tier:** 300 emails/day. More than enough for MVP.

---

### T13 — Security Audit

**Agent:** Security Agent

- [ ] Email addresses not logged in production (debug OK)
- [ ] `BREVO_API_KEY` only in `.env`, never in source code
- [ ] `.env` is in `.gitignore`
- [ ] OTP hashing unchanged (`bcrypt` in `otp.service.ts`)
- [ ] OTP expiry unchanged (10 min TTL)
- [ ] Max attempts unchanged (3)
- [ ] No email addresses in Android logs
- [ ] Backend HTTPS enforced in production

---

### T14 — Update Unit Tests

| Test File | Change |
|---|---|
| `SaveTrustedContactUseCaseTest` | Use `"test@email.com"` instead of `"+1234567890"` |
| `VerifyContactOtpUseCaseTest` | Pass `email` parameter |
| `RequestUnlockUseCaseTest` | Rename `trustedContactNumber` → `trustedContactEmail` |
| `VerifyUnlockOtpUseCaseTest` | Rename parameter |
| Backend `otp.store.test.ts` | Change phone keys to email keys |

---

### T15 — Code Review + Lint + Build

- [ ] No `!!` in modified code
- [ ] No `data.remote` imports in domain layer (fix the existing violation)
- [ ] `gradlew lintDebug` clean
- [ ] `gradlew testDebugUnitTest` all pass
- [ ] `npm run build` (backend) clean
- [ ] `npm test` (backend) all pass
- [ ] Commit: `[MIGRATE] Replace Twilio SMS with Brevo Email for OTP delivery`

---

## Execution Order

```
T1 (Architect validates) → T12 (Brevo account setup)
         ↓
T2 (email.service.ts) → T3 (config) → T4 (schemas) → T5 (routes) → T11 (npm deps)
         ↓
T6 (Android DTOs) → T7 (Entity + Migration) → T8 (Repos) → T9 (UseCases) → T10 (UI)
         ↓
T13 (Security) → T14 (Tests) → T15 (Review)
```

---

## Risk Matrix

| Risk | Impact | Mitigation |
|---|---|---|
| Email lands in spam | 🟡 Medium | Verify sender domain via Brevo DNS settings |
| User types wrong email | 🟡 Medium | Email format validation on both client + server |
| Brevo free tier limit (300/day) | 🟢 Low | 300/day is ~10x what an individual needs |
| Email slower than SMS (~30s vs ~5s) | 🟢 Low | Acceptable for OTP; add "check your email" text |
| Room migration loses data | 🟢 Low | Pre-release app — use destructive migration |
| Contact doesn't check email | 🟡 Medium | UX copy: "Ask your contact to check email including spam" |

---

## Estimated Effort

| Task | Time |
|---|---|
| T1 — Architecture validation | 15 min |
| T2–T5, T11 — Backend changes | 1.5 hours |
| T6–T9 — Android data/domain layer | 2 hours |
| T10 — UI changes | 30 min |
| T12 — Brevo account | 10 min |
| T13 — Security audit | 20 min |
| T14 — Tests | 1 hour |
| T15 — Review + lint | 20 min |
| **Total** | **~6 hours** |

---

## Brevo vs Firebase — Final Comparison

| | Firebase | Brevo |
|---|---|---|
| Free limit | 10 SMS/day | **300 emails/day** |
| Context message preserved | ❌ | **✅** |
| Backend server needed | No | Yes (kept) |
| Files modified | ~12 (heavy rewrites) | ~20 (light renames) |
| New SDK on Android | Firebase Auth | None |
| Architecture violations | Activity in domain | **None** |
| Code effort | ~7.5 hours | **~6 hours** |
| Hosting cost | None | Railway free tier |
| **Recommendation** | ❌ | **✅ Winner** |
