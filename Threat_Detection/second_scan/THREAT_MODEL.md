# FocusLocker Threat Model

## 1. Assets
- **High Value**: 
  - User Authentication Tokens (Firebase Auth tokens).
  - One-Time Passwords (OTPs) and associated user email addresses.
  - User configuration and local database (`FocusDatabase`), containing blocked domains and lock schedules.
- **Medium Value**: 
  - Application configuration (`blocked_domains.json`, settings).
  - Usage statistics and lock state metadata.

## 2. Entry Points
- **Android Application**:
  - `FocusAccessibilityService`: Ingests window content and active URLs from the Android OS and external applications.
  - `FocusVpnService`: Intercepts outbound/inbound network traffic.
  - `SmartScanUseCase`: Performs network fetches (via Jsoup) to arbitrary user-visited URLs to extract meta tags.
  - Deep links and Intents from external applications.
  - Android Device Admin actions.
- **Node.js Backend**:
  - Express REST API Endpoints (e.g., `/unlock`, `/contact`).
  - Firebase Admin SDK interfaces.

## 3. Trust Boundaries
- **Internet -> App (SmartScan)**: Untrusted web content (HTML metadata) flows into the app's `SmartScanUseCase`.
- **OS/Apps -> App (Accessibility/VPN)**: Untrusted window events, UI node content, and network packets flow into the app's services.
- **Internet -> Backend (API)**: Untrusted client requests flow into the Node.js Express server.
- **Backend -> Firebase**: Trusted backend communicating with the Firebase service.

## 4. Threats (STRIDE)
- **Spoofing**: 
  - Malicious apps spoofing accessibility events or intents to bypass the lock screen.
  - Forged API requests to the Node.js backend.
- **Tampering**: 
  - Modification of the local SQLite database (`FocusDatabase`) to unblock domains.
  - Adversarial webpages crafting malicious HTML meta tags to exploit `SmartScanUseCase` (RCE, XSS if rendered, or DoS).
- **Repudiation**: 
  - Lack of logging for unlock events or API failures.
- **Information Disclosure**: 
  - Leaking user browsing history through the accessibility service or VPN service logs/crashes.
  - Backend API exposing PII or Firebase tokens.
- **Denial of Service (DoS)**: 
  - Malicious pages causing `SmartScanUseCase` to timeout or crash due to large payloads.
  - Overwhelming the backend `/unlock` API (rate limiting failure).
- **Elevation of Privilege**: 
  - Bypassing the Device Admin or Accessibility lock to gain unrestricted access to the device.
