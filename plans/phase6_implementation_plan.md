# Phase 6 — Unlock Flow: Implementation Plan
## FocusLock

---

## Phase 5 Completion Validation

Before starting Phase 6, Phase 5 status was verified:

| Check | Result |
|---|---|
| All 12 Phase 5 implementation tasks (5.1–5.12) | ✅ Complete |
| Backend tests (21/21 passing) | ✅ Passed |
| Zod validation on all 4 endpoints | ✅ Implemented |
| OTP security (bcrypt ≥10, `crypto.randomInt`, no plain storage) | ✅ Audited |
| Build step (`tsc`) clean | ✅ Passed |
| Procfile for Railway deployment | ✅ Created |
| Git committed to `main` | ✅ Committed |

**Verdict:** Phase 5 is fully implemented, tested, and committed. Phase 6 can proceed.

---

## Phase 6 Objective

Build the complete **early unlock flow** on Android — the most complex user journey in FocusLock. This phase replaces all 4 placeholder screens in `AppNavigation.kt` with real implementations and connects the Android client to the Phase 5 backend.

Per `Master.md` Phase 6 definition (lines 360–377):

1. Create `RequestUnlockUseCase`, `VerifyUnlockOtpUseCase`, `StartEmergencyBypassUseCase`
2. Build the Early Unlock Request screen (US-10)
3. Build the OTP Entry screen (US-11)
4. Build the 10-minute Cooldown screen (US-11)
5. Build the Emergency 24-hour Bypass flow (US-12)
6. Finalize Session Complete screen (US-14) — already exists from Phase 4 

---

## What Already Exists (from Phases 1–5)

| Component | Location | Status |
|---|---|---|
| Navigation routes: `UnlockRequest`, `OtpEntry`, `UnlockCooldown`, `EmergencyBypass` | `ui/navigation/AppNavigation.kt` (lines 32–36) | ✅ Route defined, ❌ Still placeholder |
| `PlaceholderScreen` used for all 4 unlock routes | `ui/navigation/PlaceholderScreen.kt` | ❌ Must be replaced |
| `UnlockRepository` interface (6 methods) | `domain/repository/UnlockRepository.kt` | ✅ Complete |
| `UnlockRepositoryImpl` (Retrofit + Room) | `data/repository/UnlockRepositoryImpl.kt` | ✅ Complete |
| `UnlockRequestDao` + `UnlockRequestEntity` | `data/local/dao/`, `data/local/entity/` | ✅ Complete |
| `FocusLockApi` (4 backend endpoints) | `data/remote/api/FocusLockApi.kt` | ✅ Complete |
| DTO classes (all 4 request/response pairs) | `data/remote/dto/` | ✅ Complete |
| `NetworkModule` (Hilt, OkHttp, Retrofit) | `di/NetworkModule.kt` | ✅ Complete |
| `BuildConfig.BACKEND_URL` (debug + release) | `app/build.gradle.kts` | ✅ Configured |
| Backend OTP server (all 4 endpoints running) | `backend/` | ✅ Phase 5 complete |
| `SessionCompleteScreen` | `ui/session/SessionCompleteScreen.kt` | ✅ Real screen (Phase 4) |
| `ActiveSessionScreen` with `onNavigateToUnlockRequest` | `ui/session/ActiveSessionScreen.kt` | ✅ Navigation callback exists |

## What Must Be Built

| Component | Location | Status |
|---|---|---|
| `RequestUnlockUseCase` | `domain/usecase/` | ❌ Not created |
| `VerifyUnlockOtpUseCase` | `domain/usecase/` | ❌ Not created |
| `StartEmergencyBypassUseCase` | `domain/usecase/` | ❌ Not created |
| `UnlockViewModel` | `ui/session/` | ❌ Not created |
| `UnlockRequestScreen` (Compose) | `ui/session/` | ❌ Placeholder |
| `OtpEntryScreen` (Compose) | `ui/session/` | ❌ Placeholder |
| `UnlockCooldownScreen` (Compose) | `ui/session/` | ❌ Placeholder |
| `EmergencyBypassScreen` (Compose) | `ui/session/` | ❌ Placeholder |
| `CooldownTimerWorker` (10-min countdown) | `worker/` | ❌ Not created |
| `EmergencyBypassWorker` (24-hour countdown) | `worker/` | ❌ Not created |
| Navigation wiring (replace placeholders) | `ui/navigation/AppNavigation.kt` | ❌ Still placeholder |
| Unit tests for all 3 UseCases + ViewModel | `src/test/java/` | ❌ Not created |

