# Phase 6 — Integration & Final Polish: Implementation Plan
## FocusLock

---

## Phase 5 Completion Validation

Before starting Phase 6, Phase 5 status was verified:

| Check | Result |
|---|---|
| All 12 Phase 5 implementation tasks | ✅ Complete |
| Zod validation & Error Handling | ✅ Implemented |
| Tests passing (21/21) | ✅ Passed |
| Build step (`tsc`) | ✅ Passed |
| Security constraints (bcrypt, no logs, `randomInt`) | ✅ Audited & Verified |

**Verdict:** Phase 5 is fully implemented, tested, and committed to `main`. Phase 6 can proceed.

---

## Phase 6 Objective

Integrate the newly built Node.js OTP backend with the Android client. Complete the early-unlock user flows and screens, finalize release parameters, and conduct the final End-to-End (E2E) physical device testing.

1. Update the Android `FocusLockApi` endpoint base URL for Production/Railway.
2. Build the Early Unlock UI screens (Request Unlock -> Enter OTP -> Unlock).
3. Connect the Android client to the Node.js backend.
4. Finalize physical device verification for core blockers (VPN, Accessibility).
5. Deploy backend to Railway and finalize Release APK.

---

## Skill Invocation Map (from 7_RULES.md)

Before ANY coding begins, invoke skills in this order per Rule 1:

| Phase | Skills (invoke in order) | Rule |
|---|---|---|
| **Session Start** | `@brainstorming` → `@concise-planning` | R1 |
| **Android UI Construction** | `@android-jetpack-compose-expert` | R2 |
| **Android Integration** | `@dotnet-backend-patterns` (for architecture/DI if needed) | R3 |
| **Testing UI & Flows** | `@android_ui_verification` | R12 |
| **Debugging Intercepts** | `@debugging-toolkit-smart-debug` → `@error-debugging-error-trace` | R13 |
| **Code Review** | `@code-review-excellence` → `@production-code-audit` | R14 |

### Enforcement Rules (Non-Negotiable)

- **E1:** Never write UI logic inside ViewModels. UI must be driven by Compose.
- **E3:** Ensure all API calls have proper `try/catch` and user-friendly error messages.
- **E7:** Device Admin verification must be tested on a physical device, not an emulator.

---

## Task Breakdown — Build Order

### Task 6.1 — Android Client Network Configuration
**Agent:** Android Compose Agent → Backend API Agent  
**Priority:** 🔴 Critical

1. Modify `app/build.gradle.kts` `BACKEND_URL` for `debug` (e.g. your local IP or `http://10.0.2.2:3000`) and `release` (the final Railway URL).
2. Ensure Retrofit handles timeouts gracefully since SMS sending can take 1-3 seconds.

---

### Task 6.2 — Early Unlock UI Flow (US-10, US-11)
**Agent:** Android Compose Agent  
**Skills:** `@android-jetpack-compose-expert`  
**Priority:** 🔴 Critical

1. Build `UnlockRequestScreen` (Trigger unlock, confirm trusted contact, countdown 10m).
2. Build `UnlockVerifyOtpScreen` (Input 6-digit OTP, display error states for "wrong", "expired", "max attempts").
3. Hook these screens into the existing `UnlockViewModel`.

---

### Task 6.3 — End-to-End Manual Testing (Physical Device Gates)
**Agent:** Testing Agent  
**Skills:** `@android_ui_verification`  
**Priority:** 🔴 Critical

Follow the Physical Device Test Guide (see provided steps) to verify:
- VPN Domain Blocking.
- Accessibility Keyword Blocking.
- Reboot Recovery (WorkManager).
- Device Admin Uninstall Prevention.

---

### Task 6.4 — Backend Deployment (Railway)
**Agent:** Architect Agent  
**Priority:** 🟡 High

1. Link GitHub repository to Railway.
2. Inject required environment variables (`TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PHONE_NUMBER`, `PORT=3000`).
3. Verify `/health` endpoint is responding on the public URL.

---

### Task 6.5 — Final Release Build & Signing
**Agent:** Architect Agent  
**Priority:** 🟡 High

1. Provide real Keystore details via CI/CD or local env vars to sign the release APK.
2. Run `./gradlew assembleRelease`.
3. Verify the final APK size remains under 15 MB.

---

## Estimated Effort

| Task | Effort |
|---|---|
| 6.1 Network Configuration | 30 min |
| 6.2 Early Unlock UI Flow | 2 hours |
| 6.3 E2E Physical Device Testing | 1.5 hours |
| 6.4 Backend Deployment | 30 min |
| 6.5 Release Build | 30 min |
| **Total** | **~5 hours** |
