# FocusLock — MVP Finalization Plan
## All 6 Phases Built → These 8 Tasks Remain Before Ship
## Created: 2026-06-02

---

> [!IMPORTANT]
> This plan is based on a **file-by-file audit** of the entire codebase. Every gap listed below was verified by inspecting actual files on disk — no guessing.

---

## Audit Summary — What's Done vs What Remains

### ✅ Confirmed Complete (No Work Needed)
| Item | Evidence |
|---|---|
| `blocked_domains.json` | ✅ EXISTS at `app/src/main/assets/blocked_domains.json` (122 domains, 6 categories) |
| Proguard rules | ✅ COMPLETE at `app/proguard-rules.pro` — covers Room, Retrofit, Serialization, Hilt, Services |
| Backend Procfile | ✅ EXISTS — `web: node dist/index.js` |
| All 19 UseCases | ✅ All files present in `domain/usecase/` |
| All 4 backend endpoints | ✅ `contact.routes.ts` + `unlock.routes.ts` |
| All 4 backend test suites | ✅ `otp.store`, `otp.service`, `contact.routes`, `unlock.routes` |

### ❌ Confirmed Remaining (8 Tasks)

| # | Task | Severity | Evidence |
|---|---|---|---|
| **T1** | OTP Flow Reorder | 🔴 Critical | Plan at `plans/otp_flow_reorder_plan.md` — not applied |
| **T2** | Missing Unit Tests (2 UseCases) | 🔴 Critical | `VerifyUnlockOtpUseCaseTest.kt` + `StartEmergencyBypassUseCaseTest.kt` missing |
| **T3** | App Icon Integration | 🟡 High | Manifest uses `@android:drawable/sym_def_app_icon`. No `mipmap-*` dirs. Icon at `assets/focuslock_icon.png` |
| **T4** | Hardcoded Strings in Session Screens | 🟡 High | 9 hardcoded `text = "..."` in 4 session screen files |
| **T5** | Blocked Domains: ADULT Category | 🟢 Low | 0 ADULT entries in `blocked_domains.json` but ADULT is defined in `CategoryKey` |
| **T6** | Backend Deploy to Railway | 🔴 Critical | Code ready, not deployed |
| **T7** | Production Code Audit | 🟡 High | `VerifyUnlockOtpUseCase` imports `data.remote` (architecture violation) |
| **T8** | End-to-End Device Test | 🔴 Critical | Not done. PRD requires it. |

---

## Task 1 — OTP Flow Reorder

**Problem:** Trusted Contact setup appears after Permissions, should appear after Timer.
**Agents:** Architect Agent → Android Compose Agent → Domain Agent → Security Agent → Testing Agent → Code Reviewer Agent
**Skills:** `@mobile-developer` (R2) → `@software-architecture` (R4) → `@mobile-security-coder` (R10/E1) → `@test-driven-development` (R12)

**Full plan already exists:** [plans/otp_flow_reorder_plan.md](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/plans/otp_flow_reorder_plan.md) — 9 sub-tasks, execute as-is.

**Files to modify:**
- `AppNavigation.kt` — rewire 4 callbacks + route args
- `CheckOnboardingStatusUseCase.kt` — decouple contact from onboarding
- `TimerSetupViewModel.kt` — add `HasVerifiedContactUseCase` check
- `TrustedContactSetupScreen.kt` — accept `durationMillis`
- `VerifyContactScreen.kt` — accept `durationMillis`, change destination
- `PermissionSetupScreen.kt` — rename callback

**New files:** `HasVerifiedContactUseCase.kt` + `HasVerifiedContactUseCaseTest.kt`

**Acceptance:**
- [ ] First-time: Permissions → Categories → Keywords → Timer → TrustedContact → Verify → Confirm
- [ ] Returning: Categories → Keywords → Timer → Confirm (skips contact)
- [ ] Cannot reach SessionConfirmation without verified contact
- [ ] All existing tests pass

---

## Task 2 — Missing Unit Tests

