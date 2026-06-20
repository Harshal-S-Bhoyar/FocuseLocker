# Global Logging System — Implementation Plan
## FocusLocker Project (Android + Node.js Backend)

**Created:** 2026-06-17  
**Status:** PLAN  
**Scope:** Full-stack — Android app (Kotlin) + Backend (TypeScript/Node.js)

---

## 1. Current State Audit

### 1.1 What EXISTS Today

| Component | Status | Details |
|-----------|--------|---------|
| `FileLoggingTree.kt` | ✅ Exists | Timber tree → writes to daily log files at `getExternalFilesDir/log/` |
| `GlobalExceptionHandler.kt` | ✅ Exists | Catches uncaught exceptions → logs via Timber before crash |
| `FocusLockApplication.kt` | ✅ Exists | Plants `DebugTree` (debug only) + `FileLoggingTree` (always) |
| Timber dependency | ✅ Exists | Already in build.gradle |

### 1.2 Files WITH Timber Logging (10 files)

| File | Layer | Log Coverage |
|------|-------|-------------|
| `FocusLockApplication.kt` | App Init | ✅ Tree planting only — no lifecycle events logged |
| `GlobalExceptionHandler.kt` | Crash | ✅ Fatal exceptions only |
| `FileLoggingTree.kt` | Infra | ✅ File writer — no rotation, no size limits |
| `VpnPacketLoop.kt` | Service/VPN | ⚠️ Partial — blocks/errors logged, but no start/stop/stats |
| `FocusVpnService.kt` | Service/VPN | ⚠️ Partial — start/blocklist logs, no stop/revoke/destroy |
| `BootReceiver.kt` | Receiver | ⚠️ Partial — recovery logged, but no failure details |
| `FocusAccessibilityService.kt` | Service | ⚠️ Minimal — connect/interrupt only, no URL detection events |
| `BlockingOverlayService.kt` | Service | ⚠️ Error-only — no show/hide/lifecycle events |
| `FocusDeviceAdminReceiver.kt` | Service | ⚠️ Enable/disable only |
| `SaveTrustedContactUseCase.kt` | UseCase | ⚠️ Error-only — no success path logging |

### 1.3 Files with ZERO Logging (Critical Gaps)

#### Workers (5 files — background tasks, HIGH priority)
| File | Risk | Why It Needs Logging |
|------|------|---------------------|
| `CooldownTimerWorker.kt` | 🔴 HIGH | Silent failures = user stuck in cooldown |
| `EmergencyBypassWorker.kt` | 🔴 HIGH | Emergency unlock failures are safety-critical |
| `ListSyncWorker.kt` | 🔴 HIGH | API sync failures = stale blocklists |
| `SessionExpiryWorker.kt` | 🔴 HIGH | Timer failures = infinite sessions |
| `SessionTimerWorker.kt` | 🔴 HIGH | Timer tick failures = wrong countdown display |

#### Use Cases (20+ files — business logic, MEDIUM-HIGH priority)
| File | Risk | Why It Needs Logging |
|------|------|---------------------|
| `ActivateSessionUseCase.kt` | 🔴 HIGH | Session start failures invisible |
| `DeactivateSessionUseCase.kt` | 🔴 HIGH | Session end failures invisible |
| `CheckUrlForBlockedContentUseCase.kt` | 🔴 HIGH | Blocking bypass goes undetected |
| `SmartScanUseCase.kt` | 🔴 HIGH | Scan failures = content bypass |
| `DoHQueryUseCase.kt` | 🟡 MED | DNS failures need traceability |
| `MetaDescriptionFetcher.kt` | 🟡 MED | Network fetch failures |
| `KeywordScanner.kt` | 🟡 MED | Scan performance tracking |
| `RequestUnlockUseCase.kt` | 🟡 MED | Unlock flow audit trail |
| `VerifyUnlockOtpUseCase.kt` | 🟡 MED | OTP verification audit trail |
| `VerifyContactOtpUseCase.kt` | 🟡 MED | Contact verification audit trail |
| `InitializeAppUseCase.kt` | 🟡 MED | App init sequence tracking |
| `StartEmergencyBypassUseCase.kt` | 🟡 MED | Emergency bypass audit trail |
| `CleanupStaleSessionUseCase.kt` | 🟢 LOW | Cleanup events |
| `AddKeywordUseCase.kt` | 🟢 LOW | Keyword CRUD events |
| `RemoveKeywordUseCase.kt` | 🟢 LOW | Keyword CRUD events |
| `ToggleCategoryUseCase.kt` | 🟢 LOW | Category toggle events |
| `CheckOnboardingStatusUseCase.kt` | 🟢 LOW | Onboarding flow |
| `GetActiveBlockedDomainsUseCase.kt` | 🟢 LOW | Query events |
| `AhoCorasick.kt` | 🟢 LOW | Pattern matching engine |
| `DomainExtractor.kt` | 🟢 LOW | URL parsing |

