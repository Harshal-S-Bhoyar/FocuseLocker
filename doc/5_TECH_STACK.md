# Tech Stack Document
## FocusLock — Android App
**Version:** 1.0 (MVP)

---

## 1. Language

**Kotlin only**
No Java. No mixed Kotlin/Java files. Kotlin version: 2.0+
Reason: Modern Android development standard. Better coroutine support. Cleaner syntax.

---

## 2. Android App Stack

### UI
| Tool | Version | Why |
|---|---|---|
| Jetpack Compose | Latest stable | Declarative UI, no XML layouts |
| Material 3 | Latest stable | Design system, theming |
| Compose Navigation | Latest stable | Screen navigation |

No XML layouts anywhere. No View-based UI components. Every screen is a Composable function.

### Architecture
| Pattern | Detail |
|---|---|
| Overall | MVVM + Clean Architecture |
| Layers | UI → ViewModel → UseCase → Repository → DataSource |
| State management | `StateFlow` + `collectAsStateWithLifecycle()` in Compose |
| Side effects | `LaunchedEffect`, `SideEffect` in Compose only |

No logic in Composable functions. No direct Room or API calls from ViewModel. Every business rule lives in a UseCase class.

### Dependency Injection
| Tool | Why |
|---|---|
| Hilt | Official Android DI. Integrates with ViewModel and WorkManager natively |

No manual dependency passing. No service locator pattern. Everything injected via Hilt.

### Local Database
| Tool | Why |
|---|---|
| Room | Official Android ORM over SQLite |
| Kotlin coroutines + Flow | Async DB access, reactive UI updates |

All Room DAOs return `Flow<T>` or `suspend fun`. No blocking database calls on main thread.

### Background Work & Services
| Tool | Use case |
|---|---|
| `AccessibilityService` | Keyword detection across browsers (10 browsers supported) |
| `VpnService` | Local on-device DNS interception |
| `DevicePolicyManager` | Uninstall lock |
| `WorkManager` | Reliable timer countdown, survives process death |
| `ForegroundService` | Keep VPN and Accessibility alive |
| `BroadcastReceiver` | Boot recovery (`BOOT_COMPLETED`) |
| `PermissionEnforcementService` | Detect lost permissions mid-session, route to repair |
| `OemAutostartHelper` | Send OEM-specific autostart intents (Xiaomi, Samsung, OPPO) |

WorkManager is used for the timer because it is the only Android-sanctioned way to guarantee work runs at a specific future time even if the app process is killed.

### Networking (App → Backend)
| Tool | Why |
|---|---|
| Retrofit 2 | HTTP client, clean API interface definition |
| OkHttp 4 | Underlying HTTP engine, interceptors for logging |
| Kotlin Serialization | JSON parsing, no Gson or Moshi |

All network calls are `suspend` functions. No callbacks. No RxJava.

### Async
| Tool | Why |
|---|---|
| Kotlin Coroutines | All async work |
| `Dispatchers.IO` | Network and database |
| `Dispatchers.Main` | UI updates only |
| `Dispatchers.Default` | CPU-heavy work (keyword matching on large lists) |

No threads created manually. No `AsyncTask`. No RxJava.

### Testing
| Tool | Use |
|---|---|
| JUnit 5 | Unit tests |
| MockK | Mocking in Kotlin |
| Turbine | Testing `Flow` emissions |
| Compose UI Test | UI tests for critical screens |

Every UseCase must have unit tests. ViewModels must have unit tests. Repository layer must have unit tests.

---

## 3. Backend Stack

### Runtime & Framework
| Tool | Why |
|---|---|
| Node.js 20 LTS | Stable, low overhead for simple API |
| Express 4 | Minimal HTTP framework |
| TypeScript | Type safety, fewer runtime errors |

### OTP & Email
| Tool | Why |
|---|---|
| Brevo Email API | Transactional email delivery — OTP to trusted contact |
| `bcrypt` | Hash OTPs before storing in memory, never store plain OTP |
| `crypto` (Node built-in) | Secure OTP generation |

