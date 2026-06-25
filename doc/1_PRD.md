# Product Requirements Document (PRD)
## App Name: FocusLock
**Version:** 1.0 (MVP)
**Platform:** Android only
**Last Updated:** 2026

---

## 1. Problem Statement

People struggle with compulsive and distracted browsing on their phones. Existing blocker apps are too easy to bypass — one tap and the block is gone. There is no app that combines keyword-level browser blocking, a time-locked session, and a trusted contact unlock mechanism that creates real social accountability.

---

## 2. Target User

**Primary user (MVP):** The app is built for personal use first — someone who wants to voluntarily block distracting or harmful content for a set time period and genuinely cannot bypass it alone.

**User profile:**
- Android user
- Aware of their own distraction/browsing problem
- Motivated to change but needs external friction to follow through
- Has at least one trusted person (friend, partner, family member) willing to act as accountability partner

---

## 3. Core Features (MVP Only)

### Feature 1 — Keyword & Category Blocking
- User selects from a preset list of categories (social media, adult content, gambling, news, gaming)
- User can add custom keywords
- App blocks any browser URL containing those keywords using Android Accessibility Service
- App blocks known domains in selected categories using a local on-device VPN
- Works across all installed browsers (Chrome, Firefox, Brave, Opera, etc.)

### Feature 2 — Timer Lock
- User sets a duration before activating the block (minimum 15 minutes, maximum 72 hours)
- Once activated, blocking cannot be stopped until the timer expires
- Timer persists across app restarts and device reboots
- App uses Device Admin API to prevent uninstallation during active timer

### Feature 3 — Trusted Contact Unlock
- Before starting a session, user registers one trusted contact **email address**
- Email address is verified via OTP at registration time
- If user wants to unlock early, the app sends an OTP + context email to the trusted contact's address
- Context email tells the trusted contact: what was blocked, how much time remains, and timestamp of request
- Trusted contact shares the OTP manually with the user
- After correct OTP entry, a mandatory 10-minute cooldown applies before the session unlocks
- If trusted contact is unreachable, a 24-hour emergency delay bypass is available (no OTP needed, just wait)

### Feature 4 — Settings Protection
- During an active session, the app uses Accessibility Service to detect if the user navigates to:
  - Accessibility settings (to disable the service)
  - VPN settings (to disable the VPN)
  - Device Admin settings (to revoke admin)
- App shows a full-screen blocking overlay on those screens during active timer

---

## 4. Out of Scope for MVP

- iOS version
- Web dashboard
- Multiple trusted contacts
- Usage analytics or reports
- Scheduled recurring sessions
- Browser extension companion
- Parental control mode

---

## 5. Success Metrics (Personal Testing Phase)

- Timer lock holds across browser switches (Chrome → Firefox → Brave)
- Keywords detected correctly in Google, Bing, DuckDuckGo search URLs
- Trusted contact receives OTP SMS within 30 seconds
- Uninstall blocked during active session via Device Admin
- Settings bypass overlay triggers correctly on Accessibility and VPN settings screens
- App survives device reboot with session still active

---

## 6. Known Limitations (Document These Honestly)

| Limitation | Impact | Mitigation |
|---|---|---|
| Factory reset bypasses everything | High | Nothing can prevent this — accept it |
| Force stop kills services temporarily | Medium | Foreground service with persistent notification reduces this |
| Trusted contact can forward OTP without question | High | Context email reduces this |
| Accessibility Service may be killed by OEM battery optimization | Medium | Battery exemption + OemAutostartHelper autostart intents |
| Domain blocking only (no keyword) for non-browser apps | Low | Out of scope for MVP |
| Email delivery not guaranteed in all regions | Medium | Show warning if email fails, retry option |

---

## 7. User Flow Summary

```
Install app
  → Grant permissions (Accessibility, VPN, Device Admin, Internet)
  → Register trusted contact email address (OTP verification)
  → Select keywords / categories to block
  → Set timer duration
  → Activate session
      → Browsing blocked in all browsers
      → Uninstall blocked
      → Settings screens protected
      → Timer counts down
          → Timer expires → Session ends automatically
          → User requests early unlock
              → OTP sent to trusted contact via email (Brevo)
              → Trusted contact shares OTP
              → User enters OTP
              → 10-minute cooldown
              → Session unlocks
```