#### Repositories (5 files — data layer, MEDIUM priority)
| File | Risk | Why It Needs Logging |
|------|------|---------------------|
| `BlockingRepositoryImpl.kt` | 🟡 MED | Seed failures, category toggles |
| `SessionRepositoryImpl.kt` | 🟡 MED | Session CRUD operations |
| `UnlockRepositoryImpl.kt` | 🟡 MED | OTP/unlock API calls |
| `TrustedContactRepositoryImpl.kt` | 🟡 MED | Contact verification API calls |
| `AppSettingsRepositoryImpl.kt` | 🟢 LOW | Settings changes |

#### UI Layer (ViewModels + Screens)
| File | Risk | Why It Needs Logging |
|------|------|---------------------|
| `OnboardingViewModel.kt` | 🟡 MED | Onboarding errors swallowed |
| `ActiveSessionScreen.kt` | 🟡 MED | UI-level exception handling |
| All other ViewModels | 🟢 LOW | User action tracking |

#### DI / Network / Notification
| File | Risk | Why It Needs Logging |
|------|------|---------------------|
| `NetworkModule.kt` | 🟡 MED | OkHttp interceptor logging |
| `DatabaseModule.kt` | 🟡 MED | DB creation/migration events |
| `SessionNotificationManager.kt` | 🟡 MED | Notification show/cancel events |

#### Backend (Node.js/TypeScript)
| File | Risk | Why It Needs Logging |
|------|------|---------------------|
| `app.ts` | 🔴 HIGH | No structured logging — `console.log` only |
| `server.ts` | 🔴 HIGH | No startup/shutdown logs |
| `contact.routes.ts` | 🔴 HIGH | No request/response logging |
| `unlock.routes.ts` | 🔴 HIGH | No request/response logging |
| `otp.service.ts` | 🔴 HIGH | OTP generation/verification not logged |
| `email.service.ts` | 🔴 HIGH | Email delivery failures silent |
| `validate.ts` | 🟡 MED | Validation failure logging |

### 1.4 Infrastructure Deficiencies

| Issue | Impact |
|-------|--------|
| No log rotation | Disk fills up on production devices |
| No max file size | Single log file can grow unbounded |
| No log retention policy | Old logs never cleaned |
| No log levels configurable at runtime | Cannot switch to DEBUG on user's device |
| No coroutine context in log | Cannot trace async operations |
| No structured log format | Hard to parse/search logs programmatically |
| No OkHttp request/response logging | Network issues invisible |
| Backend uses raw `console.log` | No structured logging, no levels, no file output |

---

## 2. Architecture Design

### 2.1 Android — Enhanced Timber Infrastructure

```
┌──────────────────────────────────────────────────────┐
│                  FocusLockApplication                 │
│                                                      │
│  Timber.plant(DebugTree)        ← Logcat (debug)     │
│  Timber.plant(FileLoggingTree)  ← File (always)      │
│  Timber.plant(CrashReportTree)  ← NEW: Crash service │
│                                                      │
│  GlobalExceptionHandler         ← Uncaught crashes   │
│  CoroutineExceptionLogger       ← NEW: Coroutine     │
│  OkHttpLoggingInterceptor       ← NEW: Network       │
└──────────────────────────────────────────────────────┘
```

### 2.2 Log Categories (TAG Convention)

```
TAG FORMAT: [LAYER]_[COMPONENT]

Examples:
  SVC_VPN          — VPN service events
  SVC_ACCESSIBILITY — Accessibility service events
  SVC_OVERLAY      — Overlay service events
  SVC_ADMIN        — Device admin events
  WRK_SESSION      — Session timer/expiry workers
  WRK_SYNC         — List sync worker
  WRK_COOLDOWN     — Cooldown timer worker
  WRK_BYPASS       — Emergency bypass worker
  UC_SESSION       — Session activate/deactivate
  UC_SCAN          — SmartScan, keyword scan
  UC_BLOCK         — URL blocking logic
  UC_UNLOCK        — Unlock/OTP flow
  UC_DNS           — DNS queries
  UC_NET           — MetaDescriptionFetcher, network
  REPO_BLOCK       — Blocking repository
  REPO_SESSION     — Session repository
  REPO_UNLOCK      — Unlock repository
  REPO_CONTACT     — Trusted contact repository
  UI_ONBOARD       — Onboarding flow
  UI_SESSION       — Active session screen
  NET_HTTP         — OkHttp request/response
  DB_INIT          — Database creation/migration
  APP_INIT         — Application startup sequence
  APP_CRASH        — Fatal exceptions
  NOTIF            — Notification events
  BOOT             — Boot recovery
```

