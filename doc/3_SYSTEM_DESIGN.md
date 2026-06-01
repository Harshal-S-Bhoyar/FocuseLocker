# System Design Document
## FocusLock — Android App
**Version:** 1.0 (MVP)

---

## 1. Architecture Overview

FocusLock has two parts:

**On-device Android app** — handles all blocking, timer, and UI logic locally. The app works offline for all core features.

**Lightweight backend server** — handles only OTP generation, SMS delivery, and trusted contact verification. No usage data, no browsing history, no personal content is ever sent to the server.

```
┌─────────────────────────────────────────────────────┐
│                  ANDROID DEVICE                     │
│                                                     │
│  ┌──────────────┐    ┌────────────────────────┐     │
│  │   UI Layer   │    │  Accessibility Service │     │
│  │ (Compose UI) │    │  (Keyword detection)   │     │
│  └──────┬───────┘    └────────────┬───────────┘     │
│         │                        │                  │
│  ┌──────▼────────────────────────▼───────────┐      │
│  │           ViewModel / Use Cases           │      │
│  └──────┬────────────────────────┬───────────┘      │
│         │                        │                  │
│  ┌──────▼───────┐    ┌───────────▼───────────┐      │
│  │  Room DB     │    │     Service Layer      │      │
│  │ (local state)│    │  VPN + Device Admin    │      │
│  └──────────────┘    └───────────────────────┘      │
└─────────────────────────────────────────────────────┘
                          │
                    HTTPS (OTP only)
                          │
┌─────────────────────────▼───────────────────────────┐
│                   BACKEND SERVER                    │
│         Node.js + Express + Twilio SMS              │
│                                                     │
│   /verify-contact  /request-unlock  /verify-otp     │
└─────────────────────────────────────────────────────┘
```

---

## 2. Android App Components

### 2.1 UI Layer (Jetpack Compose)
Screens: Onboarding, Permission Setup, Trusted Contact Setup, Keyword/Category Selection, Timer Setup, Active Session, Unlock Request, OTP Entry, Emergency Bypass, Session Complete.

All screens are driven by ViewModels. No business logic in composables.

### 2.2 Accessibility Service
**Class:** `FocusAccessibilityService extends AccessibilityService`

Responsibilities:
- Listen to `TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_WINDOW_STATE_CHANGED` events
- Extract current URL from browser address bar nodes
- Match URL against active keyword list and blocked domain list
- Trigger overlay when match found
- Detect navigation to Settings screens and trigger overlay

Runs as a foreground service during active session. Declared in `AndroidManifest.xml` with `BIND_ACCESSIBILITY_SERVICE` permission.

### 2.3 Local VPN Service
**Class:** `FocusVpnService extends VpnService`

Responsibilities:
- Create a local TUN interface — no external VPN server
- Intercept outgoing DNS queries
- Compare queried domain against blocked domain list stored in Room DB
- Return NXDOMAIN or redirect to local blocked page for blocked domains
- Pass all other traffic through unchanged

Runs as a foreground service. Does not inspect HTTPS payload — domain-level only.

### 2.4 Device Admin Receiver
**Class:** `FocusDeviceAdminReceiver extends DeviceAdminReceiver`

Responsibilities:
- Prevent app uninstallation during active session
- Listen for admin deactivation attempts

Registered in `AndroidManifest.xml`. Device Admin is activated during onboarding and stays active. Uninstall lock is enforced by `DevicePolicyManager`.

### 2.5 Session Manager
**Class:** `SessionManager` (singleton, injected via Hilt)

Responsibilities:
- Central source of truth for session state
- Start / stop session
- Manage timer countdown using `WorkManager` for reliability
- Trigger service start/stop
- Handle reboot recovery via `BOOT_COMPLETED` receiver

### 2.6 Room Database (Local)
Single database file on device. No sync. See Data Model document for schema.

### 2.7 Overlay Screen
**Class:** `BlockingOverlayService extends Service`