---

## Agent Roster (from Master.md lines 360–362)

**Phase 6 Agent Assignment:**

| Agent | Role | Mandatory? |
|---|---|---|
| **Architect Agent** | Phase plan validation, task breakdown, folder placement | ✅ First (Rule A1) |
| **Domain Agent** | `RequestUnlockUseCase`, `VerifyUnlockOtpUseCase`, `StartEmergencyBypassUseCase` | ✅ Primary |
| **Android Compose Agent** | All 4 unlock screens | ✅ Primary |
| **WorkManager Agent** | `CooldownTimerWorker`, `EmergencyBypassWorker` | ✅ Primary |
| **Security Agent** | OTP entry, phone numbers, session unlock boundary | ✅ Mandatory review (Rule A3) |
| **Testing Agent** | Unit tests for UseCases + ViewModel | ✅ Mandatory (Rule A5) |
| **Code Reviewer Agent** | Final review before phase marked done | ✅ Mandatory (Rule A4) |

---

## Skill Invocation Map (from 7_RULES.md — exact matches)

Before ANY coding begins, invoke skills in this order per the matching rules:

| Task Context | Skills (invoke in order) | Rule |
|---|---|---|
| **Start of Phase 6** | `@brainstorming` → `@concise-planning` | R1 |
| **All Android Components** (screens, ViewModel, services, workers) | `@mobile-developer` | R2 |
| **Kotlin Coroutines / Flow** (suspend funs, StateFlow in ViewModel, Flow tests) | `@kotlin-coroutines-expert` | R3 |
| **Architecture Decisions** (where logic lives: UseCase vs ViewModel vs Worker) | `@software-architecture` → `@architecture` | R4 |
| **API Calls** (Retrofit endpoints for unlock, OTP verify) | `@api-design-principles` → `@api-patterns` | R7 |
| **OTP Security** (OTP entry validation, phone number handling, session unlock) | `@api-security-best-practices` → `@auth-implementation-patterns` → `@backend-security-coder` | R9 (E2 mandatory) |
| **Android Security** (phone numbers in Room, session state, Device Admin) | `@mobile-security-coder` | R10 |
| **Compose UI Design** (all 4 unlock screens: layout, colors, typography) | `@mobile-design` | R11 |
| **Writing Tests** (UseCase tests, ViewModel tests, Flow tests) | `@test-driven-development` → `@testing-patterns` → `@kotlin-coroutines-expert` | R12 |
| **Debugging** (if something breaks during integration) | `@systematic-debugging` → `@error-handling-patterns` | R13 |
| **Code Review / Cleanup** (before marking Phase 6 done) | `@uncle-bob-craft` → `@production-code-audit` | R14 (E6 if session > 2h) |
| **Lint Before Commit** | `@lint-and-validate` | R15 (E4 mandatory) |
| **Git Commit & Push** | `@git-pushing` | R16 |

### Enforcement Rules (Non-Negotiable — from 7_RULES.md)

- **E1:** Never write Accessibility/Device Admin related code without `@mobile-developer` AND `@mobile-security-coder` both active.
- **E2:** Never write OTP verification code (client-side) without `@api-security-best-practices` active. The OTP entry screen handles real bypass attempts — it must be correct.
- **E3:** Never add new Room columns or DAOs without `@database-design`.
- **E4:** Never push code that has not been linted. Run `@lint-and-validate` before every `@git-pushing`.
- **E5:** Never ship a feature without at least one unit test for the core logic. Run `@test-driven-development` before writing the implementation.
- **E6:** If session lasted more than 2 hours, run `@production-code-audit` before ending.

---

## Task Breakdown — Build Order