### 2.3 Log Levels Policy

| Level | When to Use | Example |
|-------|-------------|---------|
| `Timber.v()` | High-frequency events, DNS queries, packet processing | `"DNS query for google.com"` |
| `Timber.d()` | Lifecycle events, state changes, method entry/exit | `"Session activated: duration=30min"` |
| `Timber.i()` | Business events worth noting in production | `"VPN started with 150 blocked domains"` |
| `Timber.w()` | Recoverable issues, fallbacks triggered | `"NextDNS timeout, using fallback resolver"` |
| `Timber.e()` | Failures that affect functionality | `"Failed to activate session: DB write error"` |

### 2.4 Backend — Winston Structured Logging

```
┌──────────────────────────────────────┐
│           Winston Logger             │
│                                      │
│  Transport: Console (dev)            │
│  Transport: File (production)        │
│  Format: JSON structured             │
│                                      │
│  Middleware: Request logger           │
│  Middleware: Error logger             │
│  Service-level: OTP, Email           │
└──────────────────────────────────────┘
```

---

## 3. Implementation Tasks

### Phase 1: Android Infrastructure Hardening (FileLoggingTree)

| # | Task | File | Verify |
|---|------|------|--------|
| 1.1 | Add log rotation — max 5MB per file, max 7 days retention | `FileLoggingTree.kt` | Create >5MB log → confirm auto-rotation |
| 1.2 | Add buffered writing — batch writes every 2 seconds | `FileLoggingTree.kt` | Confirm no I/O per log call |
| 1.3 | Add runtime log-level filter (SharedPreferences) | `FileLoggingTree.kt` | Toggle level → confirm filter applies |
| 1.4 | Add thread/coroutine name in log format | `FileLoggingTree.kt` | Log from IO dispatcher → confirm thread name visible |
| 1.5 | Add startup banner log with app version + build type | `FocusLockApplication.kt` | App start → confirm version in first log line |

### Phase 2: Service Layer Logging

| # | Task | File | Verify |
|---|------|------|--------|
| 2.1 | Log VPN start/stop/revoke/destroy lifecycle | `FocusVpnService.kt` | VPN lifecycle → all events in log |
| 2.2 | Log packet loop start/stop/restart with stats (packets processed, blocked count) | `VpnPacketLoop.kt` | Run 1 min → confirm stats logged |
| 2.3 | Log accessibility service URL detection events | `FocusAccessibilityService.kt` | Visit URL in browser → log entry visible |
| 2.4 | Log overlay show/hide/dismiss with target domain | `BlockingOverlayService.kt` | Trigger block → show/hide logged |
| 2.5 | Log device admin enable/disable/password-change events | `FocusDeviceAdminReceiver.kt` | Toggle admin → events logged |

### Phase 3: Worker Logging (All 5 Workers)

| # | Task | File | Verify |
|---|------|------|--------|
| 3.1 | Log worker start, success, retry, failure for `CooldownTimerWorker` | `CooldownTimerWorker.kt` | Trigger cooldown → all states logged |
| 3.2 | Log worker lifecycle for `EmergencyBypassWorker` | `EmergencyBypassWorker.kt` | Trigger bypass → lifecycle logged |
| 3.3 | Log sync start/count/errors for `ListSyncWorker` | `ListSyncWorker.kt` | Trigger sync → API call + result logged |
| 3.4 | Log timer expiry decisions for `SessionExpiryWorker` | `SessionExpiryWorker.kt` | Session expires → decision logged |
| 3.5 | Log tick events + remaining time for `SessionTimerWorker` | `SessionTimerWorker.kt` | Timer ticks → logged at INFO level |

### Phase 4: Use Case Logging (20+ Files)