**Problem:** 17/19 UseCase tests exist. Missing: `VerifyUnlockOtpUseCase` and `StartEmergencyBypassUseCase`.
**Agents:** Testing Agent
**Skills:** `@test-driven-development` → `@testing-patterns` → `@kotlin-coroutines-expert` (R12, E5)

### `VerifyUnlockOtpUseCaseTest.kt` — 7 test cases:
1. OTP < 6 digits → `Invalid`
2. OTP has non-digits → `Invalid`
3. Valid OTP + backend valid → `Valid`, status=COOLDOWN, cooldown scheduled
4. Valid OTP + backend invalid + attempts<3 → `Invalid`
5. Valid OTP + backend invalid + attempts=3 → `MaxAttemptsReached`, status=FAILED
6. Attempts already >3 → `MaxAttemptsReached`, no API call
7. Network error → `Error`

### `StartEmergencyBypassUseCaseTest.kt` — 4 test cases:
1. No existing emergency → creates request, status=EMERGENCY, 24h scheduled
2. Emergency already active → `Result.failure`
3. Repository throws → `Result.failure`
4. `endsAt` ≈ now + 24 hours

**Mocks:** `UnlockRepository`, `FocusLockApi`, `UnlockWorkerScheduler`

**Acceptance:** `gradlew testDebugUnitTest` — all 21+ UseCase tests pass.

---

## Task 3 — App Icon Integration

**Problem:** Manifest line 18-20 uses `@android:drawable/sym_def_app_icon`. Icon exists at `assets/focuslock_icon.png`.
**Agents:** Android Compose Agent
**Skills:** `@mobile-developer` (R2)

### Steps:
1. **Generate mipmaps** from `assets/focuslock_icon.png` using Python Pillow:
   - `mipmap-mdpi/ic_launcher.png` → 48×48
   - `mipmap-hdpi/` → 72×72
   - `mipmap-xhdpi/` → 96×96
   - `mipmap-xxhdpi/` → 144×144
   - `mipmap-xxxhdpi/` → 192×192
   - Same for `ic_launcher_round.png`

2. **Update AndroidManifest.xml** lines 18, 20:
   ```diff
   - android:icon="@android:drawable/sym_def_app_icon"
   + android:icon="@mipmap/ic_launcher"
   - android:roundIcon="@android:drawable/sym_def_app_icon"
   + android:roundIcon="@mipmap/ic_launcher_round"
   ```

**Acceptance:** Custom icon visible on launcher. `gradlew assembleDebug` builds clean.

---

## Task 4 — Extract Hardcoded Strings

**Problem:** 9 hardcoded `text = "..."` in session screens. Rule B1 violation.
**Agents:** Android Compose Agent → Code Reviewer Agent
**Skills:** `@mobile-developer` (R2) → `@uncle-bob-craft` → `@production-code-audit` (R14)

| File | Line | String | Resource Name |
|---|---|---|---|
| `UnlockRequestScreen.kt` | 58 | "Are you sure you want to unlock early?" | `unlock_request_title` |
| `UnlockRequestScreen.kt` | 66 | "Ending your session early requires..." | `unlock_request_description` |
| `UnlockCooldownScreen.kt` | 50 | "Unlock Approved" | `unlock_cooldown_title` |
| `UnlockCooldownScreen.kt` | 58 | "A 10-minute cooldown period..." | `unlock_cooldown_description` |
| `UnlockCooldownScreen.kt` | 73 | "Unlocks at: $expiryTimeStr" | `unlock_cooldown_unlocks_at` |
| `OtpEntryScreen.kt` | 57 | "We sent a code to your trusted contact." | `otp_entry_sent_message` |
| `OtpEntryScreen.kt` | 65 | "Enter the 6-digit code below..." | `otp_entry_instruction` |
| `EmergencyBypassScreen.kt` | 59 | "Emergency bypass allows you to..." | `emergency_bypass_description` |
| `EmergencyBypassScreen.kt` | 83 | "Unlocks at: $expiryTimeStr" | `emergency_bypass_unlocks_at` |

**Acceptance:** Zero hardcoded strings in session screens. All 9 in `strings.xml`.

---

## Task 5 — Add ADULT Category Domains

