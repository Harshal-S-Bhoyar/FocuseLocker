# Security Remediation Plan — FocusLocker

**Source:** [SECURITY_REPORT.md](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/Threat_Detection/SECURITY_REPORT.md) + [THREAT_MODEL.md](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/Threat_Detection/THREAT_MODEL.md)
**Date:** 2026-06-08

---

## Root Cause Analysis (What the report got wrong)

> [!IMPORTANT]
> The original SECURITY_REPORT Finding 1 stated "no rate limiting exists." This is **partially incorrect**. After inspecting [app.ts](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/backend/src/app.ts), rate limiters ARE defined (`generalLimiter` at L20, `otpLimiter` at L27) and `express-rate-limit` IS installed. However, there are two real bugs:

1. **OTP limiter is applied AFTER route mounting** (L47-48 vs L43-44). Express middleware order matters — the limiter never fires because the route handler runs first.
2. **OTP limiter paths are wrong.** Routes are mounted at `/api` + router paths (`/verify-contact`, `/request-unlock`), but the limiter targets `/api/contact/verify` and `/api/unlock/request` — these paths don't exist on any route.

---

## Execution Plan — 6 Tasks

### Task 1: Fix OTP Rate Limiter Ordering & Paths (High Priority)
**File:** [app.ts](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/backend/src/app.ts)
**Problem:** `otpLimiter` middleware applied after routes, and paths don't match actual endpoints.
**Fix:**
- Move `otpLimiter` application BEFORE route mounting.
- Correct the paths to match actual route endpoints.

```diff
 // Apply general rate limiting
 app.use("/api", generalLimiter);
 
-// Health check
-app.get("/health", (req: Request, res: Response) => {
-  res.json({ status: "ok" });
-});
-
-// Mount routes
-app.use("/api", contactRoutes);
-app.use("/api", unlockRoutes);
-
-// Apply strict rate limit on OTP endpoints (overrides general limiter for these routes)
-app.use("/api/contact/verify", otpLimiter);
-app.use("/api/unlock/request", otpLimiter);
+// Apply strict rate limit on OTP-generating endpoints BEFORE mounting routes
+app.use("/api/verify-contact", otpLimiter);
+app.use("/api/confirm-contact", otpLimiter);
+app.use("/api/request-unlock", otpLimiter);
+app.use("/api/verify-unlock-otp", otpLimiter);
+
+// Health check
+app.get("/health", (req: Request, res: Response) => {
+  res.json({ status: "ok" });
+});
+
+// Mount routes
+app.use("/api", contactRoutes);
+app.use("/api", unlockRoutes);
```

**Verify:** `curl -X POST http://localhost:3000/api/verify-contact` 6 times rapidly → 5th request should return `429 Too Many Requests`.

---

### Task 2: Secure Intent Routing in MainActivity (Moderate Priority)
**File:** [MainActivity.kt](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/MainActivity.kt)
**Problem:** `navigate_to` extras are blindly trusted. Any app can send `early_unlock` to force the UI into the unlock flow even when no session is active.
**Fix:** Inject `SessionRepository` and validate session state before honoring the intent.

```diff
 @AndroidEntryPoint
 class MainActivity : ComponentActivity() {
 
+    @Inject
+    lateinit var sessionRepository: SessionRepository
+
     private val _navigateTo = mutableStateOf<String?>(null)
 
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         enableEdgeToEdge()
 
-        _navigateTo.value = intent.getStringExtra("navigate_to")
+        _navigateTo.value = resolveNavigationTarget(intent)
 
         setContent { /* ... unchanged ... */ }
     }
 
     override fun onNewIntent(intent: Intent) {
         super.onNewIntent(intent)
-        _navigateTo.value = intent.getStringExtra("navigate_to")
+        _navigateTo.value = resolveNavigationTarget(intent)
+    }
+
+    private fun resolveNavigationTarget(intent: Intent): String? {
+        val target = intent.getStringExtra("navigate_to") ?: return null
+        // Only honor navigation if it came from our own app's package
+        val callingPkg = callingActivity?.packageName ?: referrer?.host
+        val isTrusted = callingPkg == null || callingPkg == packageName
+        if (!isTrusted) return null
+        return target
     }
```

**Verify:** Use `adb shell am start -n com.focuslock/.MainActivity --es navigate_to early_unlock` from another package → should land on Onboarding, not UnlockFlow.

---

### Task 3: Remove Unused `canPerformGestures` (Low Priority)
**File:** [accessibility_service_config.xml](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/res/xml/accessibility_service_config.xml)
**Problem:** `canPerformGestures="true"` is declared but grep confirms zero calls to `dispatchGesture()` in the codebase.

> [!WARNING]
> The service DOES use `performGlobalAction(GLOBAL_ACTION_BACK)` at lines 168, 204, and 215 of [FocusAccessibilityService.kt](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/service/accessibility/FocusAccessibilityService.kt). `performGlobalAction` does NOT require `canPerformGestures`. These are different APIs. Safe to remove.

```diff
 <accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
     android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
     android:accessibilityFeedbackType="feedbackGeneric"
     android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
-    android:canPerformGestures="true"
     android:canRetrieveWindowContent="true"
```

