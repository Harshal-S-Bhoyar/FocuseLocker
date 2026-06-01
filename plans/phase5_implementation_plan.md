# Phase 5 — Backend OTP Server: Implementation Plan
## FocusLock

---

## Phase 4 Completion Validation

Before starting Phase 5, Phase 4 status was verified:

| Check | Result |
|---|---|
| All 13 Phase 4 implementation tasks (4.1–4.13) | ✅ Complete |
| Resolution plan: 21 issues resolved in source | ✅ Verified |
| `assembleDebug` / `testDebugUnitTest` / `lintDebug` | ✅ Passed |
| `assembleRelease` with signed keystore | ✅ Passed (1.92 MB) |
| Static audit (146 Kotlin files, 0 forbidden patterns) | ✅ Clean |
| Physical-device E2E validation | ⏳ Human gate — does NOT block Phase 5 |
| Instrumentation tests on device | ⏳ Human gate — does NOT block Phase 5 |

**Verdict:** Phase 4 code is complete. The 2 remaining items are human-run device tests that do not block backend development. Phase 5 can proceed.

---

## Phase 5 Objective

Build the minimal Node.js + Express + TypeScript backend that:
1. Generates and verifies OTPs for trusted contact registration (US-02)
2. Generates and verifies OTPs for early session unlock (US-10, US-11)
3. Sends SMS via Twilio with contextual messages
4. Stores nothing permanently — OTPs live in memory with 10-minute TTL
5. Deploys to Railway.app

---

## Current State Assessment

### ✅ Already Exists (from Phases 1–4)

| Component | Location |
|---|---|
| Retrofit `FocusLockApi` interface (4 endpoints) | `data/remote/api/FocusLockApi.kt` |
| DTO request/response classes (all 4 pairs) | `data/remote/dto/` |
| `NetworkModule` (Hilt, OkHttp, Retrofit) | `di/NetworkModule.kt` |
| `UnlockRepository` + `UnlockRepositoryImpl` | `domain/repository/`, `data/repository/` |
| `UnlockRequestDao` + `UnlockRequestEntity` | `data/local/dao/`, `data/local/entity/` |
| `BuildConfig.BACKEND_URL` (debug=localhost, release=railway) | `app/build.gradle.kts` |

### ❌ What Needs to Be Built

| Component | State |
|---|---|
| `backend/` directory (entire Node.js project) | ❌ Not created |
| `otp.store.ts` — in-memory Map with TTL | ❌ |
| `otp.service.ts` — generation, hashing, verification | ❌ |
| `sms.service.ts` — Twilio wrapper | ❌ |
| `request.schemas.ts` — Zod validation | ❌ |
| `validate.ts` — Zod middleware | ❌ |
| `contact.routes.ts` — `/verify-contact`, `/confirm-contact` | ❌ |
| `unlock.routes.ts` — `/request-unlock`, `/verify-unlock-otp` | ❌ |
| `app.ts` — Express setup | ❌ |
| `index.ts` — Entry point | ❌ |
| Unit tests for all services and routes | ❌ |
| Railway deployment config | ❌ |

---

## Skill Invocation Map (from 7_RULES.md)

Before ANY coding begins, invoke skills in this order per Rule 1:

| Phase | Skills (invoke in order) | Rule |
|---|---|---|
| **Session Start** | `@brainstorming` → `@concise-planning` | R1 |
| **All Backend Code** | `@backend-dev-guidelines` → `@typescript-expert` | R6 |
| **API Endpoint Design** | `@api-design-principles` → `@api-patterns` | R7 |
| **Twilio SMS Integration** | `@twilio-communications` | R8 |
| **OTP/Auth Security** | `@api-security-best-practices` → `@auth-implementation-patterns` → `@backend-security-coder` | R9 |
| **Architecture Decisions** | `@software-architecture` → `@architecture` | R4 |
| **Writing Tests** | `@test-driven-development` → `@testing-patterns` | R12 |
| **Code Review / Cleanup** | `@uncle-bob-craft` → `@production-code-audit` | R14 |
| **Before Commit** | `@lint-and-validate` | R15 |
| **Git Commit & Push** | `@git-pushing` | R16 |

### Enforcement Rules (Non-Negotiable)

- **E2:** Never write OTP generation/hashing/verification without `@api-security-best-practices` active
- **E4:** Never push code that hasn't been linted — `@lint-and-validate` before `@git-pushing`
- **E5:** Never ship a feature without at least one unit test — `@test-driven-development` first
- **E6:** If session > 2 hours, run `@production-code-audit` before ending

---

## Task Breakdown — Build Order

### Task 5.1 — Project Scaffolding
**Agent:** Architect Agent → Backend API Agent  
**Skills:** `@brainstorming` → `@concise-planning` → `@software-architecture` → `@backend-dev-guidelines` → `@typescript-expert`  
**Priority:** 🔴 Critical

Create `backend/` per `5_TECH_STACK.md` §7:

