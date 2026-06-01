# User Stories
## FocusLock — Android App
**Version:** 1.0 (MVP)

---

## Epic 1 — Onboarding & Permissions

**US-01**
As a new user, I want to be guided through granting all required permissions (Accessibility Service, VPN, Device Admin, SMS) in a single onboarding flow so I don't have to figure it out myself.

**Acceptance criteria:**
- Each permission has a plain-language explanation of why it is needed
- If a permission is denied, the app explains the consequence and asks again
- App does not proceed to setup until all critical permissions are granted
- Critical permissions: Accessibility Service, Device Admin
- Optional permissions: VPN (degrades to keyword-only blocking), SMS (disables trusted contact feature)

---

**US-02**
As a user, I want to verify and register a trusted contact phone number before my first session so the unlock system is ready when I need it.

**Acceptance criteria:**
- User enters a phone number
- App sends a verification OTP to that number via SMS
- User enters OTP to confirm the number is reachable
- Verified number is saved locally and to backend
- User can update the trusted contact number outside of an active session only

---

## Epic 2 — Keyword & Category Setup

**US-03**
As a user, I want to select from preset categories of content to block so I don't have to think about every keyword manually.

**Acceptance criteria:**
- Categories available: Social media, Adult content, Gambling, News, Gaming, Shopping
- Each category shows example domains it covers
- User can toggle categories on/off
- Selections are saved and persist across sessions

---

**US-04**
As a user, I want to add my own custom keywords so I can block specific content that presets don't cover.

**Acceptance criteria:**
- Text input field accepts any keyword
- Keywords are case-insensitive during matching
- User can delete custom keywords
- Maximum 50 custom keywords for MVP

---

## Epic 3 — Session Activation

**US-05**
As a user, I want to set a timer duration before activating a session so I know exactly how long I am committing to.

**Acceptance criteria:**
- Duration picker with hours and minutes (min: 15 min, max: 72 hours)
- Timer duration is shown clearly on confirmation screen before activation
- Confirmation screen shows: selected keywords, selected categories, timer duration, trusted contact number
- User must tap a deliberate "Lock In" button to activate (not swipe or single tap)

---

**US-06**
As a user, I want the session to activate immediately after I confirm so blocking starts before I change my mind.

**Acceptance criteria:**
- Accessibility Service starts monitoring within 3 seconds of activation
- Local VPN connects within 5 seconds of activation
- Home screen shows active session status with countdown timer
- Persistent notification appears immediately

---

## Epic 4 — Blocking Behaviour

**US-07**
As a user in an active session, I want any browser search containing a blocked keyword to be intercepted so I cannot see the results.

**Acceptance criteria:**
- Accessibility Service detects URL changes in all major browsers
- Matching is done against full URL string (includes search query parameters)
- On match, a full-screen overlay appears immediately
- Overlay shows: what was blocked, time remaining in session, option to request unlock
- Browser does not load the page

---

**US-08**
As a user in an active session, I want known bad domains to be blocked even if the URL doesn't contain my keyword so I can't access them by navigating directly.

**Acceptance criteria:**
- Local VPN intercepts DNS queries
- Blocked domain list covers all selected categories
- Blocked domains return a local redirect page (not an error)
- This works regardless of which browser is used

---

**US-09**
As a user in an active session, I want the app to block my attempt to disable the Accessibility Service, VPN, or Device Admin so I cannot bypass the session through settings.

**Acceptance criteria:**
- Accessibility Service monitors navigation to relevant settings screens
- Overlay appears when user enters Accessibility settings, VPN settings, or Device Admin settings
- Overlay cannot be dismissed during active session
- Back button returns user to home screen

---

## Epic 5 — Unlock Flow

**US-10**
As a user who wants to end a session early, I want to request an unlock that sends an OTP to my trusted contact so they can decide whether to grant it.

**Acceptance criteria:**
- "Request early unlock" button visible on active session screen
- Tapping it triggers SMS to trusted contact immediately
- SMS contains: app name, time of request, what categories/keywords were blocked, time remaining
- OTP is 6 digits, valid for 10 minutes
- User sees a waiting screen after requesting

---

**US-11**
As a user who received an OTP from my trusted contact, I want to enter it to begin the unlock process.

**Acceptance criteria:**
- OTP entry screen appears after unlock request is sent
- Correct OTP triggers a 10-minute visible countdown before unlock
- Incorrect OTP shows an error, allows retry (max 3 attempts)
- After 3 failed attempts, a new OTP must be requested (new SMS sent)
- During the 10-minute cooldown, blocking remains fully active

---

**US-12**
As a user who cannot reach my trusted contact in a genuine emergency, I want a fallback option that unlocks after a 24-hour waiting period without needing the OTP.

**Acceptance criteria:**
- Emergency bypass option is visible but clearly labelled as "24-hour delay unlock"
- Activating it starts a 24-hour countdown — no OTP needed
- Blocking remains fully active during the 24-hour wait
- Timer cannot be cancelled once started
- After 24 hours, session unlocks automatically

---

## Epic 6 — Session End & Reboot Persistence

**US-13**
As a user, I want my session to survive a device reboot so I cannot bypass the lock by restarting my phone.

**Acceptance criteria:**
- Session state is stored in local database with expiry timestamp
- App registers a BOOT_COMPLETED broadcast receiver
- On boot, if an active session exists and hasn't expired, all services restart automatically
- User sees active session screen after reboot, not home screen

---

**US-14**
As a user whose session has expired naturally, I want the app to unlock cleanly and return me to the home screen.

**Acceptance criteria:**
- Services stop automatically when timer reaches zero
- VPN disconnects
- Device Admin is not revoked (stays for next session)
- User sees a session summary screen (time completed, no detailed usage data for MVP)
- Persistent notification is dismissed