**Problem:** `blocked_domains.json` has 0 ADULT entries. `CategoryKey.ADULT` is defined.
**Agents:** Data Layer Agent → Security Agent
**Skills:** `@database-design` (R5) → `@mobile-security-coder` (R10)

**Action:** Add 20+ adult content domains to `blocked_domains.json` (both `domain.com` and `www.domain.com` variants).

**Acceptance:** All 6 `CategoryKey` values have entries. VPN blocks ADULT domains when enabled.

---

## Task 6 — Backend Deploy to Railway

**Problem:** Code complete, not deployed.
**Agents:** Backend API Agent → Security Agent
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` (R6) → `@api-security-best-practices` (R9)

### Steps:
1. `npm test` — all 4 suites pass
2. `npm run build` — compiles to `dist/`
3. Railway setup: connect GitHub, set root=`backend/`, add env vars (Twilio creds)
4. Deploy and verify: `curl -X POST https://focuslock-backend.railway.app/verify-contact`
5. Verify `app/build.gradle.kts` line 33 URL matches Railway URL

**Acceptance:** All 4 endpoints respond. Twilio SMS works. Android release build connects.

---

## Task 7 — Production Code Audit

**Problem:** Architecture violation in `VerifyUnlockOtpUseCase` (imports `data.remote`). Rule E6.
**Agents:** Code Reviewer Agent → Security Agent
**Skills:** `@uncle-bob-craft` → `@production-code-audit` (R14) → `@lint-and-validate` (R15)

### 7.1 — Fix Architecture Violation
| File | Issue | Fix |
|---|---|---|
| `VerifyUnlockOtpUseCase.kt` L3 | Imports `data.remote.api.FocusLockApi` | Add `verifyOtp()` to `UnlockRepository` interface; move API call to `UnlockRepositoryImpl` |
| `VerifyUnlockOtpUseCase.kt` L4 | Imports `data.remote.dto` | DTO stays in data layer |

### 7.2 — Security: grep for logged sensitive data
### 7.3 — No `!!`, no `runBlocking`, no `TODO()`
### 7.4 — `gradlew lintDebug` passes clean

**Acceptance:** Domain layer has zero `data.remote` imports. Lint clean.

---

## Task 8 — End-to-End Device Test

**Problem:** PRD requires physical device testing. VPN/Accessibility/DeviceAdmin don't work on emulator.
**Agents:** Testing Agent → Security Agent → Code Reviewer Agent
**Skills:** `@systematic-debugging` (R13) → `@mobile-developer` (R2)
**Prerequisites:** Tasks 1–7 complete.

| # | Test | Criteria | US |
|---|---|---|---|
| 1 | Fresh install + permissions | All 4 cards ✅ | US-01 |
| 2 | Select categories + keywords | Toggle works, keywords saved | US-04/05 |
| 3 | Set 15-min timer | Accepted, shown on confirm | US-06 |
| 4 | Register + verify contact | SMS received, OTP verified | US-02/03 |
| 5 | Lock In → session starts | Countdown visible | US-08 |
| 6 | Search blocked keyword in Chrome | Overlay appears | US-07 |
| 7 | Visit blocked domain | DNS blocked | US-07 |
| 8 | Try uninstall | Device Admin blocks | US-09 |
| 9 | Go to Accessibility Settings | Overlay covers screen | US-09 |
| 10 | Request early unlock | SMS to contact | US-10 |
| 11 | Enter correct OTP | 10-min cooldown | US-11 |
| 12 | Wrong OTP ×3 | Max attempts | US-11 |
| 13 | Reboot during session | Session resumes | US-13 |
| 14 | Emergency bypass | 24h countdown | US-12 |
| 15 | Timer expires | Session ends cleanly | US-08 |

**Acceptance:** All 15 pass. No crashes. No sensitive data in logcat.

---

## Execution Order

```
Track A (parallel):     T3 (Icon) | T4 (Strings) | T5 (Domains)
Track B (sequential):   T1 (OTP Reorder) → T2 (Tests)
Merge:                  T7 (Audit) → T6 (Deploy) → T8 (E2E)
```

## Effort: ~10.5 hours total