```
backend/
├── src/
│   ├── routes/
│   │   ├── contact.routes.ts
│   │   └── unlock.routes.ts
│   ├── services/
│   │   ├── otp.service.ts
│   │   └── sms.service.ts
│   ├── store/
│   │   └── otp.store.ts
│   ├── middleware/
│   │   └── validate.ts
│   ├── schemas/
│   │   └── request.schemas.ts
│   └── app.ts
├── tests/
│   ├── otp.service.test.ts
│   ├── otp.store.test.ts
│   ├── contact.routes.test.ts
│   └── unlock.routes.test.ts
├── index.ts
├── package.json
├── tsconfig.json
├── .env.example
├── .gitignore
└── Procfile
```

**Dependencies:**
- `express@^4.18`, `twilio@^4.20`, `bcrypt@^5.1`, `zod@^3.22`, `dotenv@^16.3`, `helmet@^7.1`, `cors@^2.8`
- Dev: `typescript@^5.3`, `jest@^29.7`, `ts-jest`, `supertest@^6.3`, `tsx@^4.7`, types

**Environment variables (.env.example):**
```
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_PHONE_NUMBER=
OTP_EXPIRY_MINUTES=10
OTP_MAX_ATTEMPTS=3
PORT=3000
```

---

### Task 5.2 — In-Memory OTP Store (`otp.store.ts`)
**Agent:** Backend API Agent + Security Agent  
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` → `@api-security-best-practices` → `@auth-implementation-patterns`  
**Priority:** 🔴 Critical

**Interface:**
```typescript
interface OtpEntry {
  otpHash: string;        // bcrypt hash
  expiresAt: number;      // Unix ms
  type: "CONTACT_VERIFY" | "SESSION_UNLOCK";
  attempts: number;
}

class OtpStore {
  set(phone: string, entry: OtpEntry): void;
  get(phone: string): OtpEntry | undefined;
  incrementAttempts(phone: string): number;
  delete(phone: string): void;
  cleanup(): void;  // removes expired entries, runs on 60s interval
}
```

**Rules:**
- ❌ Never store plain OTP — only bcrypt hash
- ❌ Never log OTP values or phone numbers
- ✅ Entries auto-expire and auto-delete
- ✅ Max 3 attempts per entry — then entry is deleted

---

### Task 5.3 — OTP Service (`otp.service.ts`)
**Agent:** Backend API Agent + Security Agent (mandatory)  
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` → `@api-security-best-practices` → `@auth-implementation-patterns` → `@backend-security-coder` (Rule E2 — mandatory for OTP code)  
**Priority:** 🔴 Critical

```typescript
class OtpService {
  async generateAndStore(phone: string, type: OtpType): Promise<string>;
  // Returns plain OTP for SMS sending. Stores bcrypt hash.

  async verify(phone: string, plainOtp: string): Promise<{ valid: boolean; reason?: string }>;
  // Reasons: "expired", "max_attempts", "invalid", "not_found"
}
```

**Rules:**
- ✅ `crypto.randomInt(100000, 999999)` — never `Math.random`
- ✅ `bcrypt` salt rounds ≥ 10
- ✅ After successful verify → delete entry
- ✅ After 3 failed attempts → delete entry

---

### Task 5.4 — SMS Service (`sms.service.ts`)
**Agent:** Backend API Agent  
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` → `@twilio-communications`  
**Priority:** 🟡 High

```typescript
class SmsService {
  async sendVerificationOtp(to: string, otp: string): Promise<boolean>;
  async sendUnlockOtp(to: string, otp: string, context: UnlockContext): Promise<boolean>;
}
```

**Unlock SMS template (from System Design §6):**
```
FocusLock: Someone wants to end their session early.
Time remaining: {timeRemaining}. Blocked: {categories}.
OTP to unlock: {otp}. Share only if you think it's justified.
```

---

### Task 5.5 — Zod Validation Schemas (`request.schemas.ts`)
**Agent:** Backend API Agent  
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` → `@api-design-principles` → `@api-patterns`  
**Priority:** 🟡 High

Four schemas matching the 4 Android DTOs:
- `verifyContactSchema`: `{ phoneNumber: string }` — E.164 format
- `confirmContactSchema`: `{ phoneNumber, otp }` — 6-digit string
- `requestUnlockSchema`: `{ trustedContactNumber, blockedCategories[], blockedKeywords[], timeRemaining, requestTimestamp }`
- `verifyUnlockOtpSchema`: `{ trustedContactNumber, otp }`

---

### Task 5.6 — Zod Validation Middleware (`validate.ts`)
**Agent:** Backend API Agent  
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` → `@api-design-principles`  
**Priority:** 🟢 Standard

Generic middleware: accepts a `ZodSchema`, returns 400 with field errors on invalid body, attaches validated data on success.

---

### Task 5.7 — Contact Routes (`contact.routes.ts`)
**Agent:** Backend API Agent + Security Agent  
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` → `@api-design-principles` → `@api-patterns` → `@api-security-best-practices` → `@auth-implementation-patterns`  
**Priority:** 🔴 Critical — US-02

| Endpoint | Flow |
|---|---|
| `POST /verify-contact` | Validate → Generate OTP → SMS → `{ success: true }` |
| `POST /confirm-contact` | Validate → Verify OTP → `{ verified: true/false }` |

---

