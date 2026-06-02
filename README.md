<p align="center">
  <img src="assets/focuslock_icon.png" alt="FocusLock" width="120" height="120" />
</p>

<h1 align="center">FocusLock</h1>

<p align="center">
  <strong>An Android app that helps you block distracting browser content — and keeps it blocked.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Backend-Node.js-339933?logo=nodedotjs&logoColor=white" alt="Node.js" />
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen" alt="Min SDK 26" />
  <img src="https://img.shields.io/badge/Version-1.0.0%20MVP-blue" alt="Version" />
  <img src="https://img.shields.io/badge/License-ISC-yellow" alt="License" />
</p>

---

## 🧠 What Is FocusLock?

FocusLock is a **self-discipline Android app** for people who want to voluntarily block distracting or harmful browser content for a set time period — and make it genuinely difficult to undo.

**The core promise:** Once you start a blocking session, **you cannot stop it alone.**

Unlike other blocker apps where a single tap removes the block, FocusLock creates **real accountability** by requiring a trusted human contact to help you unlock early.

### Who Is This For?

- Android users who are aware of their own distraction/browsing problem
- People motivated to change but who need **external friction** to follow through
- Anyone with at least one trusted person (friend, partner, family member) willing to act as an accountability partner

---

## ✨ Core Features

### 🔒 Feature 1 — Keyword & Category Blocking

Block browser content at two levels:

| Method | How It Works | Technology |
|---|---|---|
| **Keyword Blocking** | Intercepts browser URLs containing your custom keywords (e.g., "gambling", "reddit") | Android Accessibility Service |
| **Domain Blocking** | Blocks entire domains by category (social media, adult content, gambling, news, gaming, shopping) | Local on-device VPN (DNS interception) |

- Works across **all installed browsers** (Chrome, Firefox, Brave, Opera, etc.)
- Up to **50 custom keywords** plus 6 preset content categories
- No browsing data ever leaves your device

### ⏱️ Feature 2 — Timer Lock

- Set a blocking session from **15 minutes to 72 hours**
- Once activated, the session **cannot be stopped** until the timer expires
- Timer persists across **app restarts and device reboots**
- App uses **Device Admin API** to prevent uninstallation during active sessions

### 📱 Feature 3 — Trusted Contact Unlock

The only way to end a session early:

1. You tap **"Request Early Unlock"** during an active session
2. An **OTP + context message** is sent via SMS to your pre-registered trusted contact
3. The message tells your contact: **what you blocked**, **how much time remains**, and **when you asked**
4. Your trusted contact decides whether to share the OTP with you
5. After entering the correct OTP, a **10-minute mandatory cooldown** applies before the session unlocks
6. If your trusted contact is unreachable, a **24-hour emergency bypass** is available (no OTP needed, just wait 24 hours)

### 🛡️ Feature 4 — Settings Protection

During an active session, the app blocks your attempts to:

- Disable the Accessibility Service
- Turn off the VPN
- Revoke Device Admin privileges

A **full-screen overlay** covers the screen if you navigate to those settings.

---

## 🔄 How It Works — Full User Flow

```
📱 Install FocusLock
 │
 ├─ 1. Grant Permissions
 │     ├── Accessibility Service (keyword detection in browsers)
 │     ├── VPN Service (domain-level blocking)
 │     ├── Device Admin (prevents uninstall)
 │     └── Battery Optimization Exemption
 │
 ├─ 2. Select Content to Block
 │     ├── Choose preset categories (Social Media, Adult, Gambling, etc.)
 │     └── Add custom keywords (up to 50)
 │
 ├─ 3. Set Timer Duration
 │     └── 15 minutes to 72 hours
 │
 ├─ 4. Register Trusted Contact
 │     ├── Enter trusted person's phone number
 │     └── Verify via OTP (one-time setup)
 │
 ├─ 5. Confirm & Lock In
 │     └── Review summary → Tap "Lock In" → Session starts
 │
 └─ 6. Active Session
       ├── 🔴 Browser searches with keywords → BLOCKED (overlay)
       ├── 🔴 Blocked domains → DNS BLOCKED (local VPN)
       ├── 🔴 Uninstall attempt → PREVENTED (Device Admin)
       ├── 🔴 Settings tampering → OVERLAY
       │
       ├── ⏰ Timer expires → Session ends automatically
       │
       └── 🔓 Want to end early?
             ├── Request Unlock → OTP sent to trusted contact
             │     ├── Enter OTP → 10-min cooldown → Unlocked
             │     └── Wrong OTP × 3 → Must request new OTP
             │
             └── Emergency Bypass (no OTP needed)
                   └── 24-hour mandatory wait → Auto-unlock
```