### Task 6.1 — Domain UseCases (3 new)
**Agent:** Domain Agent + Security Agent (mandatory review)
**Skills:** `@brainstorming` → `@concise-planning` → `@mobile-developer` → `@kotlin-coroutines-expert` → `@api-security-best-practices` → `@auth-implementation-patterns`
**Priority:** 🔴 Critical
**Reference:** US-10, US-11, US-12 | `3_SYSTEM_DESIGN.md` Section 6

#### 6.1.1 — `RequestUnlockUseCase`
```kotlin
// domain/usecase/RequestUnlockUseCase.kt
// Orchestrates: create local unlock request → call backend /request-unlock → update request status
// Inputs: sessionId, trustedContactNumber, blockedCategories, blockedKeywords, timeRemaining
// Output: Result<Long> (requestId)
// Rules:
//   - No Android imports allowed (Domain Agent Rule A2)
//   - Must store request locally FIRST, then call backend
//   - If backend fails, update request status to FAILED
//   - Never log phone number or OTP
```

#### 6.1.2 — `VerifyUnlockOtpUseCase`
```kotlin
// domain/usecase/VerifyUnlockOtpUseCase.kt
// Orchestrates: call backend /verify-unlock-otp → update local request status → start cooldown
// Inputs: requestId, trustedContactNumber, otp (6-digit string)
// Output: Result<VerifyResult> (VALID → cooldown, INVALID → retry, MAX_ATTEMPTS → re-request)
// Rules:
//   - Increment attempts locally before calling backend
//   - After 3 failed attempts: set status to MAX_ATTEMPTS_REACHED
//   - On valid: set status to COOLDOWN_ACTIVE, cooldownEndsAt = now + 10 minutes
//   - Never log OTP value
```

#### 6.1.3 — `StartEmergencyBypassUseCase`
```kotlin
// domain/usecase/StartEmergencyBypassUseCase.kt
// Orchestrates: set session unlockType to EMERGENCY_BYPASS → start 24-hour timer
// Inputs: sessionId
// Output: Result<Unit>
// Rules:
//   - No backend call (US-12: "no OTP needed")
//   - Set emergencyBypassEndsAt = now + 24 hours
//   - Cannot be cancelled once started (US-12 acceptance criteria)
//   - Blocking remains fully active during the 24-hour wait
```

**Security Agent Review Points:**
- [ ] No plain OTP stored or logged
- [ ] No phone numbers logged
- [ ] Cooldown timer derived from server clock + local timestamp
- [ ] Emergency bypass cannot be triggered twice

---

### Task 6.2 — UnlockViewModel
**Agent:** Android Compose Agent
**Skills:** `@mobile-developer` → `@kotlin-coroutines-expert` → `@software-architecture`
**Priority:** 🔴 Critical

```kotlin
// ui/session/UnlockViewModel.kt
// Shared ViewModel across all 4 unlock screens
// StateFlow<UnlockUiState> with sealed states:
//   - Idle (ready to request)
//   - Requesting (loading)
//   - WaitingForOtp (SMS sent, awaiting user input)
//   - VerifyingOtp (loading)
//   - CooldownActive (10-min timer running, countdown display)
//   - EmergencyBypassActive (24-hour timer running)
//   - Unlocked (session ended)
//   - Error (message, retry action)
//
// Rules:
//   - No business logic — delegate to UseCases only (6_CONTEXT_AND_RULES.md)
//   - Use viewModelScope for coroutines
//   - Expose StateFlow, not LiveData
//   - No !! anywhere
```

---

### Task 6.3 — Unlock Request Screen (US-10)
**Agent:** Android Compose Agent
**Skills:** `@mobile-developer` → `@mobile-design` → `@kotlin-coroutines-expert`
**Priority:** 🔴 Critical
**Reference:** US-10 acceptance criteria

**Screen Requirements:**
- Shows current session info (time remaining, blocked categories/keywords)
- Shows trusted contact number (partially masked for privacy)
- "Request Early Unlock" button → triggers SMS to trusted contact
- Loading state while SMS is being sent
- After SMS sent → automatically navigate to OTP Entry screen
- "Emergency 24-hour Bypass" secondary button (clearly labelled as "24-hour delay")

