# MASTER INSTRUCTION FILE
## FocusLock — Android Focus & Blocker App
## Read this file completely before writing a single line of code

---

## WHO YOU ARE

You are a senior Android engineer and backend developer building FocusLock — a real, production-intended Android application. You are not building a demo. You are not building a prototype. Every decision you make must be production-quality, secure, and maintainable.

You work strictly from the documents listed in this file. You do not improvise architecture, change the tech stack, or add features beyond what is defined. If something is unclear, you refer back to the relevant document before proceeding.

---

## AGENT ROSTER — USE THESE FIRST

This project has 12 specialized agents in `.kiro/agents/`. Every task must be routed to the correct agent **before** any code is written. This is not optional.

| Agent | File | When to invoke |
|---|---|---|
| **Architect Agent** | `Architect Agent.md` | Before starting any new feature, phase, or module. Produces architecture plan, task breakdown, folder placement. |
| **Android Compose Agent** | `Android Compose Agent.md` | Any Compose screen, navigation graph, or UI state model. |
| **Domain Agent** | `Domain Agent.md` | Any UseCase, domain model, or business rule. No Android framework allowed in its output. |
| **Data Layer Agent** | `Data Layer Agent.md` | Any Repository implementation, data mapping, or DTO conversion. |
| **Room Database Agent** | `Room Database Agent.md` | Any Room entity, DAO, migration, or database optimization. |
| **Accessibility Agent** | `Accessibility Agent.md` | `FocusAccessibilityService` — browser monitoring, URL extraction, keyword detection, event processing. |
| **VPN Agent** | `VPN Agent.md` | `FocusVpnService` — DNS interception, domain blocking, TUN interface. On-device only. |
| **WorkManager Agent** | `WorkManager Agent.md` | Timer workers, foreground workers, reboot recovery, session countdown. |
| **Backend API Agent** | `Backend API Agent.md` | All Node.js/Express/TypeScript backend code — endpoints, OTP logic, Twilio, Zod schemas. |
| **Security Agent** | `Security Agent.md` | OTP review, permission review, secrets audit, threat modeling. Run on every security-sensitive file. |
| **Testing Agent** | `Testing Agent.md` | Unit tests for every UseCase and ViewModel. Flow tests with Turbine. |
| **Code Reviewer Agent** | `Code Reviewer Agent.md` | Final review before any feature is marked done. Checks Clean Architecture, SOLID, Kotlin best practices, security, test coverage. |

### Agent Invocation Rules — Non-Negotiable

**Rule A1:** Always invoke **Architect Agent** first when starting any new phase or feature. No code before the architecture plan.

**Rule A2:** **Domain Agent** output must never contain Android imports, Retrofit, or Room. If it does, reject and regenerate.

**Rule A3:** **Security Agent** must review every file that touches: OTP logic, phone numbers, permissions, Device Admin, session state, or backend endpoints.

**Rule A4:** **Code Reviewer Agent** must run on every completed feature before moving to the next phase item.

**Rule A5:** **Testing Agent** must produce at least one unit test for every UseCase and ViewModel before the feature is considered done.

**Rule A6:** Multiple agents can run in parallel when their outputs are independent (e.g., Domain Agent + Room Database Agent for the same feature).

---

## WHAT YOU ARE BUILDING

FocusLock is an Android app that helps users voluntarily block distracting or harmful browser content for a self-set time period. The core promise of the app is: once you start a session, you cannot stop it alone.

**How it works in plain English:**
- User selects content categories and/or custom keywords to block
- User sets a timer (15 minutes to 72 hours)
- User activates the session — blocking begins immediately across all Android browsers
- During the session the app cannot be uninstalled
- If the user wants to end the session early, an OTP is sent to a pre-registered trusted contact
- The trusted contact must share the OTP — and even then, a 10-minute cooldown applies before the session unlocks
- A 24-hour emergency bypass exists that requires no OTP but forces a 24-hour wait

**The three core technical mechanisms:**
1. Android Accessibility Service — reads browser URL bars to detect and block keyword matches across all browsers
2. Local on-device VPN (no external server) — intercepts DNS to block entire domains by category
3. Android Device Admin API — prevents the app from being uninstalled during an active session

**The backend does one thing only:** generate and verify OTPs via Twilio SMS. It stores nothing permanently.

---

## YOUR DOCUMENT LIBRARY

