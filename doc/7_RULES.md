# AI Agent Skill Usage Rules
## FocusLock — Android App
## When to use which skill — strict rules for every session

---

## HOW TO READ THIS FILE

Each rule follows this format:

```
WHEN [situation/task]
USE  @skill-name
```

Read the situation, match it to your current task, invoke the skill before writing any code.
If multiple rules match, invoke all listed skills in the order shown.

---

## RULE 1 — Starting any new feature or session

**WHEN** you are about to start building any new feature, screen, service, or module and the requirement is not fully clear in your head yet.

**USE** `@brainstorming` → then `@concise-planning`

Do not write a single line of code before these two. Brainstorming surfaces what you actually need to build. Concise planning turns that into a step-by-step task list. Skipping this is the number one reason vibe coding sessions go off track.

---

## RULE 2 — Building any Android-specific component

**WHEN** you are working on anything that is Android-specific:
- Activities, Fragments, Services
- Accessibility Service (`FocusAccessibilityService`)
- VPN Service (`FocusVpnService`)
- Device Admin Receiver (`FocusDeviceAdminReceiver`)
- BroadcastReceiver (boot recovery)
- Foreground Service + notifications
- WorkManager tasks
- Android permissions flow
- Jetpack Compose screens
- ViewModel, StateFlow, UseCase classes

**USE** `@mobile-developer`

This is your primary skill for all Android code. Invoke it at the start of every Android coding session.

---

## RULE 3 — Writing or reviewing Kotlin coroutines and Flow

**WHEN** you are:
- Writing any `suspend fun`
- Using `Flow`, `StateFlow`, `SharedFlow`
- Using `viewModelScope`, `lifecycleScope`, `coroutineScope`
- Handling coroutine exceptions or cancellation
- Writing async database or network calls
- Testing suspending functions

**USE** `@kotlin-coroutines-expert`

Never write async Kotlin code without this skill active. It prevents the most common coroutine mistakes — GlobalScope leaks, swallowed CancellationException, blocking the main thread.

---

## RULE 4 — Designing or reviewing app architecture

**WHEN** you are:
- Setting up the MVVM + Clean Architecture layers
- Deciding where a piece of logic belongs (UI vs ViewModel vs UseCase vs Repository)
- Creating new modules or packages
- Reviewing if the folder structure makes sense
- Deciding between two implementation approaches

**USE** `@software-architecture` → then `@architecture`

Use `@software-architecture` for the decision. Use `@architecture` when you need to document the decision as an ADR (Architecture Decision Record).

---

## RULE 5 — Designing or updating the database schema

**WHEN** you are:
- Creating or modifying a Room entity
- Adding a new DAO or DAO method
- Writing database migrations
- Adding indexes
- Deciding how to model relationships between entities

**USE** `@database-design`

Never add a new table or column without running this skill first. The data model is the foundation — a bad schema decision costs hours to fix later.

---

## RULE 6 — Building the backend server (Node.js / TypeScript)

**WHEN** you are working on any backend code:
- Express routes (`/verify-contact`, `/request-unlock`, `/verify-unlock-otp`, `/confirm-contact`)
- Middleware (validation, error handling)
- OTP generation and hashing logic
- In-memory OTP store
- Server setup in `app.ts` or `index.ts`
- Any TypeScript types or interfaces for the backend

**USE** `@backend-dev-guidelines` → then `@typescript-expert`

Always apply both. `@backend-dev-guidelines` covers Node.js/Express patterns. `@typescript-expert` ensures the TypeScript is correct, strict, and type-safe throughout.

---

## RULE 7 — Designing or building any API endpoint

**WHEN** you are:
- Designing the shape of a new endpoint (request body, response, status codes)
- Deciding between REST patterns
- Writing Retrofit interface definitions in the Android app
- Writing Zod validation schemas on the backend
- Reviewing API contracts between Android app and backend

**USE** `@api-design-principles` → then `@api-patterns`

`@api-design-principles` defines the correct shape and contract. `@api-patterns` covers the implementation patterns (REST, error responses, versioning).

---

## RULE 8 — Integrating Twilio SMS

**WHEN** you are:
- Setting up the Twilio client in the backend
- Writing the SMS send function in `sms.service.ts`
- Handling Twilio errors and delivery failures
- Testing SMS delivery
- Managing phone number formatting for international numbers

**USE** `@twilio-communications`

---

## RULE 9 — Implementing any security-sensitive feature

**WHEN** you are building:
- OTP generation (`crypto.randomInt`)
- OTP hashing (`bcrypt`)
- OTP verification logic
- Phone number storage in Room
- Backend API endpoint security (rate limiting, input validation)
- HTTPS enforcement
- Any authentication or verification flow (trusted contact verification)

**USE** `@api-security-best-practices` → then `@auth-implementation-patterns` → then `@backend-security-coder`

Run all three in order. Security corners are the most expensive to cut. The OTP system handles real bypass attempts — it must be correct.

---

## RULE 10 — Building anything on the Android side that touches security

**WHEN** you are:
- Storing phone numbers in Room (sensitive data)
- Handling Device Admin activation and deactivation
- Managing session state that controls lock/unlock
- Building the overlay service that blocks settings screens
- Writing any permission handling code
- Ensuring logs don't leak sensitive data

**USE** `@mobile-security-coder`