Draws a `TYPE_APPLICATION_OVERLAY` window (requires `SYSTEM_ALERT_WINDOW` permission) that covers the screen when blocking triggers. Cannot be dismissed by back button during active session.

---

## 3. Backend Server Components

Minimal Node.js + Express server. Hosted on Railway, Render, or similar low-cost provider.

### Endpoints

**POST /verify-contact**
- Input: `{ phoneNumber }`
- Generates a 6-digit OTP
- Sends OTP to phoneNumber via Twilio SMS
- Stores OTP hash + expiry (10 min) in server memory or Redis
- Returns: `{ success: true }`

**POST /confirm-contact**
- Input: `{ phoneNumber, otp }`
- Verifies OTP
- Returns: `{ verified: true/false }`

**POST /request-unlock**
- Input: `{ trustedContactNumber, blockedCategories, blockedKeywords, timeRemaining, requestTimestamp }`
- Generates a 6-digit unlock OTP
- Sends SMS to trusted contact with full context message
- Stores OTP hash + expiry (10 min)
- Returns: `{ success: true }`

**POST /verify-unlock-otp**
- Input: `{ trustedContactNumber, otp }`
- Verifies OTP
- Invalidates OTP after use
- Returns: `{ valid: true/false }`

### What the server does NOT store
- No user accounts
- No browsing history
- No keywords or categories
- Phone numbers are only held temporarily during OTP verification, then discarded

---

## 4. Permission Requirements

| Permission | Why needed | Consequence if denied |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Keyword detection in browsers | Keyword blocking disabled |
| `VPN_SERVICE` | Domain-level blocking | Domain blocking disabled |
| `BIND_DEVICE_ADMIN` | Uninstall lock | Timer lock not enforced |
| `RECEIVE_BOOT_COMPLETED` | Session survives reboot | Session ends on reboot |
| `FOREGROUND_SERVICE` | Keep services alive | Services killed by OS |
| `SYSTEM_ALERT_WINDOW` | Overlay screen | Blocking overlay won't show |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent OS from killing services | Services may be killed |
| `INTERNET` | OTP SMS via backend | Trusted contact feature disabled |

---

## 5. Data Flow — Blocking a Search

```
User types "gambling tips" in Chrome search bar
        ↓
Chrome loads: google.com/search?q=gambling+tips
        ↓
Accessibility Service detects URL change event
        ↓
Service extracts URL string from address bar node
        ↓
URL checked against keyword list in memory
        ↓
"gambling" keyword matched
        ↓
BlockingOverlayService draws overlay on screen
        ↓
Accessibility Service calls performAction(BACK) on browser
        ↓
User sees overlay with "blocked" message + time remaining
```

---

## 6. Data Flow — Early Unlock Request

```
User taps "Request early unlock"
        ↓
App calls POST /request-unlock on backend
  (passes: trusted contact number, categories, time remaining)
        ↓
Backend sends SMS to trusted contact:
  "FocusLock: [User] wants to end their session early.
   Time remaining: 1h 23m. Blocked: Gambling, Adult content.
   OTP to unlock: 847291. Share only if you think it's justified."
        ↓
Trusted contact shares OTP with user (via call/message)
        ↓
User enters OTP in app
        ↓
App calls POST /verify-unlock-otp on backend
        ↓
Valid → 10-minute cooldown countdown starts on device
        ↓
Cooldown complete → SessionManager.endSession()
        ↓
VPN disconnects, Accessibility Service stops monitoring
        ↓
Session complete screen shown
```

---

## 7. Reboot Recovery Flow

```
Device reboots
        ↓
BOOT_COMPLETED broadcast received
        ↓
BootReceiver checks Room DB for active session
        ↓
Active session found with future expiry timestamp?
  YES → Restart AccessibilityService, VpnService, SessionManager
       → Show persistent notification
       → Resume countdown from stored expiry time
  NO  → Do nothing
```
