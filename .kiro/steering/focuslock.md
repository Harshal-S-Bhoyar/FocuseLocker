# FocusLock — Project Steering File
## Kiro AI Development Context

> **ALWAYS follow `doc/MASTER.md` and this steering file for every response in this project.**
> MASTER.md is the single source of truth for build order, scope, and document references.
> This steering file is the live session state — it is updated as work progresses.

---

## WHAT THIS PROJECT IS

FocusLock is a production-intended Android app that helps users voluntarily block distracting browser content for a self-set time period they cannot cancel alone.

**Core promise:** Once a session starts, the user cannot stop it without their trusted contact's help.

**Three core technical mechanisms:**
1. **Accessibility Service** — reads browser URL bars across all Android browsers for keyword matching
2. **Local on-device VPN** — intercepts DNS to block entire domains by category (no external VPN server)
3. **Device Admin API** — prevents app uninstallation during an active session

**Backend does one thing only:** OTP generation and Twilio SMS delivery. It stores nothing permanently.

---

## DEVELOPMENT ENVIRONMENT

This project is developed in a **restricted corporate environment**:
- No admin rights on the development machine
- No Android Studio installed
- Android SDK Command Line Tools installed to user folder: `C:\Users\harshal.bhoyar\AndroidSDK`
- Build tool: `gradlew.bat` (Gradle Wrapper — no system Gradle needed)
- IDE: VS Code or IntelliJ IDEA Community (portable)
- Deploy: `adb install` to a physical Android device via USB

**Build commands:**
```cmd
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat | findstr "FocusLock"
```

**No emulator.** Physical device only. Emulators do not support VPN Service or Device Admin reliably. Accessibility Service behaves differently on emulators.

---

## TECH STACK — NON-NEGOTIABLE

### Android App
| Layer | Technology |
|---|---|
| Language | Kotlin 2.0+ only. No Java. No mixed files. |
| UI | Jetpack Compose + Material 3. No XML layouts. No View-based UI. |
| Architecture | MVVM + Clean Architecture: UI → ViewModel → UseCase → Repository → DataSource |
| DI | Hilt. No manual DI. No service locator. |
| Database | Room with Flow. No raw SQLite. |
| Async | Kotlin Coroutines only. No RxJava. No callbacks. No manual threads. |
| Networking | Retrofit + OkHttp + Kotlin Serialization. No Gson. No Moshi. |
| Timer | WorkManager only. Nothing else survives process death reliably. |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

### Backend
| Layer | Technology |
|---|---|
| Runtime | Node.js 20 LTS |
| Framework | Express 4 + TypeScript |
| SMS | Twilio SMS API |
| OTP hashing | bcrypt — never store plain OTPs |
| OTP generation | `crypto.randomInt` — never `Math.random` |
| Validation | zod on every endpoint — no unvalidated inputs |
| Storage | In-memory Map with TTL only — no database, no disk writes |
| Hosting | Railway.app |

---

## PROJECT FOLDER STRUCTURE

```
app/src/main/java/com/focuslock/
├── data/
│   ├── local/
│   │   ├── dao/          ← Room DAOs
│   │   ├── entity/       ← Room entities
│   │   └── FocusDatabase.kt
│   ├── remote/
│   │   ├── api/          ← Retrofit interfaces
│   │   └── dto/          ← Request/Response data classes
│   └── repository/       ← Repository implementations
├── domain/
│   ├── model/            ← Domain models (NOT entities)
│   ├── repository/       ← Repository interfaces
│   └── usecase/          ← One file per use case
├── service/
│   ├── accessibility/    ← FocusAccessibilityService
│   ├── vpn/              ← FocusVpnService
│   ├── overlay/          ← BlockingOverlayService
│   └── admin/            ← FocusDeviceAdminReceiver
├── ui/
│   ├── onboarding/
│   ├── setup/
│   ├── session/
│   ├── unlock/
│   ├── theme/            ← Colors, Typography, Theme
│   └── components/       ← Shared composables
├── worker/               ← WorkManager workers
├── receiver/             ← BroadcastReceivers
├── di/                   ← Hilt modules
└── util/                 ← Extensions, helpers
```

Every new file must go in the correct folder. Do not create new top-level folders without a clear reason.

---

## CODING RULES — ENFORCE ON EVERY FILE

