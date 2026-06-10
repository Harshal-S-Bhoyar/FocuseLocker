# FocusLocker Security Audit Report (Defender Agent)

## 1. Executive Summary

A comprehensive automated security audit was conducted on the FocusLocker project using the Defender security pipeline. The audit encompassed both the Android application (`app/`) and the Node.js Express backend (`backend/`).

**Overall Security Posture:**
The application relies heavily on proper access controls, Android accessibility/VPN isolation, and a remote API for OTP/Unlock mechanisms. The baseline security configuration is acceptable (helmet, bcrypt, Zod validation); however, 3 key vulnerabilities were identified primarily concerning Denial of Service (DoS) and Request Forgery.

**Findings Summary:**
- **High Severity:** 1 (Backend DoS via unbounded arrays)
- **Medium Severity:** 1 (Rate limiting bypass)
- **Low Severity:** 1 (Internal SSRF in SmartScan)

All vulnerabilities have been triaged, verified, and supplied with corresponding minimal patches in the `PATCHES/` directory.

---

## 2. Detailed Findings

### VULN-001: Unbounded Array Length in Unlock Request Schema (Backend DoS)
- **Severity:** High
- **Component:** Backend (`backend/src/schemas/request.schemas.ts`)
- **Description:** The `requestUnlockSchema` accepts `blockedCategories` and `blockedKeywords` as string arrays but enforces no maximum length. An attacker can send extremely large arrays (e.g., millions of elements), which the server processes synchronously via `.join(", ")`. This can lead to V8 Memory Exhaustion (OOM crashes) or trigger payload rejections by the downstream email service provider (Brevo).
- **Remediation:** Added a `.max(100)` constraint to both Zod array definitions.
- **Patch Location:** `PATCHES/VULN-001/patch.diff`

### VULN-002: Server-Side Request Forgery (SSRF) in MetaDescriptionFetcher
- **Severity:** Low
- **Component:** Android App (`app/src/main/java/com/focuslock/domain/usecase/MetaDescriptionFetcher.kt`)
- **Description:** The `SmartScanUseCase` fetches and parses meta tags for arbitrary domains visited by the user. The `MetaDescriptionFetcher` lacked validation against private/local IP addresses (`127.0.0.1`, `192.168.x.x`, etc.). Although the attack originates from the user's own device, malicious webpages could craft redirects to local services, enabling blind SSRF against the user's internal network (e.g., home routers, IoT devices) from the context of the Android app.
- **Remediation:** Implemented an `isPrivateIp` check using Regex to block all local and private IPv4 ranges before initiating the HTTP connection.
- **Patch Location:** `PATCHES/VULN-002/patch.diff`

### VULN-003: Rate Limiting Bypass via IP Rotation
- **Severity:** Medium
- **Component:** Backend (`backend/src/app.ts`)
- **Description:** The OTP and contact endpoints are protected by `express-rate-limit` relying solely on the client IP address (`req.ip`). A malicious actor with access to a proxy pool can rotate IPs to bypass the 5 requests / 15 minutes limit, allowing them to spam a specific victim's email address endlessly. Additionally, NAT environments (like corporate networks) could result in legitimate users being blocked by the actions of others.
- **Remediation:** Modified the rate limiter's `keyGenerator` to pivot on the target email address (`req.body.email` or `req.body.trustedContactEmail`) if available, falling back to IP.
- **Patch Location:** `PATCHES/VULN-003/patch.diff`

---

## 3. Strategic Recommendations

1. **Adversarial Network Resilience:** Currently, Android `SmartScan` relies on `HttpURLConnection` which blocks the coroutine thread. Consider using a more robust client like `OkHttp` with strict timeouts, interceptors for IP blocking, and response size limits (to prevent downloading gigabytes of junk).
2. **Backend Scalability Security:** The backend OTP storage is currently in-memory (`otpStore` Map). In a multi-instance production environment, this will cause inconsistent state and rate limiting failures. Migrate `otpStore` and `express-rate-limit` to Redis.
3. **Intent Spoofing Review:** While not directly exploitable in the current scan, ensure that all internal Android Broadcast Receivers and Services not explicitly meant for external use have `android:exported="false"` (verified true for `FocusAccessibilityService` and `FocusVpnService`).
