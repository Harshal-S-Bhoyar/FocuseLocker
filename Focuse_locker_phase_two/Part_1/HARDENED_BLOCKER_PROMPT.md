# HARDENED_BLOCKER_PROMPT.md
# Feature: Hardened Content Blocking — Smart Scan Reinforcement
# App: FocusLock (Android)
# Total Phases: 5 (Phases 1–3 ALREADY DONE, Phase 5 MOSTLY DONE)

---

## CONTEXT (Read before every phase)

This prompt file is for hardening the existing "Smart Scan" content blocking feature
of the FocusLock Android app. The feature monitors URLs opened in browsers using
an Accessibility Service (`FocusAccessibilityService`), checks them against a local
Room database blocklist (`BlockedDomainDao`), and if not found, runs a background
deep scan of the webpage to detect forbidden keywords in page meta signals.

### Existing Codebase (DO NOT RECREATE)

| Component | Class / File | Status |
|---|---|---|
| URL Pre-Check | `CheckUrlForBlockedContentUseCase` | ✅ Done (Phase 1) |
| 7-Signal Extraction | `MetaDescriptionFetcher.extractSignals()` — extracts meta desc, keywords, og:title, og:desc, twitter:desc, title, h1 + body_text | ✅ Done (Phase 2) |
| Normalized Matching | `NormalizedMatcher` — NFD + leet (0→o, 4→a, 5→s, @→a, $→s) + word boundary for ≤4 chars | ✅ Done (Phase 3) |
| Retry Fetch | `MetaDescriptionFetcher` — 2 attempts, 4s `withTimeout`, 500ms delay | ✅ Partially Done (Phase 4) |
| Domain Cache | `SmartScanUseCase` — PERMANENT/CLEAN_CACHE with expiresAt via `BlockedDomainDao` | ✅ Mostly Done (Phase 5) |
| Fetch Outcome Model | `FetchOutcome` sealed class — `Success(signals)`, `Failed(reason)` | ✅ Done |
| Scan Result Model | `SmartScanResult` sealed class — `Blocked(keyword, signal)`, `Clean`, `FetchFailed(reason)` | ✅ Done |
| SSRF Protection | `MetaDescriptionFetcher.isPrivateHost()` | ✅ Done |

---

---

## PHASE 1 — URL Pre-Check Before Any Fetch [ALREADY DONE — SKIP]

**Implemented in**: `CheckUrlForBlockedContentUseCase.kt`
- Extracts host+path, runs `NormalizedMatcher.matches()` for each keyword
- Called in `FocusAccessibilityService.handleBrowserEvent()` BEFORE SmartScan

No action needed.

---

---

## PHASE 2 — Expand the Page Signal Surface [ALREADY DONE — SKIP]

**Implemented in**: `MetaDescriptionFetcher.extractSignals()`
- Extracts 7 signals + body_text (exceeds original plan)
- Uses Jsoup, handles missing tags gracefully

No action needed.

---

---

## PHASE 3 — Normalized Keyword Matching [ALREADY DONE — SKIP]

**Implemented in**: `NormalizedMatcher.kt`
- NFD Unicode normalization
- Leet-speak: `0→o, 4→a, 5→s, @→a, $→s`
- Note: `1→i` and `3→e` intentionally excluded (documented reason: false positives
  on `mp3`, `win32`, etc.)
- Word boundary `\b` for keywords ≤4 chars, substring for ≥5 chars

No action needed.

---

---

## PHASE 4 — Fail-Closed Fetch with Retry [TODO — PARTIAL]

### GOAL
The retry mechanism is done but the fail-closed policy is incomplete. Currently
`MetaDescriptionFetcher` returns `FetchOutcome.Failed(reason)` for ALL failure
types, and `SmartScanUseCase` treats ALL failures the same — allowing the page
through. The fix is to distinguish timeout/empty failures from network errors
and apply different policies.

### WHAT TO BUILD

**Part A — Expand `FetchOutcome` sealed class:**
Edit `domain/model/FetchOutcome.kt`. Replace the current two variants with:

```kotlin
sealed class FetchOutcome {
    data class Success(val signals: Map<String, String>) : FetchOutcome()
    object FetchTimeout : FetchOutcome()
    object FetchEmpty : FetchOutcome()
    data class FetchError(val reason: String) : FetchOutcome()
}
```

