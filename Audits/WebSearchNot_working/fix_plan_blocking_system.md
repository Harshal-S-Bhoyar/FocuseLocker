# FocusLocker — Blocking System Fix Plan

## Phase 0 — Understanding

**Task:** Fix two independent but cascading bug chains:
1. **NextDNS never receives DNS queries** for domains already locally auto-blocked (cascade from fail-closed)
2. **MetaDescriptionFetcher incorrectly classifies search engine pages** as empty/timed-out → triggers fail-closed → auto-blocks search engines permanently

**Scope (files to change):**

| File | Change Type |
|------|-------------|
| `SmartScanUseCase.kt` | Fail-closed → fail-open for FetchTimeout/FetchEmpty |
| `MetaDescriptionFetcher.kt` | Timeout bump + remove IP-pinning SSRF for search engines + threshold fix |
| `BlockedDomainDao.kt` | DAO query — exclude TEMPORARY from sync blocklist |
| `SmartScanUseCase.kt` | Add search engine safelist (bypass SmartScan) |

**Success criteria:**
- `google.com/search?q=anything` → NOT blocked
- NextDNS query log shows entries for every DNS resolution through VPN
- SmartScan still blocks content-matched pages (keyword scan works)
- SSRF protection still active (private IPs still blocked)

---

## Phase 1 — Karpathy Check

- [x] Root causes confirmed via source code, not assumptions
- [x] Minimal changes only — 4 targeted edits
- [x] No new abstractions — edits in existing functions
- [x] Every change traces directly to a confirmed bug

---

## Fix 1 — SmartScan: Fail-Closed → Fail-Open