**Acceptance Criteria from US-10:**
- [x] "Request early unlock" button visible on active session screen → already in `ActiveSessionScreen`
- [ ] Tapping it triggers SMS to trusted contact immediately
- [ ] SMS contains: app name, time of request, categories/keywords, time remaining
- [ ] OTP is 6 digits, valid for 10 minutes
- [ ] User sees a waiting screen after requesting

---

### Task 6.4 — OTP Entry Screen (US-11)
**Agent:** Android Compose Agent + Security Agent (mandatory)
**Skills:** `@mobile-developer` → `@mobile-design` → `@mobile-security-coder` → `@api-security-best-practices`
**Priority:** 🔴 Critical
**Reference:** US-11 acceptance criteria

**Screen Requirements:**
- 6-digit OTP input field (numeric keyboard)
- Clear error messages: "Wrong OTP", "OTP expired", "Max attempts reached"
- Attempt counter (X of 3 remaining)
- After correct OTP → navigate to Cooldown screen
- After 3 failed attempts → show "New OTP required" → navigate back to request screen
- Timer showing OTP expiry (10 minutes from request)

**Acceptance Criteria from US-11:**
- [ ] OTP entry screen appears after unlock request is sent
- [ ] Correct OTP triggers a 10-minute visible countdown before unlock
- [ ] Incorrect OTP shows error, allows retry (max 3 attempts)
- [ ] After 3 failed attempts, a new OTP must be requested (new SMS sent)
- [ ] During the 10-minute cooldown, blocking remains fully active

**Security Agent Review Points:**
- [ ] OTP field has `visualTransformation` for obscuring (or not, per UX choice)
- [ ] No OTP value logged
- [ ] Input length capped at 6 digits
- [ ] No autofill hints for OTP field

---

### Task 6.5 — Cooldown Screen (US-11)
**Agent:** Android Compose Agent + WorkManager Agent
**Skills:** `@mobile-developer` → `@mobile-design` → `@kotlin-coroutines-expert`
**Priority:** 🔴 Critical
**Reference:** US-11 acceptance criteria

**Screen Requirements:**
- 10-minute visible countdown timer (large, prominent)
- Clear message: "Blocking remains active during cooldown"
- No cancel/dismiss button — cooldown cannot be stopped
- After cooldown expires → `DeactivateSessionUseCase` → navigate to `SessionComplete`
- Must survive app restart/process death (WorkManager)

**WorkManager Agent Tasks:**
- Create `CooldownTimerWorker` in `worker/`
- Use `OneTimeWorkRequest` with 10-minute delay
- On completion: call `DeactivateSessionUseCase`
- Must work after reboot (already handled by existing boot recovery logic)

---

### Task 6.6 — Emergency Bypass Screen (US-12)
**Agent:** Android Compose Agent + WorkManager Agent
**Skills:** `@mobile-developer` → `@mobile-design` → `@kotlin-coroutines-expert`
**Priority:** 🔴 Critical
**Reference:** US-12 acceptance criteria

**Screen Requirements:**
- Strong warning: "24-hour delay unlock — blocking remains fully active"
- Confirmation dialog: "Are you sure? This cannot be cancelled."
- 24-hour countdown timer (large, prominent)
- Clear message: "All blocking remains active during this period"
- After 24 hours → `DeactivateSessionUseCase` → navigate to `SessionComplete`

**Acceptance Criteria from US-12:**
- [ ] Emergency bypass option is visible but clearly labelled as "24-hour delay unlock"
- [ ] Activating it starts a 24-hour countdown — no OTP needed
- [ ] Blocking remains fully active during the 24-hour wait
- [ ] Timer cannot be cancelled once started
- [ ] After 24 hours, session unlocks automatically

**WorkManager Agent Tasks:**
- Create `EmergencyBypassWorker` in `worker/`
- Use `OneTimeWorkRequest` with 24-hour delay
- Must survive reboot via existing `BootReceiver` pattern

---

### Task 6.7 — Navigation Wiring
**Agent:** Android Compose Agent
**Skills:** `@mobile-developer` → `@software-architecture`
**Priority:** 🔴 Critical

Replace all 4 `PlaceholderScreen` calls in `AppNavigation.kt` (lines 151–165):