| # | Task | Files | Verify |
|---|------|-------|--------|
| 4.1 | Add entry/exit + error logging to session use cases | `ActivateSessionUseCase.kt`, `DeactivateSessionUseCase.kt`, `CleanupStaleSessionUseCase.kt` | Session flow → entry/exit/error in log |
| 4.2 | Add entry/exit + error logging to blocking use cases | `CheckUrlForBlockedContentUseCase.kt`, `SmartScanUseCase.kt`, `KeywordScanner.kt` | Block event → full trace in log |
| 4.3 | Add entry/exit + error logging to unlock/OTP use cases | `RequestUnlockUseCase.kt`, `VerifyUnlockOtpUseCase.kt`, `VerifyContactOtpUseCase.kt` | Unlock flow → full audit trail |
| 4.4 | Add entry/exit + error logging to DNS use case | `DoHQueryUseCase.kt` | DNS query → logged |
| 4.5 | Add entry/exit + error logging to network use case | `MetaDescriptionFetcher.kt` | Fetch → URL + result/error logged |
| 4.6 | Add entry/exit + error logging to remaining use cases | `InitializeAppUseCase.kt`, `SaveTrustedContactUseCase.kt`, `StartEmergencyBypassUseCase.kt`, `AddKeywordUseCase.kt`, `RemoveKeywordUseCase.kt`, `ToggleCategoryUseCase.kt`, `CheckOnboardingStatusUseCase.kt` | Each operation → logged |

### Phase 5: Repository Layer Logging

| # | Task | File | Verify |
|---|------|------|--------|
| 5.1 | Log all API calls + responses in UnlockRepositoryImpl | `UnlockRepositoryImpl.kt` | API call → request/response logged |
| 5.2 | Log seed operations + category toggles in BlockingRepositoryImpl | `BlockingRepositoryImpl.kt` | Seed → count logged, toggle → logged |
| 5.3 | Log session CRUD in SessionRepositoryImpl | `SessionRepositoryImpl.kt` | Session create/update → logged |
| 5.4 | Log contact API calls in TrustedContactRepositoryImpl | `TrustedContactRepositoryImpl.kt` | Contact verify → logged |
| 5.5 | Log settings changes in AppSettingsRepositoryImpl | `AppSettingsRepositoryImpl.kt` | Setting change → logged |

### Phase 6: UI + DI + Network Layer Logging

| # | Task | File | Verify |
|---|------|------|--------|
| 6.1 | Add OkHttp `HttpLoggingInterceptor` (BASIC level in release, BODY in debug) | `NetworkModule.kt` | API call → HTTP method/URL/status in log |
| 6.2 | Log database creation + migration events | `DatabaseModule.kt` | First launch → DB version logged |
| 6.3 | Log notification show/cancel/update | `SessionNotificationManager.kt` | Session start → notification logged |
| 6.4 | Log ViewModel errors in OnboardingViewModel | `OnboardingViewModel.kt` | Error → logged |
| 6.5 | Log UI catch blocks in ActiveSessionScreen | `ActiveSessionScreen.kt` | Exception → logged with context |

### Phase 7: Coroutine Exception Logging

| # | Task | File | Verify |
|---|------|------|--------|
| 7.1 | Create `LoggingCoroutineExceptionHandler` utility | NEW: `util/LoggingCoroutineExceptionHandler.kt` | Coroutine crash → logged before propagation |
| 7.2 | Install handler in all `CoroutineScope` usages across services | All service files | Coroutine failure → appears in log file |

### Phase 8: Backend Structured Logging (Node.js)

| # | Task | File | Verify |
|---|------|------|--------|
| 8.1 | Add `winston` dependency + create `logger.ts` utility | NEW: `backend/src/utils/logger.ts` | Import works |
| 8.2 | Add request/response logging middleware | NEW: `backend/src/middleware/requestLogger.ts` | API call → method/path/status/duration logged |
| 8.3 | Add error logging middleware | `backend/src/app.ts` | Error → logged with stack trace |
| 8.4 | Replace all `console.log` with structured logger | `otp.service.ts`, `email.service.ts`, `server.ts`, all routes | grep `console.log` returns 0 hits |
| 8.5 | Add OTP generation/verification audit logging | `otp.service.ts` | OTP flow → full audit trail |
| 8.6 | Add email send success/failure logging | `email.service.ts` | Email → delivery status logged |

---

## 4. Log Event Catalog

### 4.1 Critical Events (MUST log, non-negotiable)