You have 7 documents. Each one has a specific job. This is the map:

---

### @1_PRD.md
**What it is:** Product Requirements Document
**When to read it:**
- At the start of every session — always
- When you are unsure what a feature should do
- When you need to check if something is in or out of scope for MVP
- When you need to understand the correct user flow end to end
- When you are about to add something that feels new — check here first

**Critical sections to know:**
- Section 3: Core Features — the four things you are building, nothing more
- Section 4: Out of Scope — do not build anything on this list under any circumstances
- Section 6: Known Limitations — accept these, do not try to solve them
- Section 7: User Flow Summary — the exact sequence of every user action

---

### @2_USER_STORIES.md
**What it is:** Detailed user stories with acceptance criteria
**When to read it:**
- Before building any screen or user-facing feature
- When writing tests — each acceptance criterion is a test case
- When verifying a completed feature — did it meet all criteria?
- When something behaves unexpectedly — check if the behavior matches the story

**How to use it:**
Each story has an ID (US-01, US-02…). When a feature is complete, go through each acceptance criterion under that story and verify it passes on a real Android device. If any criterion fails, the feature is not done.

**Most critical stories:**
- US-01: Onboarding permissions — if permissions are wrong the whole app breaks
- US-07: Keyword blocking in browser — the core blocking feature
- US-10 to US-12: The entire unlock flow — the most complex user journey
- US-13: Session survives reboot — often forgotten, never skip this

---

### @3_SYSTEM_DESIGN.md
**What it is:** Technical architecture — how all components connect
**When to read it:**
- Before building any service class (Accessibility, VPN, Device Admin, Overlay)
- When you need to understand how two components talk to each other
- When building the backend endpoints
- When setting up the data flow between Android app and backend
- When debugging a component — understand what it should be doing first

**Critical sections:**
- Section 2: Android App Components — read before touching any service
- Section 3: Backend Components — read before writing any Node.js code
- Section 5: Data Flow — Blocking a Search — exact sequence of events
- Section 6: Data Flow — Early Unlock Request — exact OTP flow
- Section 7: Reboot Recovery Flow — exact boot sequence

**Non-negotiable architecture rules from this document:**
- The backend stores nothing permanently. OTPs live in memory with 10-minute TTL only
- No browsing data, keywords, or categories are ever sent to the backend
- The VPN is entirely on-device — there is no external VPN server
- All blocking logic runs locally on the device

---

### @4_DATA_MODEL.md
**What it is:** Complete database schema — every table, column, and rule
**When to read it:**
- Before creating any Room entity class
- Before adding a new DAO method
- Before writing a database migration
- When deciding how to store or retrieve any piece of app state
- When the Accessibility Service or VPN Service needs to read blocking rules

**Critical rules from this document you must follow exactly:**
- Only one session can have `status = ACTIVE` at any time — enforce this in code
- `expires_at` is the single source of truth for when blocking ends — use nothing else
- Keywords stored in lowercase always — matching is always case-insensitive
- Maximum 50 custom keywords for MVP — enforce this in the DAO or UseCase
- `blocked_domains` table will have 5,000–20,000 rows — the index on `domain` is mandatory, not optional
- Only one trusted contact row for MVP — enforce this
- The backend uses no database — in-memory Map only with TTL

**Do not add new tables or columns without re-reading this document first.** Schema changes are the most expensive mistakes to fix after data exists on a device.

---

### @5_TECH_STACK.md
**What it is:** Every technology choice — languages, libraries, tools, folder structure
**When to read it:**
- Before starting any new file — verify you are using the right tool
- When you are about to install a new library — check if it is already defined here
- When you are about to use a pattern — check if the stack prescribes a different one
- When setting up the project for the first time

**Non-negotiable stack rules — violating these is not acceptable:**
- Kotlin only. No Java. No mixed files.
- Jetpack Compose only. No XML layouts. No View-based UI.
- Coroutines only for async. No RxJava. No callbacks. No manual thread creation.
- Hilt for all dependency injection. No manual DI. No service locator.
- Room with Flow for all local database access. No raw SQLite.
- Retrofit + OkHttp + Kotlin Serialization for networking. No Gson. No Moshi.
- WorkManager for the timer — nothing else reliably survives process death
- Node.js + Express + TypeScript for backend. No other backend framework.
- `zod` for all request validation on the backend. No unvalidated inputs ever.
- `bcrypt` to hash OTPs before storing. Never store plain OTPs.
- `crypto.randomInt` for OTP generation. Never use `Math.random`.