---

## 🏗️ Architecture

FocusLock follows **MVVM + Clean Architecture** with strict layer separation:

```
┌──────────────────────────────────────────────────────────┐
│                     ANDROID DEVICE                       │
│                                                          │
│  ┌──────────────────┐    ┌────────────────────────────┐  │
│  │    UI Layer       │    │  Accessibility Service     │  │
│  │  (Jetpack Compose │    │  (Keyword detection in     │  │
│  │   + ViewModels)   │    │   browser URL bars)        │  │
│  └────────┬─────────┘    └─────────────┬──────────────┘  │
│           │                            │                  │
│  ┌────────▼────────────────────────────▼──────────────┐  │
│  │              Domain Layer (UseCases)                │  │
│  │   Pure Kotlin — no Android imports allowed          │  │
│  └────────┬────────────────────────────┬──────────────┘  │
│           │                            │                  │
│  ┌────────▼──────────┐    ┌────────────▼──────────────┐  │
│  │  Room Database     │    │     Service Layer          │  │
│  │  (SQLite — all     │    │  Local VPN + Device Admin  │  │
│  │   state is local)  │    │  + Overlay + WorkManager   │  │
│  └───────────────────┘    └───────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                            │
                      HTTPS (OTP only)
                            │
┌───────────────────────────▼──────────────────────────────┐
│                    BACKEND SERVER                         │
│           Node.js + Express + TypeScript                  │
│                                                          │
│    POST /verify-contact    POST /request-unlock           │
│    POST /confirm-contact   POST /verify-unlock-otp        │
│                                                          │
│    ⚠️ Stores NOTHING permanently                          │
│    OTPs live in memory with 10-minute TTL only            │
└──────────────────────────────────────────────────────────┘
```

### Data Flow — Blocking a Search

```
User types "gambling tips" in Chrome
  → Chrome loads: google.com/search?q=gambling+tips
    → Accessibility Service detects URL change
      → Extracts URL from browser address bar node
        → URL matched against keyword list ("gambling" found)
          → Full-screen blocking overlay appears
            → Browser navigated back automatically
```

### Data Flow — Early Unlock Request

```
User taps "Request Early Unlock"
  → App calls POST /request-unlock on backend
    → Backend sends SMS to trusted contact:
      "FocusLock: [User] wants to end their session early.
       Time remaining: 1h 23m. Blocked: Gambling, Adult.
       OTP: 847291. Share only if you think it's justified."
    → Trusted contact shares OTP with user
      → User enters OTP in app
        → App calls POST /verify-unlock-otp
          → Valid → 10-minute cooldown starts
            → Cooldown complete → Session ends
```

---

## 🛠️ Tech Stack

### Android App

| Component | Technology | Why |
|---|---|---|
| **Language** | Kotlin 2.0+ | Modern Android standard, no Java |
| **UI** | Jetpack Compose + Material 3 | Declarative UI, no XML layouts |
| **Architecture** | MVVM + Clean Architecture | UI → ViewModel → UseCase → Repository → DataSource |
| **DI** | Hilt | Official Android DI |
| **Database** | Room (SQLite) + Kotlin Flow | Reactive local storage |
| **Networking** | Retrofit + OkHttp + Kotlin Serialization | Type-safe HTTP client |
| **Background Work** | WorkManager | Reliable timer across process death & reboot |
| **Services** | AccessibilityService, VpnService, DeviceAdminReceiver | Core blocking mechanisms |
| **Async** | Kotlin Coroutines only | No RxJava, no callbacks |
| **Testing** | JUnit 4 + MockK + Turbine | Unit tests for UseCases & ViewModels |

### Backend Server

| Component | Technology | Why |
|---|---|---|
| **Runtime** | Node.js 20 LTS | Low overhead for simple API |
| **Framework** | Express 5 | Minimal HTTP framework |
| **Language** | TypeScript 6 | Type safety |
| **SMS** | Twilio SMS API | Reliable global SMS delivery |
| **OTP Hashing** | bcrypt | Secure — never stores plain OTPs |
| **OTP Generation** | `crypto.randomInt` | Cryptographically secure 6-digit codes |
| **Validation** | Zod | Schema-based request validation |
| **Security** | Helmet + CORS | HTTP hardening |
| **Storage** | In-memory Map with TTL | No database — OTPs expire in 10 minutes |
| **Hosting** | Railway.app | Simple deploy from GitHub |

### Android Requirements