OTP generation: `crypto.randomInt(100000, 999999)` — always 6 digits, cryptographically secure.

### In-Memory Store
| Tool | Why |
|---|---|
| Plain JavaScript `Map` with TTL | MVP only — no Redis needed at this scale |

For MVP with personal use and small user base, an in-memory Map is fine. Move to Redis when scaling.

### Validation
| Tool | Why |
|---|---|
| `zod` | Request body validation and type inference |

Every endpoint validates its input with zod before processing. Invalid requests return 400 immediately.

### Hosting
| Provider | Why |
|---|---|
| Railway.app | Free tier available, deploys from GitHub, simple environment variables |

Single server instance for MVP. No load balancing needed.

### Environment Variables (never hardcoded)
```
BREVO_API_KEY=
BREVO_SENDER_EMAIL=
OTP_EXPIRY_MINUTES=10
OTP_MAX_ATTEMPTS=3
PORT=3000
```

---

## 4. Development Tools (Restricted Environment Setup)

### Primary Development Environment
| Tool | Use | Installation Method |
|---|---|---|
| VS Code (Portable) | Primary IDE for Kotlin + Backend development | Download portable .zip, extract to user folder |
| Android SDK Command Line Tools | Build, compile, and deploy Android app | Download to `C:\Users\harshal.bhoyar\AndroidSDK` (no admin needed) |
| Gradle Wrapper (`gradlew.bat`) | Build automation | Included in project, no system installation needed |
| Git (Portable) | Version control | Portable Git for Windows, extract to user folder |
| ADB (Android Debug Bridge) | Deploy and debug on physical device | Comes with SDK Command Line Tools |
| Postman (Portable) | Test backend endpoints manually | Download portable version |

### VS Code Extensions (No Admin Required)
- **Kotlin Language** — Syntax highlighting and basic IntelliSense
- **Android for VS Code** — Android project support
- **Gradle for Java** — Gradle task runner integration
- **Thunder Client** — Alternative to Postman, built into VS Code

### Environment Setup (User-Level Only)

**Step 1: Android SDK Setup**
```cmd
REM Download Android SDK Command Line Tools from:
REM https://developer.android.com/studio#command-line-tools-only

REM Extract to user directory
set ANDROID_HOME=C:\Users\harshal.bhoyar\AndroidSDK
set PATH=%PATH%;%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools

REM Install required components (no admin needed)
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "cmdline-tools;latest"
```

**Step 2: Set User Environment Variables**
- Open: System Properties → Environment Variables → User variables (not System variables)
- Add `ANDROID_HOME=C:\Users\harshal.bhoyar\AndroidSDK`
- Append to `PATH`: `%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools`

**Step 3: Verify Setup**
```cmd
adb version
gradlew.bat --version
```

### Build & Deploy Workflow (Command Line)

```cmd
REM Build debug APK
gradlew.bat assembleDebug

REM Install on connected physical device
adb install -r app\build\outputs\apk\debug\app-debug.apk

REM View logs
adb logcat | findstr "FocusLock"

REM Uninstall (for testing Device Admin uninstall lock)
adb uninstall com.focuslock
```

### Physical Device Testing (Required)

**Why physical device is mandatory for this project:**
- Emulators require Hyper-V/virtualization (needs admin rights)
- Accessibility Service behaves differently on emulators
- VPN Service testing unreliable on emulators
- Device Admin uninstall lock cannot be tested on emulators
- SMS/OTP testing requires real phone number

**Physical Device Setup:**
1. Enable Developer Options (tap Build Number 7 times in Settings → About Phone)
2. Enable USB Debugging in Developer Options
3. Connect via USB cable
4. Accept USB debugging prompt on device
5. Verify connection: `adb devices`

### Alternative IDE Option (If VS Code Insufficient)