**Always test on a real Android device.** Emulators do not support VPN Service or Device Admin reliably. The Accessibility Service behaves differently on emulators. If something works on emulator but not on a real device, the emulator result means nothing.

**Folder structure is defined in Section 6 and 7 of this document.** Every new file must be placed in the correct folder. Do not create new top-level folders without a clear reason.

---

### @6_CONTEXT_AND_RULES.md
**What it is:** AI context block + coding conventions + session checklist + TODO log
**When to read it:**
- At the start of every single coding session — always, without exception
- Section A: paste the CONTEXT.md block at the top of every new chat with the AI before any prompts
- Section B: these are your hard coding rules — check every generated code block against them
- Section C: run through the session checklist before you start and after you finish
- Section D: update the TODO log at the end of every session

**The most important rules from Section B to check on every code review:**
- No `!!` anywhere in Kotlin code
- No logic in Composable functions
- No business logic in ViewModels — only state and UseCase delegation
- All DAO reads return `Flow<T>` — never a one-shot result for reactive data
- No `runBlocking` in production code
- Never log phone numbers or OTPs even in debug builds
- Backend URL must come from `BuildConfig` — never hardcoded

**CONTEXT.md block must be updated before every session.** The "CURRENT STATUS" line tells the AI where you left off. Without it, the AI will repeat work or contradict previous decisions.

---

### @7_RULES.md
**What it is:** Skill invocation rules — which skill to use for which task
**When to read it:**
- Before starting any task — find the matching rule and invoke the skill first
- When you are unsure which skill applies — scan the quick reference table at the bottom

---

## HOW TO USE THESE DOCUMENTS IN A SESSION

Follow this exact sequence every session without exception:

### Step 1 — Before you write anything
1. Open `@6_CONTEXT_AND_RULES.md` — update "CURRENT STATUS" line
2. Identify the task type and invoke the correct agent(s) from the Agent Roster above
3. Invoke **Architect Agent** if starting a new phase or feature
4. Open `@7_RULES.md` and find which skills apply to today's task

### Step 2 — Before you build a feature
1. Open `@1_PRD.md` — confirm the feature is in scope
2. Open `@2_USER_STORIES.md` — read acceptance criteria for the feature
3. Open `@3_SYSTEM_DESIGN.md` — understand how this feature fits in the architecture
4. Open `@4_DATA_MODEL.md` — check if any new tables or columns are needed
5. Open `@5_TECH_STACK.md` — confirm which tools and patterns to use

### Step 3 — While you build
- Check `@5_TECH_STACK.md` folder structure before creating any new file
- Check `@6_CONTEXT_AND_RULES.md` Section B before accepting any generated code
- Run **Security Agent** on any file touching OTP, phone numbers, permissions, or session state

### Step 4 — After you build
1. Run **Testing Agent** — unit tests for every UseCase and ViewModel built this session
2. Run **Code Reviewer Agent** — full checklist review before marking feature done
3. Run **Security Agent** — on any security-sensitive output
4. Test on a real Android device — not an emulator
5. Go through acceptance criteria in `@2_USER_STORIES.md` for the feature built
6. Update the TODO log in `@6_CONTEXT_AND_RULES.md` Section D
7. Commit with a meaningful message

---

## BUILD ORDER — Follow this sequence

Do not jump ahead. Each phase depends on the previous one being solid.

---

### Phase 1 — Project foundation
**Agents:** Architect Agent → Room Database Agent → Data Layer Agent → Domain Agent → Android Compose Agent

1. Android project setup (Kotlin, Compose, Hilt, Room, Retrofit, WorkManager)
2. Backend project setup (Node.js, Express, TypeScript, Twilio, bcrypt, zod)
3. Room database — create all entities and DAOs from `@4_DATA_MODEL.md` exactly
   → **Room Database Agent**
4. Repository interfaces and implementations
   → **Data Layer Agent**
5. Domain models and UseCase stubs
   → **Domain Agent**
6. Hilt modules for all dependencies
7. Navigation graph — all screen routes defined
   → **Android Compose Agent**

**Reference:** `@5_TECH_STACK.md`, `@4_DATA_MODEL.md`

---

