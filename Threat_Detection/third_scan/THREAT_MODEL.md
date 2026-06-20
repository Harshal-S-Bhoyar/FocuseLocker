# FocusLocker Threat Model

## 1. Overview
FocusLocker is a distraction-blocking application consisting of an Android mobile app and a Node.js/Express backend. The system enforces blocking through an Accessibility Service, VPN Service, and Device Admin receiver. The backend handles OTP-based session unlocking for trusted contacts.

## 2. Assets (High-Value Data)
- **User Blocklists:** Configured categories, domains, and keywords to be blocked.
- **Session State:** Whether an active blocking session is running, its duration, and configuration.
- **Trusted Contact PII:** Email address of the designated trusted contact.
- **OTP Codes:** Backend-generated codes used to verify and unlock active sessions.
- **Backend Credentials:** Email service API keys/SMTP credentials, server environment variables.

## 3. Entry Points

### 3.1 Backend API (Node.js/Express)
Exposed to the public internet.
- `POST /api/verify-contact`: Generates and sends OTP to register a contact.
- `POST /api/confirm-contact`: Verifies the OTP to confirm a contact.
- `POST /api/request-unlock`: Generates and sends OTP to a trusted contact for unlocking a session.
- `POST /api/verify-unlock-otp`: Verifies the unlock OTP.
- `GET /health`: Healthcheck endpoint.

### 3.2 Android Application
- **Intents/Receivers:**
  - `MainActivity` (exported: true)
  - `FocusDeviceAdminReceiver` (exported: true, protected by `BIND_DEVICE_ADMIN`)
  - `BootReceiver` (exported: true, protected by `BOOT_COMPLETED` action)
- **UI Inputs:** Keyword entry, category toggling, trusted contact email input.
- **System Interactions:** Accessibility Service (reading screen content, typing), VPN Service (intercepting network traffic).

## 4. Trust Boundaries
- **TB-1 (Internet -> Backend):** Untrusted requests reach the Express server over HTTP/HTTPS.
- **TB-2 (User -> Android App):** The user provides inputs to configure the blocking rules and trusted contact.
- **TB-3 (Android App -> Backend):** The Android app sends data to the backend API.
- **TB-4 (Android App -> OS):** The app interacts with Android system services (Accessibility, VPN, Device Admin) to enforce blocks.
- **TB-5 (Backend -> External Services):** Backend sends emails via an external provider.

## 5. Threat Identification (STRIDE)
- **Spoofing:** An attacker could spoof requests to the backend API pretending to be the Android app, attempting to trigger OTPs to arbitrary emails.
- **Tampering:** A user attempting to bypass the block might try to tamper with local storage, preferences, or intent inputs to disable the Accessibility/VPN service or clear the active session.
- **Repudiation:** Insufficient logging on the backend might prevent identifying abuse of the OTP endpoints.
- **Information Disclosure:** Hardcoded secrets in the Android app or backend, or insecure logging of OTPs/PII.
- **Denial of Service (DoS):** Attacker could spam the backend OTP endpoints, exhausting email service quotas or causing the backend to crash. Attacker could also trigger rapid UI updates in the Android app to exhaust memory or CPU.
- **Elevation of Privilege:** A malicious app could exploit exported components (e.g., `MainActivity` with specific intent extras) to manipulate FocusLocker's state or bypass blocks.

## 6. Focus Areas for Audit
1. **Backend Rate Limiting & DoS:** Analyze the Express rate limiting configuration and payload validation.
2. **Backend Authentication/Authorization:** Ensure OTP verification logic prevents bypasses and brute-force attacks.
3. **Android Exported Components:** Analyze `MainActivity` and `BootReceiver` for unintended intent processing.
4. **Android Service Resiliency:** Ensure the Accessibility Service and VPN Service cannot be easily bypassed or killed by the user without authorization.
5. **Data Validation:** Validate inputs on both Android UI and Backend schemas (e.g., email formats, long strings, array bounds).