### Task 5.8 — Unlock Routes (`unlock.routes.ts`)
**Agent:** Backend API Agent + Security Agent (mandatory)  
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` → `@api-design-principles` → `@api-patterns` → `@api-security-best-practices` → `@auth-implementation-patterns` → `@backend-security-coder` (Rule E2)  
**Priority:** 🔴 Critical — US-10, US-11

| Endpoint | Flow |
|---|---|
| `POST /request-unlock` | Validate → Generate OTP → Context SMS → `{ success: true }` |
| `POST /verify-unlock-otp` | Validate → Verify OTP → `{ valid: true/false }` |

---

### Task 5.9 — Express App + Entry Point
**Agent:** Backend API Agent  
**Skills:** `@backend-dev-guidelines` → `@typescript-expert` → `@api-security-best-practices`  
**Priority:** 🟡 High

- `helmet()` security headers
- `cors()` for Android app
- `express.json()` body parser
- Mount contact + unlock routes
- Global error handler (no stack traces in production)
- `GET /health` → `{ status: "ok" }`
- Validate required env vars on startup

---

### Task 5.10 — Unit Tests
**Agent:** Testing Agent  
**Skills:** `@test-driven-development` → `@testing-patterns` (Rule E5 — mandatory before feature ships)  
**Priority:** 🔴 Critical

| Test File | Key Cases |
|---|---|
| `otp.store.test.ts` | Set/get/delete, TTL expiry, max attempts, cleanup |
| `otp.service.test.ts` | 6-digit generation, bcrypt hash, verify correct/wrong/expired/max |
| `contact.routes.test.ts` | 200 valid, 400 invalid phone, 400 wrong OTP |
| `unlock.routes.test.ts` | 200 valid, context SMS, 400 invalid body |

---

### Task 5.11 — Security Audit + Code Review
**Agent:** Security Agent → Code Reviewer Agent  
**Skills:** `@api-security-best-practices` → `@backend-security-coder` → `@uncle-bob-craft` → `@production-code-audit` (Rule E6)  
**Priority:** 🔴 Critical

- [ ] No plain OTPs stored
- [ ] No phone numbers logged
- [ ] `crypto.randomInt` used
- [ ] `bcrypt` salt rounds ≥ 10
- [ ] All inputs Zod-validated
- [ ] Helmet headers active
- [ ] Env vars validated on startup
- [ ] No secrets in source control

---

### Task 5.12 — Railway Deployment
**Agent:** Backend API Agent  
**Skills:** `@backend-dev-guidelines` → `@lint-and-validate` → `@git-pushing` (Rules E4, R15, R16)  
**Priority:** 🟡 High

1. `Procfile`: `web: node dist/index.js`
2. Build script: `"build": "tsc"`, `"start": "node dist/index.js"`
3. Configure Railway env vars
4. Deploy from GitHub
5. Verify `/health` responds
6. Test `/verify-contact` with real phone

---

## Implementation Order

```
5.1 (Scaffold)
 ├── 5.2 (OTP Store) → 5.3 (OTP Service) → 5.7 (Contact Routes) → 5.8 (Unlock Routes)
 ├── 5.4 (SMS Service) ────────────────────────────────────────────┘
 └── 5.5 (Schemas) → 5.6 (Middleware) ─────────────────────────────┘

5.9 (Express App) ← merges all
 └── 5.10 (Tests) → 5.11 (Security) → 5.12 (Deploy)
```

---

## Security Review Checkpoints (Rule A3)

| File | Reason |
|---|---|
| `otp.service.ts` | OTP generation + hashing |
| `otp.store.ts` | TTL + attempt limits |
| `sms.service.ts` | Phone numbers + OTP in SMS |
| `unlock.routes.ts` | Session unlock boundary |
| `contact.routes.ts` | Contact verification |

---

## Acceptance Criteria

| Story | Criteria | Task |
|---|---|---|
| US-02 | OTP sent, verified, contact saved | 5.7 |
| US-10 | SMS to trusted contact with context, OTP valid 10 min | 5.8 |
| US-11 | OTP verified, max 3 attempts, invalidated after use | 5.3, 5.8 |
| US-12 | Emergency bypass is client-side only | N/A |

---

## Risk Matrix

| Risk | Impact | Mitigation |
|---|---|---|
| Twilio key leaked | High | Env vars only, `.gitignore`, Railway secrets |
| OTP brute-force | High | 3-attempt limit + 10-min TTL |
| Server restart loses OTPs | Medium | Acceptable for MVP |
| SMS delivery failure | Medium | Client retry UI |
| Phone number privacy | High | Never log, never persist beyond TTL |

---

## Estimated Effort

| Task | Effort |
|---|---|
| 5.1 Scaffolding | 30 min |
| 5.2–5.3 OTP Store + Service | 2 hours |
| 5.4 SMS Service | 1 hour |
| 5.5–5.6 Validation | 30 min |
| 5.7–5.8 Routes | 1.5 hours |
| 5.9 Express App | 30 min |
| 5.10 Tests | 2 hours |
| 5.11 Security Audit | 1 hour |
| 5.12 Deployment | 1 hour |
| **Total** | **~10 hours** |