### Kotlin
- No `!!` anywhere. Handle nulls with `?.`, `?:`, or `requireNotNull()`
- No `var` when `val` is possible
- No `Any` as a type — always use specific types
- No `runBlocking` in production code
- No `GlobalScope` — use `viewModelScope` or `lifecycleScope`
- No `TODO()` in completed code — implement it or throw `NotImplementedError("reason")`
- All user-facing strings in `res/values/strings.xml` — no string literals in code
- No hardcoded colors or dimensions in composables — use theme tokens only

### Architecture
- No business logic in Composable functions
- No business logic in ViewModels — ViewModels only hold state and delegate to UseCases
- One UseCase per user action (`ActivateSessionUseCase`, `RequestUnlockUseCase`, etc.)
- No direct DAO calls from ViewModel — always go through Repository
- No Retrofit calls from ViewModel — always go through Repository
- Domain models and Room entities are separate classes — map in Repository layer
- Repository interfaces in `domain/repository/`, implementations in `data/repository/`

### Compose UI
- Every screen is a stateless Composable receiving state and callbacks as parameters
- State hoisted to ViewModel via `StateFlow`
- Use `collectAsStateWithLifecycle()` not `collectAsState()`
- Loading/error/success states modelled as sealed class `UiState`
- Navigation callbacks passed as lambdas — no NavController directly in composables

### Room
- Every DAO read returns `Flow<T>` — never a one-shot result for reactive data
- Every DAO write is a `suspend fun`
- Always define indexes on columns used in WHERE clauses
- `blocked_domains` table has 5,000–20,000 rows — index on `domain` is mandatory

### Services
- Every service running during an active session must be a foreground service with a visible notification
- Services must not crash silently — wrap event handling in `try/catch`
- Always clean up resources in `onDestroy()` — close sockets, file descriptors, unregister receivers

### Security
- Never log phone numbers or OTPs — even in debug builds
- Use `BuildConfig.DEBUG` to gate all debug logging
- Never store OTPs on device — server holds OTP hashes only
- Backend URL from `BuildConfig` — never hardcoded
- All backend API calls use HTTPS only

---

## DATABASE RULES — CRITICAL

These rules from `doc/4_DATA_MODEL.md` are enforced in code:

| Rule | Enforcement |
|---|---|
| Only one `ACTIVE` session at a time | Checked in `SessionRepository` before insert |
| `expires_at` is the single source of truth | Never use any other field to determine session end |
| Keywords stored in lowercase always | Normalized in `BlockingRepository.addKeyword()` |
| Maximum 50 custom keywords | Enforced in `BlockingRepository.addKeyword()` |
| Index on `blocked_domains.domain` | Defined in `BlockedDomainEntity` |
| Maximum 1 trusted contact row | Enforced in `TrustedContactRepository` |
| Backend uses no database | In-memory Map with 10-minute TTL only |

Do not add new tables or columns without reading `doc/4_DATA_MODEL.md` first.

---

## WHAT HAS BEEN BUILT

