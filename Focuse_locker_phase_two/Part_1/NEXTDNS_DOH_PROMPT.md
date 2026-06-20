# NEXTDNS_DOH_PROMPT.md
# Feature: Dynamic Category Check via NextDNS DoH
# App: FocusLock (Android)
# Language: Kotlin
# Total Phases: 2

---

## CONTEXT

FocusLock needs dynamic real-time detection for domains not found in the local
Room DB (`BlockedDomainDao`) or StevenBlack offline lists. This module queries
NextDNS via DNS-over-HTTPS (DoH) — a free service with 30+ categories (adult,
gambling, social media, gaming, dating, streaming, and more). The query is a
single lightweight HTTP call (~1KB) that returns within 200ms on a normal connection.

The result is cached permanently in Room DB via the existing
`BlockedDomainDao.upsertDomain()` so the same domain is never queried twice.

NextDNS free tier: 300,000 queries/month — sufficient for a single-user app.

### Existing Codebase (DO NOT RECREATE)

| Component | Class / File |
|---|---|
| Domain entity | `BlockedDomainEntity` (table: `blocked_domains`) |
| DAO | `BlockedDomainDao` — has `upsertDomain()`, `findActiveDomain()`, `deleteExpiredEntries()` |
| Repository | `BlockingRepositoryImpl` — has `getCachedDomainStatus()`, `addAutoBlockedDomain()` |
| Domain extractor | `SmartScanUseCase.extractDomain()` — strips `www.`, handles parse errors |
| OkHttp | Already a project dependency (version 4.12.0) |
| Settings storage | App uses SharedPreferences (NOT DataStore). Store NextDNS profile ID there. |

---

---

## PHASE 1 — NextDNS Profile Setup and Query Function [TODO]

### GOAL
Build the DoH query function that asks NextDNS whether a domain is blocked
under the user's configured category policy.

### WHAT TO BUILD

**Part A — NextDNS profile configuration:**
The user must create a free NextDNS account at `nextdns.io` and configure which
categories to block in their profile. Each profile has a unique ID (e.g.
`abc123`). Store this profile ID in SharedPreferences so the user can
enter it in the FocusLock settings screen.

Create a `DoHSettingsRepository` or add methods to existing `BlockingRepository`:
- `saveNextDnsProfileId(profileId: String)` — saves to SharedPreferences
- `getNextDnsProfileId(): String?` — reads from SharedPreferences

The DoH endpoint for a profile is:
```
https://{profileId}.dns.nextdns.io/dns-query
```

If the user has not set a profile ID, fall back to the AdGuard Family DNS
endpoint which covers adult and gambling for free with no account:
```
https://family.adguard-dns.com/dns-query
```

**Part B — Query function:**
Create a `DoHQueryUseCase` (or standalone class) in `domain/usecase/` with a
suspend function `queryDoH(domain: String): DoHResult` that:

1. Gets the DoH endpoint URL from SharedPreferences (NextDNS profile URL or
   AdGuard fallback).
2. Builds the request URL:
   `{endpoint}?name={domain}&type=A`
3. Sets the request header `Accept: application/dns-json`.
4. Uses OkHttp (already available as a project dependency) with:
   - Connect timeout: 3 seconds
   - Read timeout: 3 seconds
5. Makes the request inside `withContext(Dispatchers.IO)`.
6. Parses the JSON response body using `org.json.JSONObject` (already available
   in Android SDK). The field to check is `"Status"`:
   - `0` → domain resolves normally → `DoHResult.Clean`
   - `3` → NXDOMAIN, domain is blocked by the resolver → `DoHResult.Blocked`
   - Any other value → treat as `DoHResult.Unknown`
7. On any exception (timeout, network error, parse error) → return
   `DoHResult.QueryFailed`

Define `DoHResult` as a sealed class in `domain/model/`:
- `Blocked`
- `Clean`
- `Unknown`
- `QueryFailed`

Never throw from this function — all exceptions return `QueryFailed`.

