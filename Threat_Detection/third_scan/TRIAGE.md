# Defender Triage Report

## Verified Vulnerabilities (True Positives)

### VULN-001: Server-Side Request Forgery (SSRF) via DNS Rebinding
- **Status**: True Positive
- **Severity**: High (Upgraded due to ease of exploitation and automated scanning behavior)
- **Root Cause Location**: `app/src/main/java/com/focuslock/domain/usecase/MetaDescriptionFetcher.kt`

**Preconditions**: 
Attacker must convince the user to visit an attacker-controlled domain in a supported browser. The app's automated scanning will intercept the URL and scan it.

**Attack Path**: 
1. User visits `attacker.com`.
2. FocusLocker intercepts URL and extracts domain.
3. FocusLocker's `SmartScanUseCase` attempts to fetch meta description.
4. `attacker.com` DNS returns a public IP during the `isPrivateHost` validation.
5. `URL.openConnection()` resolves DNS again, and attacker returns a private IP (e.g., 127.0.0.1).
6. FocusLocker fetches sensitive internal services on the local network and parses them.

**Impact**: 
Exposes local or private network services (e.g., router admin pages, local debug endpoints) to an attacker.

**Remediation**: 
Disable DNS resolution entirely during validation, or use a custom `SocketFactory`/`HostnameVerifier` that pins the validated IP address for the HTTP connection, preventing the secondary DNS lookup.

---

### VULN-002: Rate Limiting Bypass on OTP Endpoints via Variable Emails
- **Status**: True Positive
- **Severity**: High (Upgraded due to risk of financial DoS and lack of authentication required)
- **Root Cause Location**: `backend/src/app.ts`

**Preconditions**: 
Attacker can send POST requests to the backend API over the internet.

**Attack Path**: 
1. Attacker writes a script to repeatedly POST to `/api/verify-contact` with a different email on each request.
2. The rate limiter keys off the email instead of the IP, treating each request as a new client.
3. The IP-based fallback in the key generator is never triggered because an email is provided.

**Impact**: 
Exhausts the email sending quotas and causes Denial of Service (DoS) for legitimate users, alongside potential financial loss.

**Remediation**: 
Implement a strict IP-based rate limiter for the OTP endpoint that cannot be bypassed, or limit the number of OTP requests per IP globally alongside the email limiter.