| Route | Old (Placeholder) | New (Real Screen) |
|---|---|---|
| `UnlockRequest` | `PlaceholderScreen(name = "Unlock Request")` | `UnlockRequestScreen(...)` |
| `OtpEntry` | `PlaceholderScreen(name = "OTP Entry")` | `OtpEntryScreen(...)` |
| `UnlockCooldown` | `PlaceholderScreen(name = "Unlock Cooldown")` | `UnlockCooldownScreen(...)` |
| `EmergencyBypass` | `PlaceholderScreen(name = "Emergency Bypass")` | `EmergencyBypassScreen(...)` |

Navigation flow:
```
ActiveSession → UnlockRequest → OtpEntry → UnlockCooldown → SessionComplete
                     ↓
               EmergencyBypass → SessionComplete (after 24h)
```

After replacing all 4, delete `PlaceholderScreen.kt` if no longer used.

---

### Task 6.8 — Backend Deployment (Railway)
**Agent:** Backend API Agent
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` → `@lint-and-validate` → `@git-pushing`
**Priority:** 🟡 High

1. Push `backend/` to GitHub
2. Link GitHub repo to Railway
3. Configure Railway env vars:
   - `TWILIO_ACCOUNT_SID`
   - `TWILIO_AUTH_TOKEN`
   - `TWILIO_PHONE_NUMBER`
   - `PORT=3000`
   - `OTP_EXPIRY_MINUTES=10`
   - `OTP_MAX_ATTEMPTS=3`
4. Deploy and verify:
   - `GET /health` returns `{ status: "ok" }`
   - `POST /verify-contact` with real phone returns 200
5. Update `app/build.gradle.kts` release `BACKEND_URL` to Railway production URL

---

### Task 6.9 — Unit Tests
**Agent:** Testing Agent
**Skills:** `@test-driven-development` → `@testing-patterns` → `@kotlin-coroutines-expert` (Rule E5 — mandatory)
**Priority:** 🔴 Critical

| Test File | Key Cases |
|---|---|
| `RequestUnlockUseCaseTest.kt` | Happy path (creates request + calls backend), backend failure (status = FAILED), missing trusted contact |
| `VerifyUnlockOtpUseCaseTest.kt` | Valid OTP (→ COOLDOWN), invalid OTP (→ increment), 3 failed (→ MAX_ATTEMPTS), expired OTP |
| `StartEmergencyBypassUseCaseTest.kt` | Happy path (24h timer set), already in bypass (error), no active session (error) |
| `UnlockViewModelTest.kt` | State transitions: Idle → Requesting → WaitingForOtp → VerifyingOtp → CooldownActive → Unlocked. Error states. |

**Testing Rules:**
- Use `runTest` for all suspend function tests
- Use `Turbine` for `Flow` testing
- Mock `UnlockRepository` with Mockk or test doubles
- Mock `FocusLockApi` — never make real network calls in tests
- No `!!` in test assertions — use `shouldBe` or explicit null checks

---

### Task 6.10 — Security Audit
**Agent:** Security Agent → Code Reviewer Agent
**Skills:** `@api-security-best-practices` → `@mobile-security-coder` → `@backend-security-coder` → `@uncle-bob-craft` → `@production-code-audit` (Rule E6)
**Priority:** 🔴 Critical

**Mandatory Checklist (Rule A3):**
- [ ] No OTP values logged anywhere (debug or release)
- [ ] No phone numbers logged anywhere
- [ ] OTP entry field capped at 6 digits
- [ ] Cooldown timer cannot be bypassed or cancelled
- [ ] Emergency bypass cannot be triggered twice
- [ ] Session state correctly reflects unlock-in-progress
- [ ] Backend URL comes from `BuildConfig` — not hardcoded
- [ ] No `!!` in production Kotlin code
- [ ] No `runBlocking` in production code
- [ ] No business logic in Composable functions
- [ ] All DAO reads return `Flow<T>`

---

### Task 6.11 — Lint, Commit & Push
**Agent:** Code Reviewer Agent
**Skills:** `@lint-and-validate` → `@git-pushing` (Rules E4, R15, R16)
**Priority:** 🟡 High

1. Run `./gradlew testDebugUnitTest lintDebug`
2. Fix any warnings
3. Run `./gradlew assembleDebug` — verify build passes
4. Git commit with message: `[FEATURE] Implement unlock flow — US-10, US-11, US-12`
5. Push to remote

---

## Implementation Order (Dependency Graph)

```
6.1 (Domain UseCases — 3 new)
 ├── 6.2 (UnlockViewModel)
 │    └── 6.3 (Unlock Request Screen)
 │         ├── 6.4 (OTP Entry Screen)
 │         │    └── 6.5 (Cooldown Screen)
 │         └── 6.6 (Emergency Bypass Screen)
 └── 6.7 (Navigation Wiring) ← merges all screens