| Requirement | Value |
|---|---|
| `minSdk` | 26 (Android 8.0 Oreo) |
| `targetSdk` | 35 (Android 15) |
| `compileSdk` | 35 |

---

## 📁 Project Structure

```
FocuseLocker/
├── app/                              # Android application
│   └── src/main/java/com/focuslock/
│       ├── data/                     # Data layer
│       │   ├── local/
│       │   │   ├── dao/              # Room DAOs (7 DAOs)
│       │   │   ├── entity/           # Room entities (7 entities)
│       │   │   └── FocusDatabase.kt  # Room database
│       │   ├── remote/
│       │   │   ├── api/              # Retrofit API interface
│       │   │   └── dto/              # Request/Response DTOs
│       │   └── repository/           # Repository implementations
│       ├── domain/                   # Domain layer (pure Kotlin)
│       │   ├── model/                # Domain models
│       │   ├── repository/           # Repository interfaces
│       │   └── usecase/              # 19 UseCases
│       ├── service/                  # Android services
│       │   ├── accessibility/        # FocusAccessibilityService
│       │   ├── vpn/                  # FocusVpnService
│       │   ├── overlay/              # BlockingOverlayService
│       │   └── admin/                # FocusDeviceAdminReceiver
│       ├── ui/                       # Presentation layer
│       │   ├── onboarding/           # Onboarding + Permission screens
│       │   ├── setup/                # Category, Keyword, Timer screens
│       │   ├── session/              # Active session + Unlock screens
│       │   ├── navigation/           # Compose Navigation graph
│       │   ├── theme/                # Material 3 theme
│       │   └── common/               # Shared composables
│       ├── worker/                   # WorkManager timer workers
│       ├── receiver/                 # Boot recovery receiver
│       ├── di/                       # Hilt DI modules
│       └── util/                     # Helpers & extensions
├── backend/                          # Node.js OTP server
│   ├── src/
│   │   ├── routes/                   # Express route handlers
│   │   ├── services/                 # OTP + SMS services
│   │   ├── store/                    # In-memory OTP store
│   │   ├── schemas/                  # Zod validation schemas
│   │   ├── middleware/               # Request validation
│   │   ├── config/                   # Environment config
│   │   └── utils/                    # Helpers
│   ├── tests/                        # Jest test suite
│   ├── package.json
│   └── tsconfig.json
├── doc/                              # Project documentation
│   ├── 1_PRD.md                      # Product Requirements
│   ├── 2_USER_STORIES.md             # User Stories + Acceptance Criteria
│   ├── 3_SYSTEM_DESIGN.md            # System architecture
│   ├── 4_DATA_MODEL.md               # Database schema
│   ├── 5_TECH_STACK.md               # Technology choices
│   ├── 6_CONTEXT_AND_RULES.md        # AI context + coding rules
│   └── 7_RULES.md                    # Skill invocation rules
└── plans/                            # Implementation plans
    ├── phase3_implementation_plan.md
    ├── phase4_implementation_plan.md
    ├── phase5_implementation_plan.md
    └── phase6_implementation_plan.md
```

---

## 🗄️ Database Schema

All data is stored **locally on-device** using Room (SQLite). No user data is stored on the backend.

| Table | Purpose | Key Rule |
|---|---|---|
| `sessions` | Current & past focus sessions | Only 1 session can be `ACTIVE` at a time |
| `blocked_keywords` | User's custom keywords | Max 50 keywords, stored lowercase |
| `blocked_categories` | Preset content categories | 6 categories, toggled on/off |
| `blocked_domains` | Domain blocklist for VPN | 5,000–20,000 rows, indexed |
| `trusted_contacts` | Verified trusted contact | Max 1 contact for MVP |
| `unlock_requests` | Early unlock attempts (audit trail) | Max 3 OTP attempts per request |
| `app_settings` | Key-value app configuration | Tracks permission & onboarding state |

```
sessions (1) ──────── (many) unlock_requests
blocked_categories (1) ──── (many) blocked_domains
trusted_contacts (1) ──── referenced by unlock_requests
```

---

## 🔌 Backend API Endpoints

The backend does **one thing only**: generate and verify OTPs via SMS. It stores nothing permanently.

| Endpoint | Method | Purpose | Input |
|---|---|---|---|
| `/verify-contact` | POST | Send OTP to verify trusted contact number | `{ phoneNumber }` |
| `/confirm-contact` | POST | Verify the contact OTP | `{ phoneNumber, otp }` |
| `/request-unlock` | POST | Send unlock OTP + context to trusted contact | `{ trustedContactNumber, blockedCategories, blockedKeywords, timeRemaining }` |
| `/verify-unlock-otp` | POST | Verify the unlock OTP | `{ trustedContactNumber, otp }` |