| Event | Level | TAG | Log Message Format |
|-------|-------|-----|-------------------|
| App startup | `i` | `APP_INIT` | `"App started: v{version} ({buildType})"` |
| App crash | `e` | `APP_CRASH` | `"FATAL: {exception} in {thread}"` |
| VPN start | `i` | `SVC_VPN` | `"VPN started: {domainCount} domains blocked"` |
| VPN stop | `i` | `SVC_VPN` | `"VPN stopped: reason={reason}"` |
| Domain blocked (DNS) | `d` | `SVC_VPN` | `"DNS BLOCKED: {domain}"` |
| Domain blocked (URL) | `d` | `UC_BLOCK` | `"URL BLOCKED: {url} reason={reason}"` |
| Session activated | `i` | `UC_SESSION` | `"Session activated: duration={min}min"` |
| Session deactivated | `i` | `UC_SESSION` | `"Session deactivated: reason={reason}"` |
| Session expired | `i` | `WRK_SESSION` | `"Session expired: id={sessionId}"` |
| Unlock requested | `i` | `UC_UNLOCK` | `"Unlock requested: email={masked}"` |
| OTP verified | `i` | `UC_UNLOCK` | `"OTP verified: success={bool}"` |
| Emergency bypass | `w` | `UC_UNLOCK` | `"Emergency bypass activated"` |
| Worker failed | `e` | `WRK_*` | `"Worker failed: {workerName} error={msg}"` |
| API call failed | `e` | `NET_HTTP` | `"API error: {method} {url} status={code}"` |
| DB migration | `i` | `DB_INIT` | `"DB migrated: v{from}→v{to}"` |
| Boot recovery | `i` | `BOOT` | `"Boot recovery: session={active/expired}"` |

### 4.2 Diagnostic Events (SHOULD log)

| Event | Level | TAG |
|-------|-------|-----|
| Accessibility URL detected | `d` | `SVC_ACCESSIBILITY` |
| SmartScan keyword matched | `d` | `UC_SCAN` |
| Category toggled | `d` | `REPO_BLOCK` |
| Keyword added/removed | `d` | `UC_BLOCK` |
| Notification shown/cancelled | `d` | `NOTIF` |
| Overlay shown/dismissed | `d` | `SVC_OVERLAY` |
| Setting changed | `d` | `REPO_SETTINGS` |
| Trusted contact saved | `d` | `REPO_CONTACT` |

### 4.3 Trace Events (MAY log — verbose only)

| Event | Level | TAG |
|-------|-------|-----|
| DNS query forwarded | `v` | `SVC_VPN` |
| Packet processed | `v` | `SVC_VPN` |
| DAO query executed | `v` | `DB_QUERY` |
| Worker tick | `v` | `WRK_*` |

---

## 5. File Inventory — All Files Requiring Changes

### New Files to Create (3)
1. `app/src/main/java/com/focuslock/util/LoggingCoroutineExceptionHandler.kt`
2. `backend/src/utils/logger.ts`
3. `backend/src/middleware/requestLogger.ts`

### Files to Modify (40+)

#### Infrastructure (3)
- `util/FileLoggingTree.kt` — rotation + buffering + level filter
- `util/GlobalExceptionHandler.kt` — enhanced crash metadata
- `FocusLockApplication.kt` — startup banner + coroutine handler

#### Services (5)
- `service/vpn/FocusVpnService.kt`
- `service/vpn/VpnPacketLoop.kt`
- `service/accessibility/FocusAccessibilityService.kt`
- `service/overlay/BlockingOverlayService.kt`
- `service/admin/FocusDeviceAdminReceiver.kt`

#### Workers (5)
- `worker/CooldownTimerWorker.kt`
- `worker/EmergencyBypassWorker.kt`
- `worker/ListSyncWorker.kt`
- `worker/SessionExpiryWorker.kt`
- `worker/SessionTimerWorker.kt`

#### Use Cases (20)
- `domain/usecase/ActivateSessionUseCase.kt`
- `domain/usecase/DeactivateSessionUseCase.kt`
- `domain/usecase/CheckUrlForBlockedContentUseCase.kt`
- `domain/usecase/SmartScanUseCase.kt`
- `domain/usecase/KeywordScanner.kt`
- `domain/usecase/DoHQueryUseCase.kt`
- `domain/usecase/MetaDescriptionFetcher.kt`
- `domain/usecase/RequestUnlockUseCase.kt`
- `domain/usecase/VerifyUnlockOtpUseCase.kt`
- `domain/usecase/VerifyContactOtpUseCase.kt`
- `domain/usecase/InitializeAppUseCase.kt`
- `domain/usecase/SaveTrustedContactUseCase.kt`
- `domain/usecase/StartEmergencyBypassUseCase.kt`
- `domain/usecase/AddKeywordUseCase.kt`
- `domain/usecase/RemoveKeywordUseCase.kt`
- `domain/usecase/ToggleCategoryUseCase.kt`
- `domain/usecase/CleanupStaleSessionUseCase.kt`
- `domain/usecase/CheckOnboardingStatusUseCase.kt`
- `domain/usecase/AhoCorasick.kt`
- `domain/usecase/DomainExtractor.kt`