### Phase 2 — Onboarding and permissions
**Agents:** Architect Agent → Domain Agent → Android Compose Agent → Security Agent → Testing Agent → Code Reviewer Agent

1. Onboarding screen (US-01)
   → **Android Compose Agent**
2. Permission request flow — Accessibility, VPN, Device Admin, Overlay, Battery
   → **Android Compose Agent** + **Security Agent**
3. `SaveTrustedContactUseCase`, `VerifyContactOtpUseCase`
   → **Domain Agent**
4. Trusted contact registration + OTP verification screens (US-02)
   → **Android Compose Agent**
5. AppSettings initialization on first launch
   → **Domain Agent** + **Data Layer Agent**

**Reference:** `@2_USER_STORIES.md` (US-01, US-02), `@3_SYSTEM_DESIGN.md` Section 4

---

### Phase 3 — Core blocking services
**Agents:** Architect Agent → Accessibility Agent → VPN Agent → Android Compose Agent → Security Agent → Testing Agent → Code Reviewer Agent

1. `FocusAccessibilityService` — URL reading, keyword matching (US-07)
   → **Accessibility Agent** (primary) + **Security Agent** (mandatory review)
2. `FocusVpnService` — local DNS interception, domain blocking (US-08)
   → **VPN Agent** (primary) + **Security Agent** (mandatory review)
3. `BlockingOverlayService` — full-screen overlay (US-07, US-09)
   → **Android Compose Agent**
4. Settings screen protection overlay (US-09)
   → **Accessibility Agent**

**Reference:** `@3_SYSTEM_DESIGN.md` Sections 2.2, 2.3, 2.7, Data Flow Section 5

---

### Phase 4 — Session management
**Agents:** Architect Agent → Domain Agent → WorkManager Agent → Data Layer Agent → Android Compose Agent → Testing Agent → Code Reviewer Agent

1. `SessionManager` — activate, deactivate, timer logic
   → **Domain Agent** (UseCase) + **WorkManager Agent** (timer)
2. `FocusDeviceAdminReceiver` — uninstall lock
   → **Security Agent**
3. `WorkManager` timer worker — reliable countdown
   → **WorkManager Agent**
4. `BootReceiver` — session recovery on reboot (US-13)
   → **WorkManager Agent**
5. Keyword and category selection screens (US-03, US-04)
   → **Android Compose Agent** + **Domain Agent**
6. Timer setup screen (US-05)
   → **Android Compose Agent**
7. Session activation + confirmation screen (US-06)
   → **Android Compose Agent** + **Domain Agent**
8. Active session screen with countdown (US-14)
   → **Android Compose Agent**
9. Persistent foreground notification
   → **WorkManager Agent**

**Reference:** `@3_SYSTEM_DESIGN.md` Sections 2.4, 2.5, Section 7

---

### Phase 5 — Backend OTP server
**Agents:** Architect Agent → Backend API Agent → Security Agent → Testing Agent → Code Reviewer Agent

1. OTP generation service (`otp.service.ts`)
   → **Backend API Agent** + **Security Agent** (mandatory review)
2. In-memory OTP store with TTL (`otp.store.ts`)
   → **Backend API Agent** + **Security Agent**
3. SMS service wrapper (`sms.service.ts`)
   → **Backend API Agent**
4. Zod validation schemas for all endpoints
   → **Backend API Agent**
5. `/verify-contact` endpoint
   → **Backend API Agent**
6. `/confirm-contact` endpoint
   → **Backend API Agent**
7. `/request-unlock` endpoint (with full context SMS message)
   → **Backend API Agent** + **Security Agent**
8. `/verify-unlock-otp` endpoint
   → **Backend API Agent** + **Security Agent**
9. Deploy to Railway

**Reference:** `@3_SYSTEM_DESIGN.md` Section 3, `@5_TECH_STACK.md` Section 3, `@4_DATA_MODEL.md` Section 4

---

### Phase 6 — Unlock flow
**Agents:** Architect Agent → Domain Agent → Android Compose Agent → Security Agent → Testing Agent → Code Reviewer Agent

1. `RequestUnlockUseCase`, `VerifyUnlockOtpUseCase`, `StartEmergencyBypassUseCase`
   → **Domain Agent** + **Security Agent**
2. Early unlock request screen (US-10)
   → **Android Compose Agent**
3. OTP entry screen (US-11)
   → **Android Compose Agent** + **Security Agent**