### ✅ Phase 1 — Project Foundation (COMPLETE)

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`
- `app/build.gradle.kts` — all dependencies declared
- `AndroidManifest.xml` — all permissions and service declarations
- `res/xml/accessibility_service_config.xml`, `device_admin.xml`, `data_extraction_rules.xml`
- `res/values/strings.xml`, `themes.xml`
- `proguard-rules.pro`, `gradle/wrapper/gradle-wrapper.properties`
- All 7 Room entities: `SessionEntity`, `BlockedKeywordEntity`, `BlockedCategoryEntity`, `BlockedDomainEntity`, `TrustedContactEntity`, `UnlockRequestEntity`, `AppSettingEntity`
- All 7 Room DAOs: `SessionDao`, `BlockedKeywordDao`, `BlockedCategoryDao`, `BlockedDomainDao`, `TrustedContactDao`, `UnlockRequestDao`, `AppSettingDao`
- `FocusDatabase.kt`
- `DatabaseModule.kt`, `NetworkModule.kt`, `RepositoryModule.kt` (Hilt)
- `FocusLockApi.kt` (Retrofit interface)
- All 4 DTOs: `VerifyContactRequest`, `ConfirmContactRequest`, `RequestUnlockRequest`, `VerifyUnlockOtpRequest`
- `FocusLockApplication.kt`, `MainActivity.kt` (with navigation wired)
- `Color.kt`, `Typography.kt`, `Theme.kt`
- All 5 domain models: `Session`, `BlockedKeyword`, `BlockedCategory`, `TrustedContact`, `UnlockRequest`
- All 5 repository interfaces + all 5 implementations
- `AppNavigation.kt` — all screen routes defined with placeholder composables
- `assets/blocked_domains.json` — bundled domain blocklist (120 domains across 6 categories)

### ✅ Phase 2 — Onboarding & Permissions (COMPLETE)

- `InitializeAppUseCase.kt`, `CheckOnboardingStatusUseCase.kt`
- `SaveTrustedContactUseCase.kt`, `VerifyContactOtpUseCase.kt`
- `UiState.kt` (shared sealed class)
- `PermissionChecker.kt` (util)
- `OnboardingScreen.kt` + `OnboardingViewModel.kt`
- `PermissionSetupScreen.kt` + `PermissionSetupViewModel.kt`
- `TrustedContactSetupScreen.kt` + `TrustedContactSetupViewModel.kt`
- `VerifyContactScreen.kt` + `VerifyContactViewModel.kt`
- `AppNavigation.kt` — Phase 2 screens live, Phases 4–6 still placeholder
- `strings.xml` — all Phase 2 strings added
- Unit tests: `SaveTrustedContactUseCaseTest`, `VerifyContactOtpUseCaseTest`, `TrustedContactSetupViewModelTest`

### 🔄 Phase 3 — Core Blocking Services (CURRENT)

**Agents to invoke (in order):**
1. Architect Agent — architecture plan for services
2. Accessibility Agent — `FocusAccessibilityService`
3. VPN Agent — `FocusVpnService`
4. Android Compose Agent — `BlockingOverlayService` UI
5. Security Agent — mandatory review on all 3 services
6. Testing Agent — unit tests
7. Code Reviewer Agent — final review

**To build next (in order):**
1. `FocusAccessibilityService.kt` — URL extraction from all browsers, keyword matching, settings screen detection (US-07, US-09)
2. `FocusVpnService.kt` — local TUN interface, DNS interception, domain blocking (US-08)
3. `BlockingOverlayService.kt` — `TYPE_APPLICATION_OVERLAY` window, cannot be dismissed during session
4. Settings screen protection — overlay on Accessibility/VPN/Device Admin settings screens

**Reference:** `doc/3_SYSTEM_DESIGN.md` Sections 2.2, 2.3, 2.7, Data Flow Section 5

### Phases 4–7 — Not started

---

## BUILD ORDER — DO NOT SKIP PHASES

```
Phase 1  → Project foundation (current)
Phase 2  → Onboarding + permissions (US-01, US-02)
Phase 3  → Core blocking services (AccessibilityService, VpnService, Overlay)
Phase 4  → Session management (SessionManager, WorkManager, Device Admin, UI screens)
Phase 5  → Backend OTP server (Node.js + Twilio + Railway deploy)
Phase 6  → Unlock flow (OTP entry, cooldown, emergency bypass)
Phase 7  → End-to-end testing on real device
```

---

## HARD LIMITS — NEVER BUILD THESE

- iOS version
- Multiple trusted contacts
- Usage analytics or session history display
- Scheduled or recurring sessions
- Browser extension
- Web dashboard
- Parental control mode
- Social features, leaderboards, streaks
- Paid/subscription tier
- Push notifications (SMS only)
- User account/login system
- External VPN server
- Server-side storage of keywords, categories, or browsing data

Source: `doc/1_PRD.md` Section 4

---

## KNOWN LIMITATIONS — ACCEPT, DO NOT FIX

| Limitation | Reason |
|---|---|
| Factory reset bypasses everything | OS-level — no app can prevent this |
| Force stop temporarily kills services | Mitigated by foreground service |
| Trusted contact can forward OTP | Mitigated by context message in SMS |
| Accessibility Service killed by battery optimization | Mitigated by battery exemption request on install |
| SMS delivery not guaranteed in all regions | Mitigated by retry logic and failure UI |

Source: `doc/1_PRD.md` Section 6

---

## REFERENCE DOCUMENTS

| Document | Purpose |
|---|---|
| `doc/1_PRD.md` | What to build — features, scope, user flow |
| `doc/2_USER_STORIES.md` | Acceptance criteria for every screen and feature |
| `doc/3_SYSTEM_DESIGN.md` | Architecture, component connections, data flows |
| `doc/4_DATA_MODEL.md` | Complete Room schema — all tables, columns, rules |
| `doc/5_TECH_STACK.md` | Every technology choice + folder structure |
| `doc/6_CONTEXT_AND_RULES.md` | Coding rules + session checklist + TODO log |
| `doc/7_RULES.md` | Which skill to use for which task |