#### Repositories (5)
- `data/repository/BlockingRepositoryImpl.kt`
- `data/repository/SessionRepositoryImpl.kt`
- `data/repository/UnlockRepositoryImpl.kt`
- `data/repository/TrustedContactRepositoryImpl.kt`
- `data/repository/AppSettingsRepositoryImpl.kt`

#### DI / Network / Notification (3)
- `di/NetworkModule.kt`
- `di/DatabaseModule.kt`
- `notification/SessionNotificationManager.kt`

#### UI (2+)
- `ui/onboarding/OnboardingViewModel.kt`
- `ui/session/ActiveSessionScreen.kt`

#### Receiver (1)
- `receiver/BootReceiver.kt`

#### Backend (6)
- `backend/src/app.ts`
- `backend/src/server.ts`
- `backend/src/routes/contact.routes.ts`
- `backend/src/routes/unlock.routes.ts`
- `backend/src/services/otp.service.ts`
- `backend/src/services/email.service.ts`

---

## 6. Acceptance Criteria

| # | Criteria | How to Verify |
|---|----------|---------------|
| AC-1 | Every `catch` block in the codebase logs the exception via Timber with context | `grep -r "catch" --include="*.kt" | wc -l` == `grep -r "Timber.e\|Timber.w" --include="*.kt" | wc -l` (within catch blocks) |
| AC-2 | Every service lifecycle event (start/stop/create/destroy) is logged | Manual lifecycle test → all events in log file |
| AC-3 | Every worker execution logs start + result (success/retry/failure) | Trigger each worker → verify log output |
| AC-4 | Every use case logs entry + exit (success/failure) for non-trivial operations | Execute each flow → verify log trace |
| AC-5 | Log files rotate at 5MB, retain max 7 days | Create >5MB log → old file rotated; wait 7 days → old files deleted |
| AC-6 | OkHttp interceptor logs all API requests/responses | Make API call → HTTP method/URL/status in log |
| AC-7 | Backend uses winston with structured JSON logging | API call → JSON log entry in console/file |
| AC-8 | Zero `console.log` remaining in backend after migration | `grep -r "console.log" backend/src/ | wc -l` == 0 |
| AC-9 | Coroutine failures are caught and logged before propagation | Simulate coroutine crash → logged to file |
| AC-10 | App startup log contains version, build type, and device info | Cold start → first line confirms metadata |

---

## 7. Execution Order & Dependencies

```
Phase 1 (Infra)     → No dependencies — do first
Phase 7 (Coroutine) → Depends on Phase 1 (uses enhanced FileLoggingTree)
Phase 2 (Services)  → Depends on Phase 1 + 7
Phase 3 (Workers)   → Depends on Phase 1
Phase 4 (UseCases)  → Depends on Phase 1
Phase 5 (Repos)     → Depends on Phase 1
Phase 6 (UI/DI/Net) → Depends on Phase 1
Phase 8 (Backend)   → Independent — can run in parallel with Android phases
```

**Recommended execution sequence:**
1. Phase 1 → Phase 7 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
2. Phase 8 (parallel)

---

## 8. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Logging adds I/O overhead to VPN hot path | Latency increase on DNS resolution | Use `Timber.v()` for packet events; buffer writes |
| Log files consume device storage | User storage pressure | 5MB rotation + 7-day retention policy |
| Sensitive data in logs (emails, OTP codes) | Privacy/security violation | Mask emails (`h***@gmail.com`), never log OTP values |
| Backend logging exposes request bodies | Security risk | Sanitize headers (strip auth tokens), redact PII |
| Too many log statements = noise | Hard to find real issues | Strict level policy; verbose only for trace events |

---

## 9. Constraints

- **No new dependencies on Android** — Timber already exists; use `HttpLoggingInterceptor` from OkHttp (already present)
- **One new dependency on Backend** — `winston` for structured logging
- **Match existing code style** — follow Timber tag convention, Kotlin idioms
- **No log in tight loops without level guard** — VPN packet processing must check level before formatting strings
- **Never log raw OTP codes, passwords, or full email addresses**
