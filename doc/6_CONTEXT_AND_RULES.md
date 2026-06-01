# AI Context & Coding Rules
## FocusLock — Android App
**Version:** 1.0 (MVP)

---

## SECTION A — CONTEXT.md
### Paste this at the start of every AI coding session

```
I am building FocusLock, an Android app that helps users voluntarily block
distracting browser content for a set time period they cannot cancel alone.

WHAT THE APP DOES:
- Blocks browser searches and URLs containing user-defined keywords
- Blocks entire domains from preset categories (social media, gambling, adult, etc.)
- Locks the session for a user-set timer (15 min to 72 hours)
- Prevents uninstallation during active session via Device Admin API
- Early unlock requires an OTP sent to a pre-registered trusted contact
- A 10-minute cooldown applies even after a correct OTP is entered
- A 24-hour emergency bypass exists that requires no OTP but enforces a wait

HOW IT WORKS TECHNICALLY:
- Accessibility Service reads browser URL bars across all Android browsers for keyword matching
- Local on-device VPN (no external server) intercepts DNS for domain-level blocking
- Device Admin API prevents uninstall during active session
- WorkManager manages the timer reliably across process death and reboots
- A minimal Node.js backend on Railway handles only OTP generation and Twilio SMS delivery
- All session state is stored locally in Room (SQLite)
- No browsing data, keywords, or personal content is ever sent to the server

TECH STACK:
- Language: Kotlin only
- UI: Jetpack Compose + Material 3
- Architecture: MVVM + Clean Architecture (UI → ViewModel → UseCase → Repository → DataSource)
- DI: Hilt
- Database: Room with Flow
- Async: Kotlin Coroutines only (no RxJava, no callbacks)
- Networking: Retrofit + OkHttp + Kotlin Serialization
- Background: WorkManager, ForegroundService, BroadcastReceiver
- Backend: Node.js + Express + TypeScript + Twilio + bcrypt
- Min SDK: 26, Target SDK: 35

CURRENT STATUS:
[Update this line each session — e.g. "Onboarding screens done, working on AccessibilityService"]
```

---

## SECTION B — RULES.md
### Strict coding rules — enforce these in every prompt

---

### B1. General Rules

- Write Kotlin only. Never suggest Java.
- Never use `var` when `val` is possible.
- Never use `!!` (non-null assertion). Handle nulls explicitly with `?.`, `?:`, or `requireNotNull()`.
- Never use `Any` as a type. Always use specific types.
- Never use string literals directly in code. All user-facing strings go in `res/values/strings.xml`.
- Never hardcode colors or dimensions directly in composables. Use theme tokens only.
- No `TODO()` left in completed code. Either implement it or throw `NotImplementedError("reason")`.

---

### B2. Architecture Rules

- No business logic in Composable functions. None. Ever.
- No business logic in ViewModel. ViewModels only hold state and delegate to UseCases.
- One UseCase per user action. `ActivateSessionUseCase`, `RequestUnlockUseCase`, etc.
- No direct Room DAO calls from ViewModel. Always go through Repository.
- No Retrofit calls from ViewModel. Always go through Repository.
- Repository interfaces live in `domain/repository/`. Implementations live in `data/repository/`.
- Domain models and Room entities are separate classes. Map between them in the Repository layer.

---

### B3. Coroutines Rules

- All async work uses coroutines. No callbacks. No threads created manually.
- Database operations use `Dispatchers.IO`.
- Network operations use `Dispatchers.IO`.
- CPU-heavy work (keyword list matching) uses `Dispatchers.Default`.
- Never call `runBlocking` in production code. Only acceptable in tests.
- Always handle exceptions in coroutines. Use `try/catch` or `CoroutineExceptionHandler`.
- Use `viewModelScope` in ViewModels. Use `lifecycleScope` in Activity/Fragment if ever needed.

---

### B4. Compose UI Rules

- Every screen is a stateless Composable that receives state and callbacks as parameters.
- State is hoisted to the ViewModel via `StateFlow`.
- Use `collectAsStateWithLifecycle()` not `collectAsState()` to collect flows in Compose.
- No `rememberCoroutineScope()` used to launch business logic. That belongs in ViewModel.
- Loading, error, and success states are modelled as a sealed class `UiState`.
- Navigation callbacks are passed as lambdas, not using NavController directly in composables.