**IntelliJ IDEA Community Edition (Portable)**
- Download .zip version (no installer)
- Extract to user folder
- Install Android plugin from JetBrains marketplace
- Configure Android SDK path to your user folder location
- Better Kotlin support than VS Code

### Limitations & Workarounds

| Limitation | Impact | Workaround |
|---|---|---|
| No Android Studio Layout Editor | Cannot visually design XML layouts | Not needed — using Jetpack Compose (code-only UI) |
| No AVD Manager (Emulator) | Cannot test on virtual devices | Use physical device (better for this project anyway) |
| No Profiler GUI | Cannot use Android Studio Profiler | Use `adb shell dumpsys` commands for memory/CPU monitoring |
| Slower IntelliSense | VS Code Kotlin support is basic | Use IntelliJ IDEA Community (portable) for better experience |

**Always test on a real Android device.** Emulators do not support VPN service or Device Admin reliably. Accessibility Service behaviour on emulators is different from real devices. For this project, physical device testing is not just recommended — it's required.

---

## 5. Minimum Android Requirements

| Requirement | Value | Reason |
|---|---|---|
| `minSdk` | 26 (Android 8.0) | WorkManager reliable from API 26+ |
| `targetSdk` | 35 (Android 15) | Latest target for Play Store compliance |
| `compileSdk` | 35 | Match target SDK |

---

## 6. Folder Structure (Android App)

```
app/
├── src/
│   └── main/
│       ├── java/com/focuslock/
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── dao/          -- Room DAOs
│       │   │   │   ├── entity/       -- Room entities
│       │   │   │   └── FocusDatabase.kt
│       │   │   ├── remote/
│       │   │   │   ├── api/          -- Retrofit interfaces
│       │   │   │   └── dto/          -- Request/Response data classes
│       │   │   └── repository/       -- Repository implementations
│       │   ├── domain/
│       │   │   ├── model/            -- Domain models (not entities)
│       │   │   ├── repository/       -- Repository interfaces
│       │   │   └── usecase/          -- One file per use case
│       │   ├── service/
│       │   │   ├── accessibility/    -- FocusAccessibilityService
│       │   │   ├── vpn/              -- FocusVpnService
│       │   │   ├── overlay/          -- BlockingOverlayService
│       │   │   └── admin/            -- FocusDeviceAdminReceiver
│       │   ├── ui/
│       │   │   ├── onboarding/
│       │   │   ├── setup/
│       │   │   ├── session/
│       │   │   ├── unlock/
│       │   │   ├── theme/            -- Colors, Typography, Theme
│       │   │   └── components/       -- Shared composables
│       │   ├── worker/               -- WorkManager workers
│       │   ├── receiver/             -- BroadcastReceivers
│       │   ├── di/                   -- Hilt modules
│       │   └── util/                 -- Extensions, helpers
│       ├── res/
│       │   ├── xml/
│       │   │   ├── accessibility_service_config.xml
│       │   │   └── device_admin.xml
│       │   └── raw/
│       │       └── blocked_domains.json  -- Bundled domain blocklist
│       └── AndroidManifest.xml
└── build.gradle.kts
```

---

## 7. Folder Structure (Backend)

```
backend/
├── src/
│   ├── routes/
│   │   ├── contact.routes.ts     -- /verify-contact, /confirm-contact
│   │   └── unlock.routes.ts      -- /request-unlock, /verify-unlock-otp
│   ├── services/
│   │   ├── otp.service.ts        -- OTP generation, hashing, verification
│   │   └── sms.service.ts        -- Twilio SMS wrapper
│   ├── store/
│   │   └── otp.store.ts          -- In-memory OTP Map with TTL
│   ├── middleware/
│   │   └── validate.ts           -- Zod validation middleware
│   ├── schemas/
│   │   └── request.schemas.ts    -- Zod schemas for all endpoints
│   └── app.ts                    -- Express app setup
├── index.ts                      -- Entry point
├── .env
├── package.json
└── tsconfig.json
```