### ACCEPTANCE CRITERIA
- `queryDoH("xhamster.com")` returns `Blocked` against AdGuard fallback
- `queryDoH("github.com")` returns `Clean`
- Network timeout after 3 seconds returns `QueryFailed` — does not hang
- Function is a `suspend fun` — never called on main thread

---

---

## PHASE 2 — Cache and Wire into Blocking Flow [TODO]

### GOAL
Cache every DoH result permanently in Room DB so the same domain is never
queried twice. Wire the DoH check into the app's main blocking flow between
the Room DB Fast Lane check and the keyword meta scan.

### WHAT TO BUILD

**Part A — Result caching:**
Create a suspend function `checkAndCacheDoH(url: String): DoHResult` in
the `DoHQueryUseCase` that:

1. Extracts the bare domain from the URL — reuse
   `SmartScanUseCase.extractDomain()` logic (or factor it into a shared utility).
   Strip `www.`, `m.`, `mobile.` prefixes, remove scheme and path.
2. Checks Room DB via `BlockingRepository.getCachedDomainStatus(domain)` first:
   - If `"PERMANENT"` → return `DoHResult.Blocked` immediately, skip network call
   - If `"CLEAN_CACHE"` (and not expired) → return `DoHResult.Clean`, skip network call
3. If not cached, call `queryDoH(domain)`.
4. Based on result:
   - `Blocked` → call `BlockingRepository.addAutoBlockedDomain(domain, "", "PERMANENT", null)`
   - `Clean` → call `BlockingRepository.addAutoBlockedDomain(domain, "", "CLEAN_CACHE", now + 24h)`
   - `Unknown` or `QueryFailed` → do not cache, return as-is
5. Return the `DoHResult`.

**Part B — Wire into the main blocking flow:**
Edit `FocusAccessibilityService.handleBrowserEvent()`. The current flow is:

```
1. CheckUrlForBlockedContentUseCase (URL pre-check)     → block if hit
2. SmartScanUseCase (Room DB cache + meta fetch scan)    → block if hit
```

Insert the DoH check between step 1 and step 2. The updated flow should be:

```
1. CheckUrlForBlockedContentUseCase (URL pre-check)      → block if hit
2. DoHQueryUseCase.checkAndCacheDoH(url)                  → block if Blocked, stop if Clean
3. SmartScanUseCase (Room DB cache + meta fetch scan)     → block if hit (last resort)
```

In `handleBrowserEvent()`, after the URL pre-check passes, launch a coroutine:
```kotlin
pendingSmartScanJob?.cancel()
pendingSmartScanJob = serviceScope.launch {
    // Step 2: DoH check
    val dohResult = dohQueryUseCase.checkAndCacheDoH(url)
    if (dohResult is DoHResult.Blocked) {
        withContext(Dispatchers.Main) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            launchOverlayService("category_block", "NEXTDNS_DOH")
        }
        return@launch
    }
    if (dohResult is DoHResult.Clean) {
        return@launch  // confirmed clean, skip meta scan
    }
    // Step 3: Fall through to meta scan (Unknown / QueryFailed)
    val result = smartScanUseCase(url, activeKeywords.get())
    // ... existing SmartScanResult handling ...
}
```

Inject `DoHQueryUseCase` via `BlockingServiceEntryPoint` — add it to the
Hilt entry point interface in `di/BlockingServiceEntryPoint.kt`.

### ACCEPTANCE CRITERIA
- A domain blocked by DoH is never queried again after first result — Room DB
  cache is used on all subsequent visits
- A `QueryFailed` result does not cache and does not block — falls through to
  meta scan
- The full flow from URL capture to block screen for a known bad domain
  (already in Room DB) takes under 50ms
- The full flow for an unknown domain hitting DoH takes under 500ms on a
  normal mobile connection
- DoH is skipped entirely if `checkAndCacheDoH` finds the domain already
  cached in Room DB

---

_End of NEXTDNS_DOH_PROMPT.md_