```kotlin
// Correct pattern
@Composable
fun ActiveSessionScreen(
    state: ActiveSessionUiState,
    onRequestUnlock: () -> Unit,
    onEmergencyBypass: () -> Unit
) { ... }

// Wrong — never do this
@Composable
fun ActiveSessionScreen(viewModel: ActiveSessionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // logic here — NO
}
```

---

### B5. Room Rules

- Every DAO function that reads data returns `Flow<T>` (reactive, not one-shot).
- Every DAO function that writes data is a `suspend fun`.
- Never do raw SQL unless Room's query builder cannot express it.
- Always define indexes in `@Entity` for columns used in WHERE clauses.
- Database migrations must be written — never use `fallbackToDestructiveMigration()` after version 1.

---

### B6. Service Rules (Accessibility, VPN, Device Admin)

- Accessibility Service must handle `onServiceConnected()`, `onAccessibilityEvent()`, and `onInterrupt()`.
- VPN Service must handle `onStartCommand()`, `onRevoke()`, and clean up `ParcelFileDescriptor` properly.
- Every Service that runs during an active session must run as a foreground service with a visible notification.
- Services must not crash silently. Wrap event handling in `try/catch` and log errors.
- Services must not leak resources. Always close sockets, file descriptors, and unregister receivers in `onDestroy()`.

---

### B7. Security Rules

- Never log sensitive data (phone numbers, OTPs, keywords) in production builds.
- Use `BuildConfig.DEBUG` to gate any debug logging.
- Never store OTPs on device. Only the server holds OTP hashes temporarily.
- Phone numbers stored in Room must be treated as sensitive — do not include in crash reports.
- All backend API calls must use HTTPS. No HTTP.
- Backend URL must be stored in `local.properties` and read via `BuildConfig`. Never hardcoded.

---

### B8. Error Handling Rules

- Network errors must be caught and surfaced as user-readable messages in the UI.
- Database errors must never crash the app silently — always propagate to UI state.
- If SMS delivery fails, the app must show the user a clear message and offer a retry.
- Services that fail to start must update `AppSettings` in Room so the UI can reflect the degraded state.

---

### B9. What NOT to build (scope guard)

Do not build any of the following unless explicitly told the MVP scope has changed:

- iOS version
- Multiple trusted contacts
- Usage analytics, graphs, or session history display
- Scheduled or recurring sessions
- Browser extension
- Web dashboard
- Parental control mode or child profiles
- Social features, leaderboards, streaks
- Paid/subscription tier logic
- Push notifications (SMS only for MVP)
- Account creation or login system

---

## SECTION C — Session Checklist
### Run through this before starting each AI coding session

- [ ] Paste CONTEXT.md at the top of the conversation
- [ ] Update "CURRENT STATUS" line in CONTEXT.md to reflect what was last completed
- [ ] Tell the AI specifically what you are building this session (one feature only)
- [ ] Tell the AI what files already exist so it doesn't recreate them
- [ ] After the AI generates code, check it against RULES.md before accepting
- [ ] Test on a real Android device before marking a feature done
- [ ] Update the TODO log below after each session

---

## SECTION D — TODO Log
### Update this after every session

```
[DATE] - Completed: [what was built]
[DATE] - Next: [what to build next]

DONE:
- [ ] Project setup, Hilt, Room, Retrofit boilerplate
- [ ] Onboarding screen UI
- [ ] Permission request flow (Accessibility, VPN, Device Admin)
- [ ] Trusted contact registration + OTP verification
- [ ] Keyword selection screen
- [ ] Category selection screen
- [ ] Timer setup screen
- [ ] Session activation (SessionManager + WorkManager)
- [ ] AccessibilityService (URL reading + keyword matching)
- [ ] BlockingOverlayService
- [ ] VpnService (DNS interception + domain blocking)
- [ ] Device Admin receiver (uninstall lock)
- [ ] Boot recovery receiver
- [ ] Settings screen protection overlay
- [ ] Active session screen with countdown
- [ ] Early unlock request flow
- [ ] OTP entry screen
- [ ] 10-minute cooldown screen
- [ ] Emergency 24-hour bypass flow
- [ ] Session complete screen
- [ ] Backend: /verify-contact endpoint
- [ ] Backend: /confirm-contact endpoint
- [ ] Backend: /request-unlock endpoint
- [ ] Backend: /verify-unlock-otp endpoint
- [ ] Backend: deploy to Railway
- [ ] End-to-end test on real device
```