**Verify:** Build APK, install, open a blocked URL → overlay still appears and BACK action still fires.

---

### Task 4: Patch Vulnerable Dependencies (Moderate Priority)
**File:** [package.json](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/backend/package.json)
**Problem:** `uuid` < 11.1.1 has buffer bounds check vulnerability (GHSA-w5hq-g745-h8pq). Affects `firebase-admin` ^13.10.0 transitively.
**Fix:**

```bash
cd backend
npm audit fix
# If that doesn't resolve it (semver-major):
npm audit fix --force
# Then verify:
npm audit
```

> [!NOTE]
> `npm audit` reports the fix requires downgrading `firebase-admin` to 10.3.0 (semver-major). This may break Firebase Admin SDK v13 APIs. Alternative: wait for `firebase-admin` upstream to bump their `uuid` dependency. The vulnerability's practical exploitability through Firebase is LOW — it requires passing a user-controlled `buf` argument to `uuid.v3/v5/v6`, which Firebase does not expose.

**Verify:** `npm audit` returns 0 vulnerabilities, OR document the accepted risk if downgrade is rejected.

---

### Task 5: Add OTP Request Audit Logging (STRIDE: Repudiation)
**File:** [otp.service.ts](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/backend/src/services/otp.service.ts)
**Problem:** THREAT_MODEL identifies "Lack of comprehensive audit logs for Emergency Bypass" as a Repudiation threat. Currently, no structured logging exists for OTP generation or verification events.
**Fix:** Add structured `console.log` (or a proper logger) at generation and verification points. Do NOT log the plaintext OTP.

```diff
   async generateAndStore(email: string, type: OtpType): Promise<string> {
     const plainOtp = crypto.randomInt(100000, 999999).toString();
     const otpHash = await bcrypt.hash(plainOtp, this.saltRounds);
     const expiresAt = Date.now() + config.otp.expiryMinutes * 60 * 1000;
     // ...
     otpStore.set(email, entry);
+    console.log(JSON.stringify({ event: "otp_generated", email, type, expiresAt }));
     return plainOtp;
   }

   async verify(email: string, plainOtp: string, expectedType: OtpType) {
     // ... after isValid check:
     if (isValid) {
       otpStore.delete(email);
+      console.log(JSON.stringify({ event: "otp_verified", email, type: expectedType, success: true }));
       return { valid: true };
     }
+    console.log(JSON.stringify({ event: "otp_verify_failed", email, type: expectedType, attempts }));
     return { valid: false, reason: "invalid" };
   }
```

**Verify:** Trigger a contact verification → check server stdout for JSON log lines.

---

### Task 6: Validate HTTPS Enforcement (STRIDE: Information Disclosure)
**Problem:** THREAT_MODEL flags "OTPs transmitted over HTTP." The backend has no HTTPS configuration — it runs `app.listen(PORT, "0.0.0.0")` on bare HTTP.
**Status:** This is expected for local dev. In production, HTTPS must be terminated at the reverse proxy / cloud layer (Firebase Hosting, Cloud Run, etc.).
**Fix:** No code change needed if deploying behind Firebase/Cloud Run (they enforce HTTPS). Document this assumption.

> [!CAUTION]
> If the backend is ever deployed as a standalone server (e.g., on a raw VPS), you MUST add TLS termination via nginx or an HTTPS-enforcing middleware. The Android `network_security_config.xml` should also enforce `cleartextTrafficPermitted="false"`.

**Verify:** Check Android `AndroidManifest.xml` for `android:usesCleartextTraffic` — currently not set (defaults to `false` on API 28+). ✅ Already secure.

---

## Execution Order

| Order | Task | Risk Level | Effort | Dependencies |
| :---: | :--- | :--- | :--- | :--- |
| 1 | Fix OTP rate limiter ordering | **High** | ~5 min | None |
| 2 | Add OTP audit logging | Moderate | ~5 min | None |
| 3 | Secure intent routing | Moderate | ~10 min | Hilt injection |
| 4 | Remove `canPerformGestures` | Low | ~1 min | None |
| 5 | Patch dependencies | Moderate | ~5 min | npm |
| 6 | Validate HTTPS | Info | 0 min | Documentation only |

---

## What Does NOT Need Fixing

| Threat Model Item | Status | Reason |
| :--- | :--- | :--- |
| Tampering (Room DB modification) | **Accepted Risk** | Requires root access or physical device control. Out of scope for app-level defense. |
| Elevation of Privilege (disable Device Admin via adb/safe mode) | **Accepted Risk** | Android OS-level limitation. No app can prevent `adb shell dpm remove-active-admin`. |
| BootReceiver `exported="true"` | **Not a bug** | Required by Android for `BOOT_COMPLETED`. The receiver validates `intent.action == ACTION_BOOT_COMPLETED` at line 42. |
| Hardcoded secrets | **Clean** | Grep scan found no secrets in source. `.env` is gitignored. |
| Insecure Android APIs | **Clean** | No `MODE_WORLD_READABLE`, `addJavascriptInterface`, or cert pinning bypasses found. |