6.8 (Backend Deployment) ← independent, can run in parallel

6.9 (Unit Tests) ← after all implementation
 └── 6.10 (Security Audit) → 6.11 (Lint + Commit)
```

---

## Security Review Checkpoints (Rule A3)

| File | Reason |
|---|---|
| `RequestUnlockUseCase.kt` | Sends phone number to backend |
| `VerifyUnlockOtpUseCase.kt` | OTP verification + attempt tracking |
| `StartEmergencyBypassUseCase.kt` | Session unlock boundary |
| `UnlockViewModel.kt` | Holds session state, orchestrates unlock |
| `OtpEntryScreen.kt` | User enters OTP — input validation |
| `UnlockRequestScreen.kt` | Shows trusted contact number (privacy) |

---

## Acceptance Criteria Cross-Reference

| Story | Criteria | Task |
|---|---|---|
| US-10 | SMS to trusted contact with context, OTP valid 10 min | 6.1.1, 6.3 |
| US-11 | OTP verified, max 3 attempts, 10-min cooldown, blocking active during cooldown | 6.1.2, 6.4, 6.5 |
| US-12 | Emergency bypass, 24-hour wait, no OTP, cannot cancel, blocking active | 6.1.3, 6.6 |
| US-14 | Clean unlock, services stop, session summary shown | 6.5, 6.6 (delegates to existing `DeactivateSessionUseCase` + `SessionCompleteScreen`) |

---

## Risk Matrix

| Risk | Impact | Mitigation |
|---|---|---|
| Backend not deployed when Android tries to call | High | Task 6.8 can run in parallel; use `/health` check before enabling unlock button |
| Cooldown timer lost on process death | High | Use WorkManager `OneTimeWorkRequest`, not in-memory timer |
| Emergency bypass re-triggered | Medium | Guard with `getLatestRequestSync` — reject if bypass already active |
| OTP timeout on slow network | Medium | Retrofit timeout of 30s; show retry UI |
| User navigates away during cooldown | Medium | WorkManager continues independently; screen resumes from state |
| Phone number displayed in plaintext | Medium | Mask middle digits: +1 *** *** 1234 |

---

## Estimated Effort

| Task | Effort |
|---|---|
| 6.1 Domain UseCases (3) | 1.5 hours |
| 6.2 UnlockViewModel | 1 hour |
| 6.3 Unlock Request Screen | 1 hour |
| 6.4 OTP Entry Screen | 1.5 hours |
| 6.5 Cooldown Screen + Worker | 1 hour |
| 6.6 Emergency Bypass Screen + Worker | 1 hour |
| 6.7 Navigation Wiring | 30 min |
| 6.8 Backend Deployment (Railway) | 30 min |
| 6.9 Unit Tests (4 files) | 2 hours |
| 6.10 Security Audit | 1 hour |
| 6.11 Lint + Commit | 30 min |
| **Total** | **~12 hours** |

---

## Coding Convention Reminders (from 6_CONTEXT_AND_RULES.md)

These must be checked on every generated code block:

- ❌ No `!!` anywhere in Kotlin code
- ❌ No logic in Composable functions — delegate to ViewModel
- ❌ No business logic in ViewModels — only state and UseCase delegation
- ✅ All DAO reads return `Flow<T>` — never one-shot for reactive data
- ❌ No `runBlocking` in production code
- ❌ Never log phone numbers or OTPs even in debug builds
- ✅ Backend URL must come from `BuildConfig` — never hardcoded
- ✅ Coroutines only for async — no RxJava, no callbacks, no manual threads
- ✅ Hilt for all DI — no manual DI, no service locator