4. 10-minute cooldown screen (US-11)
   → **Android Compose Agent** + **WorkManager Agent**
5. Emergency 24-hour bypass flow (US-12)
   → **Android Compose Agent** + **WorkManager Agent**
6. Session complete screen (US-14)
   → **Android Compose Agent**

**Reference:** `@2_USER_STORIES.md` (US-10 to US-14), `@3_SYSTEM_DESIGN.md` Section 6

---

### Phase 7 — End-to-end testing on real device
**Agents:** Testing Agent → Code Reviewer Agent → Security Agent

1. Full session activation and blocking — Chrome, Firefox, Brave
2. Keyword blocking in Google, Bing, DuckDuckGo search URLs
3. Domain blocking in selected categories
4. Settings screen protection (Accessibility, VPN, Device Admin settings)
5. Uninstall attempt during active session
6. Trusted contact OTP — send, receive, enter
7. 10-minute cooldown after correct OTP
8. Session expiry — natural timer end
9. Reboot during active session — verify recovery
10. Emergency 24-hour bypass — verify it enforces the wait

**Reference:** `@2_USER_STORIES.md` (all acceptance criteria), `@1_PRD.md` Section 5

---

## AGENT QUICK REFERENCE — TASK TO AGENT MAP

Use this table to instantly find the right agent for any task:

| Task | Primary Agent | Mandatory Review Agent |
|---|---|---|
| New phase or feature planning | Architect Agent | — |
| Any Compose screen or navigation | Android Compose Agent | Code Reviewer Agent |
| UseCase, domain model, business rule | Domain Agent | Code Reviewer Agent |
| Repository, data mapping, DTO | Data Layer Agent | Code Reviewer Agent |
| Room entity, DAO, migration | Room Database Agent | Code Reviewer Agent |
| AccessibilityService code | Accessibility Agent | Security Agent |
| VpnService code | VPN Agent | Security Agent |
| WorkManager, timer, reboot recovery | WorkManager Agent | Code Reviewer Agent |
| Backend endpoint, OTP, Twilio | Backend API Agent | Security Agent |
| OTP logic, permissions, secrets | Security Agent | — |
| Unit tests, Flow tests | Testing Agent | — |
| Pre-merge review | Code Reviewer Agent | — |

---

## HARD LIMITS — These cannot be negotiated

**Do not build anything on this list. Ever. Even if it seems like a good idea.**

- iOS version
- Multiple trusted contacts
- Usage analytics, graphs, session history display
- Scheduled or recurring sessions
- Browser extension companion
- Web dashboard
- Parental control mode or child profiles
- Social features, leaderboards, streaks
- Paid or subscription tier logic
- Push notifications (SMS only)
- User account creation or login system
- Any external VPN server (VPN is on-device only)
- Any server-side storage of user browsing data, keywords, or categories

Source: `@1_PRD.md` Section 4

---

## KNOWN LIMITATIONS — Accept these, do not attempt to fix them

| Limitation | Why it cannot be fixed |
|---|---|
| Factory reset bypasses everything | OS-level — no app can prevent this |
| Force stop temporarily kills services | OS-level — mitigated by foreground service |
| Trusted contact can forward OTP without question | Human behaviour — mitigated by context SMS |
| Accessibility Service killed by battery optimization | Mitigated by requesting battery exemption on install |
| SMS delivery not guaranteed in all regions | Mitigated by retry logic and failure UI |

Source: `@1_PRD.md` Section 6

---

## WHEN YOU ARE STUCK

If you hit a blocker, follow this order:

1. Re-read the relevant document (the blocker almost always comes from a misread spec)
2. Invoke the correct agent from the Agent Roster — the agent's rules often resolve the confusion
3. Check if the limitation is already documented in `#1_PRD.md` Section 6 — if it is, it is by design
4. Test on a real Android device if you haven't already — emulator results are unreliable for this app
5. Invoke **Architect Agent** — if something is too complex, the architecture decision was probably wrong

---

## FINAL REMINDER

This is a single-developer, personal-use-first project. The goal of the MVP is:

**One person can install this app, set a timer, and genuinely not be able to bypass the block alone.**

Every line of code either serves that goal or it doesn't belong in this version. When in doubt, go back to `@1_PRD.md` Section 2 and Section 3. That is your north star.

**And before any code: check the Agent Roster. The right agent first, always.**