**Part B — Update `MetaDescriptionFetcher` to classify failures:**
In `MetaDescriptionFetcher.fetchPageSignals()`:

1. After both retry attempts fail, check the last exception type:
   - `kotlinx.coroutines.TimeoutCancellationException` → return `FetchOutcome.FetchTimeout`
   - All other exceptions → return `FetchOutcome.FetchError(message)`
2. After a successful fetch, check body length:
   - If the total HTML is blank or under 50 characters → return `FetchOutcome.FetchEmpty`
   - Otherwise proceed to `extractSignals()` and return `FetchOutcome.Success`

**Part C — Update `SmartScanUseCase` to enforce fail-closed policy:**
In `SmartScanUseCase.invoke()`, update the `when(outcome)` block:

```kotlin
when (outcome) {
    is FetchOutcome.Success -> { /* existing keyword match logic — no change */ }
    is FetchOutcome.FetchTimeout, is FetchOutcome.FetchEmpty -> {
        // FAIL-CLOSED: Temporarily block for 1 hour
        val expiresAt = System.currentTimeMillis() + 60 * 60 * 1000L
        blockingRepository.addAutoBlockedDomain(domain, "", "TEMPORARY", expiresAt)
        val result = SmartScanResult.Blocked("timeout", "fail_closed")
        memoryCache.put(domain, result)
        return result
    }
    is FetchOutcome.FetchError -> {
        // Network error — allow, do NOT cache (will re-scan next visit)
        return SmartScanResult.FetchFailed(outcome.reason)
    }
}
```

**Part D — Update `FocusAccessibilityService` to handle temporary blocks:**
No change needed — `SmartScanResult.Blocked` already triggers
`performGlobalAction(BACK) + launchOverlayService()`.

### ACCEPTANCE CRITERIA
- A fetch that hangs for 5 seconds → user sees block screen within ~8.5 seconds
  (4s attempt 1 + 0.5s wait + 4s attempt 2)
- A fetch that throws an SSL exception → page is allowed but not cached
- A fetch returning an empty body → domain is temporarily blocked for 1 hour
- A fetch returning a page with a matching keyword → domain is permanently blocked
- No scenario results in the scan function completing without returning one of
  the four outcome types
- `SmartScanResult.FetchFailed` is only used for `FetchError`, never for
  timeout/empty (those return `SmartScanResult.Blocked`)

---

---

## PHASE 5 — Domain-Level Blocking and Cache [ALREADY DONE — MINOR TODO]

### ALREADY DONE
- `BlockedDomainEntity` has `block_status` (PERMANENT/TEMPORARY/CLEAN_CACHE) + `expires_at`
- `BlockedDomainDao.findActiveDomain()` checks expiry
- `BlockedDomainDao.deleteExpiredEntries()` cleans up expired entries
- `SmartScanUseCase` calls `getCachedDomainStatus()` before fetch
- Domain extraction via `extractDomain()` strips `www.`

### MINOR TODO — Add `source` column (LOW PRIORITY)

If you want audit tracking, add a `source` column to `BlockedDomainEntity`:

```kotlin
@ColumnInfo(name = "source", defaultValue = "'UNKNOWN'")
val source: String = "UNKNOWN"  // STEVENBLACK, NEXTDNS, META_SCAN, URL_PRECHECK, MANUAL
```

This requires a Room migration from version 6 → 7 in `FocusDatabase.kt`.
Add as `AutoMigration(from = 6, to = 7)` — Room handles simple column additions
automatically when `defaultValue` is set.

Then update `BlockingRepositoryImpl.addAutoBlockedDomain()` to accept and pass
the `source` parameter.

**This is optional and LOW PRIORITY.** The system works without it.

---

## PHASE EXECUTION ORDER (UPDATED)

| Phase | What it fixes | Status |
|---|---|---|
| 1 | URL contains keyword — no fetch needed | ✅ DONE |
| 2 | Meta description is empty or lying | ✅ DONE |
| 3 | Obfuscated / accented keywords bypass match | ✅ DONE |
| 4 | Fetch failure silently lets page through | ⚠️ TODO |
| 5 | Same domain re-scanned on every new path | ✅ DONE (source column optional) |

**Only Phase 4 requires implementation work.** Phase 5 source column is optional.

---

_End of HARDENED_BLOCKER_PROMPT.md_
