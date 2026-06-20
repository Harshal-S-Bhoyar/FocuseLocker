# Defender Scan Findings

## VULN-001: Server-Side Request Forgery (SSRF) via DNS Rebinding
- **Severity**: High
- **Confidence**: High
- **Location**: `app/src/main/java/com/focuslock/domain/usecase/MetaDescriptionFetcher.kt`
- **Focus Area**: Android SmartScan Usecase
- **CWE**: CWE-918

### Description
The `isPrivateHost` validation checks if an IP address is private using `InetAddress.getByName(host)` before opening the HTTP connection. However, `URL.openConnection()` independently resolves the hostname again. An attacker can perform a DNS rebinding attack by serving a short-TTL DNS record that points to a public IP during the validation phase, and then immediately points to a private/internal IP (e.g., 127.0.0.1) during the HTTP connection phase, successfully bypassing the protection.

---

## VULN-002: Rate Limiting Bypass on OTP Endpoints via Variable Emails
- **Severity**: Medium
- **Confidence**: High
- **Location**: `backend/src/app.ts`
- **Focus Area**: Backend Authentication/Authorization
- **CWE**: CWE-799

### Description
The `otpLimiter` uses a custom `keyGenerator` that limits requests based on the provided email address, falling back to the IP address only if no email is provided. Because the API endpoints require an email, an attacker can bypass the per-IP rate limit by sending requests with different, random email addresses. This allows a single IP to spam the OTP generation endpoint, potentially exhausting external email service quotas and causing financial loss (DoS).