**Privacy guarantee:**
- ❌ No user accounts
- ❌ No browsing history stored
- ❌ No keywords or categories stored on server
- ✅ Phone numbers held only during OTP verification, then discarded
- ✅ OTPs hashed with bcrypt, expire in 10 minutes

---

## 📱 Required Android Permissions

| Permission | Why | If Denied |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Keyword detection in browser URLs | Keyword blocking disabled |
| `VPN_SERVICE` | Domain-level DNS blocking | Domain blocking disabled |
| `BIND_DEVICE_ADMIN` | Prevent uninstall during session | Timer lock not enforced |
| `RECEIVE_BOOT_COMPLETED` | Session survives reboot | Session ends on reboot |
| `FOREGROUND_SERVICE` | Keep blocking services alive | Services killed by OS |
| `SYSTEM_ALERT_WINDOW` | Full-screen blocking overlay | Overlay won't show |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent OS from killing services | Services may be killed |
| `INTERNET` | OTP via backend SMS API | Trusted contact feature disabled |

---

## 🚀 Getting Started

### Prerequisites

- **Android SDK** (API 26+) — [Download CLI tools](https://developer.android.com/studio#command-line-tools-only)
- **Node.js 20 LTS** — for the backend server
- **Physical Android device** — emulators cannot reliably test VPN, Accessibility Service, or Device Admin
- **Twilio account** — for SMS delivery ([sign up](https://www.twilio.com/try-twilio))

### 1. Clone the Repository

```bash
git clone https://github.com/Harshal-S-Bhoyar/FocuseLocker.git
cd FocuseLocker
```

### 2. Set Up the Android App

```bash
# Set environment variable (Windows — User level)
set ANDROID_HOME=C:\Users\<YOUR_USERNAME>\AndroidSDK

# Install required SDK components
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Build the debug APK
gradlew.bat assembleDebug

# Connect your Android device via USB (with USB Debugging enabled)
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 3. Set Up the Backend

```bash
cd backend

# Install dependencies
npm install

# Create your environment file
cp .env.example .env
```

Edit `.env` with your Twilio credentials:

```env
TWILIO_ACCOUNT_SID=your_account_sid_here
TWILIO_AUTH_TOKEN=your_auth_token_here
TWILIO_PHONE_NUMBER=+1234567890
OTP_EXPIRY_MINUTES=10
OTP_MAX_ATTEMPTS=3
PORT=3000
```

```bash
# Start the development server
npm run dev

# Run tests
npm test

# Build for production
npm run build
npm start
```

### 4. Connect App to Backend

The app reads the backend URL from `BuildConfig`:

| Build Type | URL | Source |
|---|---|---|
| **Debug** | `http://10.0.2.2:3000` | Emulator localhost proxy |
| **Release** | `https://focuslock-backend.railway.app` | Production server |

For physical device debugging, update the debug URL in `app/build.gradle.kts` to your machine's local IP:

```kotlin
debug {
    buildConfigField("String", "BACKEND_URL", "\"http://192.168.x.x:3000\"")
}
```

---

## 🧪 Testing

### Android Unit Tests

```bash
# Run all unit tests
gradlew.bat testDebugUnitTest

# View test reports
# app/build/reports/tests/testDebugUnitTest/index.html
```

**Test coverage includes:**
- ✅ All UseCases (19 use cases)
- ✅ All ViewModels (Onboarding, Permission Setup, Trusted Contact, Verify Contact)
- ✅ Repository implementations
- ✅ Flow emissions tested with [Turbine](https://github.com/cashapp/turbine)
- ✅ Coroutine testing with `kotlinx-coroutines-test`

### Backend Tests

```bash
cd backend
npm test
```

**Test coverage includes:**
- ✅ OTP store operations (create, verify, expiry, max attempts)
- ✅ Request validation (Zod schemas)
- ✅ API endpoint integration tests (Supertest)

---

## 🔐 Security Design

| Aspect | Implementation |
|---|---|
| **OTP Generation** | `crypto.randomInt(100000, 999999)` — cryptographically secure |
| **OTP Storage** | Hashed with `bcrypt` — never stored in plain text |
| **OTP Lifetime** | 10 minutes max, deleted after use or max attempts |
| **Phone Numbers** | Stored locally on device only, never in server logs |
| **Logging** | Phone numbers and OTPs never logged, even in debug builds |
| **Network** | All API calls over HTTPS |
| **Backend URL** | Read from `BuildConfig`, never hardcoded |
| **Backend Storage** | In-memory only — nothing written to disk |
| **Session Integrity** | `expires_at` timestamp is the single source of truth |
| **Uninstall Protection** | Device Admin API active during sessions |
| **Settings Protection** | Overlay blocks access to Accessibility/VPN/Admin settings |

---

## ⚠️ Known Limitations

| Limitation | Impact | Mitigation |
|---|---|---|
| Factory reset bypasses everything | High | Cannot be prevented on Android — accepted trade-off |
| Force stop kills services temporarily | Medium | Foreground service with persistent notification reduces this |
| Trusted contact can share OTP without thinking | High | Context message in SMS encourages responsible decision |
| Accessibility Service killed by battery optimization | Medium | App requests battery optimization exemption |
| Domain blocking only (no keyword matching) for non-browser apps | Low | Out of scope for MVP |
| SMS delivery not guaranteed in all regions | Medium | Retry option available; 24-hour emergency bypass as fallback |

---

## 📋 Development Phases

The project is built in **6 phases**, each with a detailed implementation plan:

| Phase | Focus | Status |
|---|---|---|
| **Phase 1** | Project setup, Hilt, Room, Retrofit boilerplate | ✅ Complete |
| **Phase 2** | Onboarding, permissions, trusted contact registration | ✅ Complete |
| **Phase 3** | Category/keyword selection, timer setup, session activation | ✅ Complete |
| **Phase 4** | Accessibility Service, VPN Service, blocking overlay | ✅ Complete |
| **Phase 5** | Device Admin, boot recovery, settings protection | ✅ Complete |
| **Phase 6** | Unlock flow, OTP entry, cooldown, emergency bypass | 🔧 In Progress |

---

## 🤝 Contributing

This is currently a **personal project** in active development. If you'd like to contribute:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Follow the coding rules in `doc/6_CONTEXT_AND_RULES.md`
4. Write unit tests for any new UseCase or ViewModel
5. Test on a **physical Android device**
6. Submit a pull request

### Coding Rules (Non-Negotiable)

- **Kotlin only** — no Java files
- **No `!!`** — handle nulls with `?.`, `?:`, or `requireNotNull()`
- **No business logic in Composables** — delegate to ViewModel
- **No business logic in ViewModels** — delegate to UseCases
- **All DAO reads return `Flow<T>`** — reactive, not one-shot
- **No `runBlocking`** in production code
- **Never log phone numbers or OTPs** — not even in debug builds
- **Hilt for all DI** — no manual dependency passing

---

## 📄 Documentation

| Document | Description |
|---|---|
| [`doc/1_PRD.md`](doc/1_PRD.md) | Product Requirements Document |
| [`doc/2_USER_STORIES.md`](doc/2_USER_STORIES.md) | User Stories with acceptance criteria |
| [`doc/3_SYSTEM_DESIGN.md`](doc/3_SYSTEM_DESIGN.md) | System architecture & data flows |
| [`doc/4_DATA_MODEL.md`](doc/4_DATA_MODEL.md) | Database schema & entity relationships |
| [`doc/5_TECH_STACK.md`](doc/5_TECH_STACK.md) | Technology choices & folder structure |
| [`doc/6_CONTEXT_AND_RULES.md`](doc/6_CONTEXT_AND_RULES.md) | AI context block & coding rules |
| [`doc/7_RULES.md`](doc/7_RULES.md) | Skill invocation rules |

---

## 📊 Codebase Stats

| Metric | Count |
|---|---|
| Total files | 71+ |
| Kotlin source files | ~55 |
| TypeScript source files | ~12 |
| Domain UseCases | 19 |
| Room DAOs | 7 |
| Room Entities | 7 |
| Repository interfaces | 5 |
| Repository implementations | 5 |
| Compose screens | 12+ |
| ViewModels | 6+ |
| Unit tests | 16+ |
| Hilt DI modules | 3 |
| Android services | 4 |
| Backend API endpoints | 4 |

---

## 🗺️ Roadmap (Post-MVP)

These are explicitly **out of scope for MVP** but may be considered for future versions:

- [ ] iOS version
- [ ] Multiple trusted contacts
- [ ] Usage analytics & session history reports
- [ ] Scheduled recurring sessions
- [ ] Browser extension companion
- [ ] Parental control mode
- [ ] Web dashboard

---

<p align="center">
  <strong>Built with focus, for focus.</strong><br/>
  <em>Because sometimes, you need someone else to hold the key.</em>
</p>