Android security has different concerns than backend security. This skill covers mobile-specific attack surfaces — data storage, permission misuse, intent vulnerabilities.

---

## RULE 11 — Designing any screen UI

**WHEN** you are:
- Designing a new Compose screen (layout, spacing, colors, typography)
- Building reusable composable components
- Deciding on user flow between screens
- Working on the blocking overlay UI
- Working on the active session countdown screen
- Building the onboarding permission screens

**USE** `@mobile-design`

The UI of this app needs to feel calm, trustworthy, and deliberate — not aggressive or alarming. Run this skill before writing any Compose UI code to ensure design consistency.

---

## RULE 12 — Writing tests

**WHEN** you are:
- Writing unit tests for any UseCase
- Writing unit tests for any ViewModel
- Writing unit tests for any Repository
- Writing unit tests for OTP generation or verification logic
- Writing Flow tests
- Writing suspend function tests
- Planning what to test before building a feature (TDD)

**USE** `@test-driven-development` → then `@testing-patterns` → then `@kotlin-coroutines-expert`

`@test-driven-development` for the TDD mindset and test-first approach.
`@testing-patterns` for Jest/test structure (applies to backend Node.js tests).
`@kotlin-coroutines-expert` for testing suspend functions and Flows with `runTest` and `Turbine`.

---

## RULE 13 — Debugging a broken feature

**WHEN** you are:
- The feature is not working and you don't know why
- A service is not starting or is being killed by the OS
- The accessibility service is not detecting browser URLs
- The VPN is not intercepting DNS queries
- The Device Admin is not preventing uninstall
- An OTP is not being sent or verified correctly
- A coroutine is hanging, leaking, or throwing unexpectedly
- A Room query is returning wrong data

**USE** `@systematic-debugging` → then `@error-handling-patterns`

`@systematic-debugging` gives a step-by-step process for finding the root cause. `@error-handling-patterns` helps you handle it correctly once found. Do not guess — follow the process.

---

## RULE 14 — Reviewing or cleaning up code

**WHEN** you are:
- Reviewing a completed feature before moving to the next
- Cleaning up messy code after a fast vibe coding session
- Checking if a class violates Single Responsibility
- Checking if MVVM layers are bleeding into each other
- Looking for code smells, unused variables, dead code

**USE** `@uncle-bob-craft` → then `@production-code-audit`

`@uncle-bob-craft` applies Clean Code and SOLID principles. `@production-code-audit` does a deeper line-by-line scan for production-readiness issues. Run both before marking any feature as done.

---

## RULE 15 — Linting and validating code

**WHEN** you are:
- Before any git commit
- After finishing a coding session
- Before testing on a real device
- After a big refactor

**USE** `@lint-and-validate`

Non-negotiable. Every commit must pass lint. Kotlin: run `ktlint`. TypeScript: run `eslint` with strict rules. Do not commit code that hasn't been linted.

---

## RULE 16 — Committing and pushing code

**WHEN** you are:
- Ready to save your work after a session
- After a feature is complete and tested on device
- After a bug fix is verified

**USE** `@git-pushing`

Commit messages must be meaningful. Format: `[FEATURE] Add AccessibilityService URL detection` not `"fixed stuff"`. Each commit should be one logical unit of work.

---

## QUICK REFERENCE TABLE

| Task | Primary Skill | Secondary Skill |
|---|---|---|
| Start a new feature | `@brainstorming` | `@concise-planning` |
| Any Android code | `@mobile-developer` | — |
| Kotlin coroutines / Flow | `@kotlin-coroutines-expert` | — |
| Architecture decisions | `@software-architecture` | `@architecture` |
| Room / database schema | `@database-design` | — |
| Backend (Node.js/TS) | `@backend-dev-guidelines` | `@typescript-expert` |
| API design / Retrofit | `@api-design-principles` | `@api-patterns` |
| Twilio SMS | `@twilio-communications` | — |
| OTP / auth security | `@api-security-best-practices` | `@auth-implementation-patterns` |
| Android security | `@mobile-security-coder` | `@backend-security-coder` |
| Compose UI design | `@mobile-design` | — |
| Writing tests | `@test-driven-development` | `@testing-patterns` |
| Debugging | `@systematic-debugging` | `@error-handling-patterns` |
| Code review / cleanup | `@uncle-bob-craft` | `@production-code-audit` |
| Lint before commit | `@lint-and-validate` | — |
| Git commit & push | `@git-pushing` | — |

---

## ENFORCEMENT RULES

These are non-negotiable. If any of these are violated, stop and fix before continuing.

**Rule E1:** Never write AccessibilityService code without `@mobile-developer` and `@mobile-security-coder` both active in the session. This service has direct access to screen content — security cannot be an afterthought.

**Rule E2:** Never write OTP generation, hashing, or verification code without `@api-security-best-practices` active. A flawed OTP system breaks the entire trusted contact unlock feature.

**Rule E3:** Never add a new Room entity, column, or DAO method without `@database-design`. Schema changes after data is stored are expensive and risky.

**Rule E4:** Never push code that has not been linted. Run `@lint-and-validate` before every `@git-pushing`.

**Rule E5:** Never ship a feature to the real device without at least one unit test for the core logic. Run `@test-driven-development` before writing the implementation, not after.

**Rule E6:** If a session lasted more than 2 hours, run `@production-code-audit` before ending. Long sessions accumulate technical debt fast.