**File:** [SmartScanUseCase.kt:59-67](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/domain/usecase/SmartScanUseCase.kt#L59-L67)

**Root cause:** `FetchTimeout` and `FetchEmpty` are **fetch infrastructure failures** — not evidence of blocked content. The fail-closed policy interprets "I couldn't read the page" as "the page must be bad." This is incorrect.

**Debugger diagnosis:**
- Error: `AUTO_BLOCKED: fail_closed` in overlay
- Location: `SmartScanUseCase.kt:64` — `SmartScanResult.Blocked("timeout", "fail_closed")`
- Reproduction: Visit any JS-rendered page (Google, Bing) → 4s timeout expires → auto-blocked
- Hypothesis confirmed: Search engines serve JS-rendered pages → 8KB HTML range has no content → `totalHtml.length < 50` → `FetchEmpty`

**Fix:**

```diff
// SmartScanUseCase.kt — lines 59-67
-        when (outcome) {
-            is FetchOutcome.FetchTimeout, is FetchOutcome.FetchEmpty -> {
-                // FAIL-CLOSED: Temporarily block for 1 hour
-                val expiresAt = System.currentTimeMillis() + 60 * 60 * 1000L
-                blockingRepository.addAutoBlockedDomain(domain, "", "TEMPORARY", expiresAt)
-                val result = SmartScanResult.Blocked("timeout", "fail_closed")
-                memoryCache.put(domain, CacheEntry(result, System.currentTimeMillis()))
-                return result
-            }
+        when (outcome) {
+            is FetchOutcome.FetchTimeout, is FetchOutcome.FetchEmpty -> {
+                // FAIL-OPEN: No content evidence available — do NOT block.
+                // Re-scan on next visit. Cache nothing to avoid stale false-negatives.
+                return SmartScanResult.FetchFailed("fetch_incomplete")
+            }
```

**Verify:**
- Visit `google.com/search?q=test` → should show `FetchFailed` in log → NOT blocked
- Visit a known-bad domain with content → still blocked (keyword match path unchanged)

**Risk:** Low. `FetchFailed` is already handled in `FocusAccessibilityService` — it simply allows the page (same as before for `FetchError`).

---

## Fix 2 — MetaDescriptionFetcher: Three Sub-Fixes

**File:** [MetaDescriptionFetcher.kt](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/domain/usecase/MetaDescriptionFetcher.kt)

### 2a — Timeout: 4000ms → 8000ms

**Root cause:** Mobile networks on 4G/LTE add 200-800ms RTT. Search engine TLS handshake + first byte adds 500-1500ms. 4s budget is exhausted before any HTML arrives.

**Location:** Line 59

```diff
-                return@withContext withTimeout(4000L) {
+                return@withContext withTimeout(8000L) {
```

### 2b — Remove IP-Pinning (safeUrl) SSRF bypass for SNI-sensitive hosts

**Root cause:** Line 65 builds a URL with the resolved IP address instead of the hostname:
```kotlin
val safeUrl = URL(url.protocol, addr.hostAddress, url.port, url.file).toString()
// produces: https://142.250.80.46/search?q=...  (instead of https://google.com/search?q=...)
```
Google, Bing, and other search engines use **SNI (Server Name Indication)** for TLS and **CDN routing based on hostname**. Connecting to the IP directly causes:
- TLS handshake failure (SNI mismatch → connection reset)
- CDN returns 400/421 error
- Exception → `putCache(cacheKey, emptyMap())` at line 118 → `FetchEmpty`

The SSRF protection goal is valid, but IP-pinning breaks TLS for SNI hosts. **The correct SSRF protection is the private-IP check before the request — not rewriting the URL.**

```diff
-                    val safeUrl = URL(url.protocol, addr.hostAddress, url.port, url.file).toString()
-                    
-                    val request = Request.Builder()
-                        .url(safeUrl)
-                        .get()
-                        .header("Host", url.host)
+                    // SSRF protection: private IP already rejected above.
+                    // Use original hostname to preserve SNI / CDN routing.
+                    val request = Request.Builder()
+                        .url(url.toString())
+                        .get()
```

**SSRF safety preserved:** The `isPrivateHost(addr)` check at line 62 still blocks all private/loopback/link-local IPs before any request is made. Only the URL rewrite is removed — the IP check stays.

### 2c — Minimum Content Threshold: 50 → 200 chars

**Root cause:** Line 97 — `totalHtml.length < 50` flags pages as empty when they return minimal boilerplate. Valid search engine pages with a `<head>` block containing `<title>` tags are well above 200 chars even if body is deferred.

```diff
-                        if (totalHtml.isBlank() || totalHtml.length < 50) {
+                        if (totalHtml.isBlank() || totalHtml.length < 200) {
```

**Combined Fix 2 diff (full function context):**

```diff
// MetaDescriptionFetcher.kt
 
         var lastException: Exception? = null
         repeat(2) { attempt ->
             var response: okhttp3.Response? = null
             try {
-                return@withContext withTimeout(4000L) {
+                return@withContext withTimeout(8000L) {
                     val url = URL(if (urlStr.startsWith("http")) urlStr else "https://$urlStr")
                     val addr = try { InetAddress.getByName(url.host) } catch (e: Exception) { throw Exception("DNS resolution failed") }
                     if (isPrivateHost(addr)) {
                         throw Exception("Blocked: private address")
                     }
-                    val safeUrl = URL(url.protocol, addr.hostAddress, url.port, url.file).toString()
-                    
-                    val request = Request.Builder()
-                        .url(safeUrl)
-                        .get()
-                        .header("Host", url.host)
+                    // SSRF: private IP already checked above. Keep hostname for SNI.
+                    val request = Request.Builder()
+                        .url(url.toString())
+                        .get()
                         .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                         .header("Range", "bytes=0-8191")
                         .build()
 
                     ...
 
-                        if (totalHtml.isBlank() || totalHtml.length < 50) {
+                        if (totalHtml.isBlank() || totalHtml.length < 200) {
```

**Verify:**
- `curl -H "Range: bytes=0-8191" https://google.com` → confirm HTML > 200 chars
- Fetch a known-content page → `extractSignals()` returns valid map
- Fetch a known private IP → still throws `Blocked: private address`

**Risk:** Medium (SSRF change). Private IP check at line 62 remains intact — the removed line 65 only rewrote the URL, it never provided additional security.

---

## Fix 3 — BlockedDomainDao: Exclude TEMPORARY from Sync Blocklist

**File:** [BlockedDomainDao.kt:28-42](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/data/local/dao/BlockedDomainDao.kt#L28-L42)

**Root cause:** Both `getAllBlockedDomainList()` and `getAllBlockedDomainListFlow()` include `TEMPORARY` entries. This means a domain auto-blocked by fail-closed immediately appears in `activeDomains` → synchronous `DOMAIN_BLOCKED` on next URL event.

`TEMPORARY` entries exist only to cache a "fetch failed" result and should **not** be treated as blocked domains in the sync list.

```diff
     @Query("""
         SELECT DISTINCT bd.domain 
         FROM blocked_domains bd
         INNER JOIN blocked_categories bc ON bd.category_key = bc.category_key
-        WHERE bc.is_active = 1 AND (bd.block_status IS NULL OR bd.block_status != 'CLEAN_CACHE')
+        WHERE bc.is_active = 1 
+          AND (bd.block_status IS NULL OR bd.block_status = 'PERMANENT')
+          AND (bd.expires_at IS NULL OR bd.expires_at > :now)
     """)
-    suspend fun getAllBlockedDomainList(): List<String>
+    suspend fun getAllBlockedDomainList(now: Long = System.currentTimeMillis()): List<String>
     
     @Query("""
         SELECT DISTINCT bd.domain 
         FROM blocked_domains bd
         INNER JOIN blocked_categories bc ON bd.category_key = bc.category_key
-        WHERE bc.is_active = 1 AND (bd.block_status IS NULL OR bd.block_status != 'CLEAN_CACHE')
+        WHERE bc.is_active = 1 
+          AND (bd.block_status IS NULL OR bd.block_status = 'PERMANENT')
     """)
     fun getAllBlockedDomainListFlow(): Flow<List<String>>
```

> [!NOTE]
> The Flow query intentionally omits `expires_at` filter — Room cannot bind parameters in Flow queries the same way. Expired entries are cleaned by `deleteExpiredEntries()` which runs on every `getCachedDomainStatus()` call.

**Verify:**
- After a timeout on google.com: DB has `block_status = TEMPORARY` for `google.com`
- `getAllBlockedDomainList()` → `google.com` NOT in result
- `getAllBlockedDomainListFlow()` → `google.com` NOT in emitted list
- `activeDomains` in accessibility service does NOT contain `google.com`
- Second visit to `google.com` → Step 1 sync check passes → SmartScan runs again

**Risk:** Low. `PERMANENT` entries (from `blocked_domains.json`, `hosts.txt`, and keyword-matched auto-blocks) are unaffected.

---

## Fix 4 — SmartScan: Search Engine Safelist (Bypass Meta-Fetch)

**File:** [SmartScanUseCase.kt](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/domain/usecase/SmartScanUseCase.kt)

**Purpose:** Search engines cannot be evaluated by meta-fetch (JS-rendered, no meaningful static content). Scanning them is wasteful and unreliable. Skip the entire SmartScan for these domains.

```diff
 @Singleton
 class SmartScanUseCase @Inject constructor(
     private val fetcher: MetaDescriptionFetcher,
     private val blockingRepository: BlockingRepository,
     private val keywordScanner: KeywordScanner
 ) {
 
+    // Domains that are inherently search infrastructure — meta-fetch provides no signal.
+    // SmartScan is skipped for these. Keyword blocking (CheckUrlForBlockedContentUseCase)
+    // still applies to the full URL path (e.g. google.com/search?q=casino).
+    private val SEARCH_ENGINE_DOMAINS = setOf(
+        "google.com", "bing.com", "duckduckgo.com", "yahoo.com",
+        "yandex.com", "baidu.com", "ecosia.org", "startpage.com",
+        "search.brave.com", "qwant.com", "mojeek.com", "swisscows.com",
+        "you.com", "perplexity.ai"
+    )
+
     ...
 
     suspend operator fun invoke(urlStr: String, activeKeywords: List<String>): SmartScanResult {
         if (activeKeywords.isEmpty()) return SmartScanResult.Clean
 
         val domain = DomainExtractor.extract(urlStr) ?: return SmartScanResult.Clean
+
+        // Skip SmartScan for search engines — they are JS-rendered and unfetchable.
+        // Keyword matching on the URL path still applies via CheckUrlForBlockedContentUseCase.
+        if (SEARCH_ENGINE_DOMAINS.any { domain == it || domain.endsWith(".$it") }) {
+            return SmartScanResult.Clean
+        }
 
         // Check in-memory cache first
```

**Verify:**
- `google.com/search?q=test` → `SmartScanResult.Clean` immediately (no HTTP fetch)
- `google.com/search?q=casino` → still caught by `CheckUrlForBlockedContentUseCase` keyword match on path
- `casino-win.com` → SmartScan runs normally, keyword match blocks it

**Risk:** Minimal. Only affects SmartScan path. Keyword/domain sync checks are unaffected.

---

## Fix 5 — NextDNS Routing (VPN Layer)

**Root cause recap:** NextDNS receives 0 logs because domains that were auto-blocked locally never reach the VPN DoH forward. With Fix 1 + Fix 3, the cascade is broken — domains no longer get stuck in `TEMPORARY` auto-block. This naturally restores DNS forwarding to NextDNS.

**No code change needed** — Fixes 1 and 3 are sufficient to unblock the NextDNS path. But verify after implementing:

**Verification steps:**
1. Clear the app data (clears auto-blocked domain cache)
2. Start a session with VPN active
3. Visit `google.com/search?q=test`
4. Check NextDNS dashboard → should see `google.com` DNS query
5. Check Timber logs → `SVC_VPN_PACKET: DNS query for google.com resolved via DoH`

If NextDNS still shows 0 logs after clearing data, the VPN TUN interface itself may not be routing correctly — investigate `FocusVpnService.startVpn()` builder routes.

---

## Implementation Order (PAUL: Apply Phase)

Execute strictly in this order to avoid breaking the system mid-fix:

```
1. Fix 3 — BlockedDomainDao (no logic change, just SQL)
   → Verify: getAllBlockedDomainList() excludes TEMPORARY
   
2. Fix 1 — SmartScanUseCase fail-open
   → Verify: timeout/empty returns FetchFailed, not Blocked
   
3. Fix 4 — Search engine safelist in SmartScanUseCase
   → Verify: google.com returns Clean immediately
   
4. Fix 2 — MetaDescriptionFetcher (timeout + SSRF + threshold)
   → Verify: real content pages fetch correctly
   → Verify: private IP still blocked
```

---

## Unit Test Coverage Required

| Test | File | What to assert |
|------|------|---------------|
| `FetchTimeout → FetchFailed (not Blocked)` | SmartScanUseCaseTest | `result is SmartScanResult.FetchFailed` |
| `FetchEmpty → FetchFailed (not Blocked)` | SmartScanUseCaseTest | `result is SmartScanResult.FetchFailed` |
| `google.com → Clean (safelist)` | SmartScanUseCaseTest | `result is SmartScanResult.Clean`, fetcher not called |
| `TEMPORARY not in blocklist` | BlockedDomainDaoTest | DAO query excludes TEMPORARY entries |
| `Private IP still blocked` | MetaDescriptionFetcherTest | throws / returns FetchError for 192.168.x.x |
| `Valid content page still scanned` | MetaDescriptionFetcherTest | returns FetchOutcome.Success with signals |

---

## Risk Matrix

| Fix | Risk | Mitigation |
|-----|------|-----------|
| Fix 1 (fail-open) | Low — FetchFailed already handled as allow | No mitigation needed |
| Fix 2a (timeout 8s) | Low — more battery on mobile | Timeout still prevents indefinite hang |
| Fix 2b (remove IP-pin) | Medium — SSRF surface | Private-IP check at line 62 remains intact |
| Fix 2c (threshold 200) | Low — cosmetic | Pages with valid `<head>` always > 200 chars |
| Fix 3 (DAO query) | Low — PERMANENT entries unaffected | Verify with unit test |
| Fix 4 (safelist) | Low — additive bypass | URL keyword check still runs |

---

## Unresolved Questions (Confirm Before Implementing)

> [!IMPORTANT]
> Confirm these before starting implementation:

1. **Fix 2b (SSRF):** Acceptable to remove IP-pinning and rely solely on pre-request private-IP check? Or do you want to keep IP-pinning for non-SNI hosts only?
2. **Fix 4 (Safelist):** Should search engines be completely skipped (current proposal) or should DoH check still run for them? (DoH can still detect NextDNS-blocked categories on search engines)
3. **Scope:** Should this be a single commit or one commit per fix for easier rollback?
