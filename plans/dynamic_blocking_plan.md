# 🧠 FocusLocker — Dynamic Smart Blocking Feature
## Full Implementation Plan

> **Feature Name:** Smart Keyword Scan (Dynamic Domain Blocking)
> **Goal:** When a user navigates to a URL during an active session, the app silently fetches that page's `meta description` tag. If the description contains any of the user's blocked keywords, the domain is blocked immediately **and** auto-saved to the Room DB (and optionally `blocked_domains.json`) for future reference.

---

## ✅ Feasibility Review

### Is This Possible?

**YES — 100% implementable.** Here's why:

| Question | Answer |
|---|---|
| Can the app intercept the current URL? | ✅ Already done — `FocusAccessibilityService` reads the browser URL bar in real-time |
| Can we fetch meta description from a URL? | ✅ Yes — via a simple HTTP GET + HTML parse (no browser needed) |
| Can we compare with user keywords? | ✅ Already done — `BlockedKeywordEntity` / `BlockedKeywordDao` exists |
| Can we save new domains to the DB? | ✅ `BlockedDomainDao.insertDomains()` exists |
| Can we avoid breaking existing blocking? | ✅ Yes — this is purely additive; existing flow is unchanged |
| Any Android permission issues? | ⚠️ Needs `INTERNET` (already present) + no additional permissions |
| Does it require a backend? | ❌ No — purely on-device; no server needed |

### Key Technical Insight

> The "snippet" you see in Google/Bing search results (e.g. *"Every business today faces the same paradox..."*) is the **`<meta name="description" content="...">` tag** in the page's HTML `<head>`. This tag is returned in the very first HTTP response — we do **NOT** need to run JavaScript or render the page. A simple `GET` request + HTML text parsing is enough.

---

## 🏗️ Architecture Overview

```
User Navigates to URL
       │
       ▼
FocusAccessibilityService (already intercepts URL)
       │
       ▼
CheckUrlForBlockedContentUseCase (existing — check domain + keywords)
       │
       ├── BLOCKED → show overlay (existing)
       │
       └── NOT BLOCKED → [NEW] SmartScanUseCase
                              │
                              ▼
                    MetaDescriptionFetcher (NEW — fetches HTML HEAD)
                              │
                              ▼
                    KeywordMatcher (NEW — compare description vs. keywords)
                              │
                      ┌───────┴────────┐
                      │ Match Found    │ No Match
                      ▼                ▼
             Auto-block domain    Do nothing
             Save to Room DB
             (BlockedDomainDao)
             Trigger overlay
```

---

## 📋 Implementation Phases

---

### Phase 1 — Data Layer: Add `auto_blocked` flag to `BlockedDomainEntity`

**Why:** We need to distinguish auto-discovered domains from the static seed so users can review and optionally remove them.

**Files to Modify:**

#### 1.1 — `BlockedDomainEntity.kt`

**Current entity** (inferred from `BlockingRepositoryImpl`):
```kotlin
@Entity(tableName = "blocked_domains")
data class BlockedDomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val categoryKey: String
)
```

**New entity** — add two fields:
```kotlin
@Entity(tableName = "blocked_domains")
data class BlockedDomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val categoryKey: String,

    // NEW: true = auto-blocked by Smart Scan, false = from static JSON seed
    val isAutoBlocked: Boolean = false,

    // NEW: the matched keyword that triggered this block (for user review UI)
    val matchedKeyword: String? = null,

    // NEW: timestamp when auto-block was added
    val autoBlockedAt: Long? = null
)
```

#### 1.2 — `FocusDatabase.kt` — Bump version + Migration

```kotlin
// Bump version from current (e.g., 4) to 5
@Database(
    entities = [..., BlockedDomainEntity::class],
    version = 5,  // ← bump version
    autoMigrations = [
        AutoMigration(from = 4, to = 5)  // Room handles adding nullable columns automatically
    ]
)
```

> [!NOTE]
> Because we're only **adding** new nullable columns with defaults, Room's `AutoMigration` handles this with zero manual SQL. No migration class needed.

#### 1.3 — `BlockedDomainDao.kt` — Add new query

```kotlin
// NEW: Insert a single auto-discovered domain
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertDomain(domain: BlockedDomainEntity)

// NEW: Check if a domain was already auto-blocked (avoid duplicates)
@Query("SELECT COUNT(*) FROM blocked_domains WHERE domain = :domain")
suspend fun countByDomain(domain: String): Int

// NEW: Get all auto-blocked domains for the "Smart Blocked" review screen
@Query("SELECT * FROM blocked_domains WHERE isAutoBlocked = 1 ORDER BY autoBlockedAt DESC")
fun getAutoBlockedDomains(): Flow<List<BlockedDomainEntity>>
```

---

### Phase 2 — Domain Layer: New Use Case + Fetcher

**Files to Create (NEW):**

#### 2.1 — `MetaDescriptionFetcher.kt`
📁 Path: `app/src/main/java/com/focuslock/domain/util/MetaDescriptionFetcher.kt`

```kotlin
package com.focuslock.domain.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches only the HTML <head> section of a URL and extracts the
 * meta description tag content. This is what appears as the "snippet"
 * under a website title in Google/Bing search results.
 *
 * Example:
 *   URL: helium.ai
 *   Returns: "Every business today faces the same paradox. The world
 *             is flooded with hundreds of AI tools..."
 *
 * Why only <head>? We stop reading HTML after </head> to avoid
 * downloading the entire page body, keeping it fast (<200ms).
 */
@Singleton
class MetaDescriptionFetcher @Inject constructor() {

    companion object {
        // Timeout constants — keep it fast to not block the accessibility service
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 3000

        // Regex to extract meta description content
        // Handles both: <meta name="description" content="...">
        //           and: <meta property="og:description" content="...">
        private val META_DESC_REGEX = Regex(
            """<meta[^>]+(?:name=["']description["']|property=["']og:description["'])[^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val META_DESC_REGEX_ALT = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:name=["']description["']|property=["']og:description["'])""",
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * Returns the meta description text for a URL, or null if:
     * - The network request fails
     * - The page has no meta description
     * - The request times out
     *
     * This runs on Dispatchers.IO — always call from a coroutine.
     */
    suspend fun fetchDescription(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection().apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "Mozilla/5.0") // polite UA
            }

            // Read only the first 8KB — enough to capture <head> without full page
            val headHtml = connection.getInputStream().bufferedReader().use { reader ->
                val sb = StringBuilder()
                val buffer = CharArray(1024)
                var bytesRead = 0
                while (bytesRead < 8192) {
                    val charsRead = reader.read(buffer)
                    if (charsRead == -1) break
                    sb.append(buffer, 0, charsRead)
                    bytesRead += charsRead
                    // Stop early if we've passed </head> — no need to read body
                    if (sb.contains("</head>", ignoreCase = true)) break
                }
                sb.toString()
            }

            // Try standard meta description first, then og:description
            META_DESC_REGEX.find(headHtml)?.groupValues?.get(1)?.trim()
                ?: META_DESC_REGEX_ALT.find(headHtml)?.groupValues?.get(1)?.trim()

        } catch (e: Exception) {
            // Network errors, timeouts, SSL issues — silently return null
            // The existing domain/keyword check already ran; this is best-effort only
            Timber.d("MetaDescriptionFetcher: Could not fetch $url — ${e.message}")
            null
        }
    }
}
```

#### 2.2 — `SmartScanUseCase.kt`
📁 Path: `app/src/main/java/com/focuslock/domain/usecase/SmartScanUseCase.kt`

```kotlin
package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
import com.focuslock.domain.util.MetaDescriptionFetcher
import timber.log.Timber
import javax.inject.Inject

/**
 * This Use Case is the "brain" of the Smart Blocking feature.
 *
 * It is called ONLY when the standard URL check did NOT find a block
 * (i.e., the domain is not in the static list AND the URL itself
 * doesn't contain a blocked keyword).
 *
 * What it does:
 * 1. Fetches the meta description of the visited URL
 * 2. Checks if any active user keyword is present in that description
 * 3. If yes → tells the caller to block, and saves the domain to DB
 *
 * Returns: the matched keyword if a block is needed, or null if clean.
 */
class SmartScanUseCase @Inject constructor(
    private val metaDescriptionFetcher: MetaDescriptionFetcher,
    private val blockingRepository: BlockingRepository
) {

    /**
     * @param url          Full URL the user navigated to (e.g., "https://helium.ai")
     * @param activeKeywords The user's current list of blocked keywords (lowercase)
     * @return The keyword that matched (to show in the overlay), or null if no match
     */
    suspend operator fun invoke(url: String, activeKeywords: List<String>): String? {
        if (activeKeywords.isEmpty()) return null // nothing to check against

        // Step 1: Extract the domain from the URL for saving to DB later
        val domain = extractDomain(url) ?: return null

        // Step 2: Fetch the page's meta description (the snippet text)
        val description = metaDescriptionFetcher.fetchDescription(url)
        if (description.isNullOrBlank()) return null

        val descriptionLower = description.lowercase()
        Timber.d("SmartScan: Checking '$domain' — description: ${description.take(100)}...")

        // Step 3: Check if any active keyword appears in the description
        val matchedKeyword = activeKeywords.firstOrNull { keyword ->
            descriptionLower.contains(keyword.lowercase())
        }

        if (matchedKeyword != null) {
            Timber.i("SmartScan: BLOCKED '$domain' — keyword '$matchedKeyword' found in description")

            // Step 4: Auto-save this domain to the DB for future fast blocking
            blockingRepository.addAutoBlockedDomain(
                domain = domain,
                matchedKeyword = matchedKeyword
            )
        }

        return matchedKeyword
    }

    /**
     * Extracts the clean domain from a full URL.
     * Example: "https://www.helium.ai/pricing" → "helium.ai"
     */
    private fun extractDomain(url: String): String? {
        return try {
            val host = java.net.URL(url).host
            // Remove "www." prefix for cleaner storage
            host.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }
}
```

---

### Phase 3 — Repository Layer: Add `addAutoBlockedDomain()`

**File to Modify:** `BlockingRepository.kt` (interface) + `BlockingRepositoryImpl.kt`

#### 3.1 — `BlockingRepository.kt` (Domain Interface)

Add this to the interface:
```kotlin
// NEW: Save a domain that was auto-discovered by Smart Scan
suspend fun addAutoBlockedDomain(domain: String, matchedKeyword: String)

// NEW: Get all domains discovered by Smart Scan (for review screen)
fun getAutoBlockedDomains(): Flow<List<BlockedDomainEntity>>
```

#### 3.2 — `BlockingRepositoryImpl.kt`

Add this implementation:
```kotlin
/**
 * Saves a domain that was discovered by the Smart Scan feature.
 * Uses IGNORE conflict strategy — safe to call even if domain already exists.
 */
override suspend fun addAutoBlockedDomain(domain: String, matchedKeyword: String) {
    // Check if this domain is already in the DB (avoid duplicates)
    if (domainDao.countByDomain(domain) > 0) {
        Timber.d("SmartScan: '$domain' already in blocked list — skipping save")
        return
    }

    domainDao.insertDomain(
        BlockedDomainEntity(
            domain = domain,
            categoryKey = "AUTO_BLOCKED",     // New category key for smart-blocked domains
            isAutoBlocked = true,
            matchedKeyword = matchedKeyword,
            autoBlockedAt = System.currentTimeMillis()
        )
    )
    Timber.i("SmartScan: Auto-saved '$domain' to blocked domains DB")
}

override fun getAutoBlockedDomains(): Flow<List<BlockedDomainEntity>> {
    return domainDao.getAutoBlockedDomains()
}
```

---

### Phase 4 — Service Layer: Wire into `FocusAccessibilityService`

**File to Modify:** `FocusAccessibilityService.kt`

This is the most critical change. We call `SmartScanUseCase` only when the existing check returns `NotBlocked`.

```kotlin
// Add this field alongside existing use cases
private lateinit var smartScanUseCase: SmartScanUseCase

// In onServiceConnected() — add injection
smartScanUseCase = entryPoint.smartScanUseCase()

// MODIFIED handleBrowserEvent() — add smart scan after existing check
private fun handleBrowserEvent(event: AccessibilityEvent, packageName: String) {
    val source = event.source ?: return
    val urlNode = findUrlNode(source, packageName)
    val url = urlNode?.text?.toString()
    urlNode?.recycle()
    source.recycle()

    if (!url.isNullOrBlank()) {
        if (isDuplicateUrlEvent(url)) return

        // Step 1: Existing fast check (domain list + URL keyword match)
        val result = checkUrlForBlockedContentUseCase(url, activeKeywords.get(), activeDomains.get())

        if (result is BlockCheckResult.Blocked) {
            // Existing block — show overlay immediately
            performGlobalAction(GLOBAL_ACTION_BACK)
            launchOverlayService(result.matchedTerm, result.reason.name)
        } else {
            // Step 2 (NEW): Smart Scan — check meta description asynchronously
            // This runs on IO thread and does NOT freeze the accessibility callback
            serviceScope.launch {
                val matchedKeyword = smartScanUseCase(url, activeKeywords.get())
                if (matchedKeyword != null) {
                    // Run UI actions on Main thread
                    withContext(Dispatchers.Main) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        launchOverlayService(
                            matchedTerm = matchedKeyword,
                            blockReason = "SMART_SCAN_KEYWORD_MATCH"
                        )
                    }
                } else {
                    stopService(Intent(this@FocusAccessibilityService, BlockingOverlayService::class.java))
                }
            }
        }
    }
}
```

#### Update `BlockingServiceEntryPoint.kt`

```kotlin
// Add this to the Hilt entry point interface
fun smartScanUseCase(): SmartScanUseCase
```

---

### Phase 5 — UI Layer: Smart Blocked Domains Review Screen (Optional but Recommended)

**Why important:** Users should be able to see what the app auto-blocked and remove false positives.

#### 5.1 — `SmartBlockedDomainsScreen.kt`
📁 Path: `app/src/main/java/com/focuslock/ui/smartblocked/SmartBlockedDomainsScreen.kt`

```kotlin
/**
 * A simple list screen showing all domains auto-blocked by Smart Scan.
 * User can:
 *   - See the domain + the keyword that triggered the block
 *   - Delete a domain if it was a false positive
 */
@Composable
fun SmartBlockedDomainsScreen(viewModel: SmartBlockedDomainsViewModel = hiltViewModel()) {
    val domains by viewModel.autoBlockedDomains.collectAsStateWithLifecycle()

    LazyColumn {
        items(domains) { domain ->
            SmartBlockedDomainCard(
                domain = domain.domain,
                matchedKeyword = domain.matchedKeyword ?: "Unknown",
                blockedAt = domain.autoBlockedAt,
                onDelete = { viewModel.removeDomain(domain.id) }
            )
        }
    }
}
```

---

## 📂 Summary of All Files to Create / Modify

| Action | File | What Changes |
|--------|------|-------------|
| 🆕 Create | `domain/util/MetaDescriptionFetcher.kt` | HTTP fetch + HTML parse for meta description |
| 🆕 Create | `domain/usecase/SmartScanUseCase.kt` | Orchestrates fetch → match → save logic |
| 🆕 Create | `ui/smartblocked/SmartBlockedDomainsScreen.kt` | Review screen for auto-blocked domains |
| 🆕 Create | `ui/smartblocked/SmartBlockedDomainsViewModel.kt` | ViewModel for the review screen |
| ✏️ Modify | `data/local/entity/BlockedDomainEntity.kt` | Add `isAutoBlocked`, `matchedKeyword`, `autoBlockedAt` fields |
| ✏️ Modify | `data/local/dao/BlockedDomainDao.kt` | Add `insertDomain()`, `countByDomain()`, `getAutoBlockedDomains()` |
| ✏️ Modify | `data/local/FocusDatabase.kt` | Bump version + AutoMigration |
| ✏️ Modify | `domain/repository/BlockingRepository.kt` | Add `addAutoBlockedDomain()` to interface |
| ✏️ Modify | `data/repository/BlockingRepositoryImpl.kt` | Implement `addAutoBlockedDomain()` |
| ✏️ Modify | `service/accessibility/FocusAccessibilityService.kt` | Call `SmartScanUseCase` after existing check |
| ✏️ Modify | `di/BlockingServiceEntryPoint.kt` | Expose `SmartScanUseCase` to the service |

---

## ⚡ Data Flow (Step by Step)

```
1. User opens Chrome during a FocusLock session
2. User navigates to "helium.ai"
3. FocusAccessibilityService reads URL from chrome address bar
4. CheckUrlForBlockedContentUseCase runs:
   - Is "helium.ai" in activeDomains list? → NO
   - Does the URL itself contain a keyword? → NO
   → Result: NotBlocked
5. SmartScanUseCase is called (async, non-blocking):
   a. GET "https://helium.ai" → read first 8KB of HTML
   b. Extract: <meta name="description" content="Every business today...AI tools...">
   c. Compare with activeKeywords = ["ai", "artificial intelligence"]
   d. "ai" found in "Every business today faces the same paradox...AI tools..."
   → MATCH FOUND: "ai"
6. Action:
   a. performGlobalAction(GLOBAL_ACTION_BACK) → browser goes back
   b. BlockingOverlayService shows: "Blocked by Smart Scan — keyword: ai"
   c. BlockedDomainEntity saved to Room DB:
      { domain: "helium.ai", category: "AUTO_BLOCKED", isAutoBlocked: true, matchedKeyword: "ai" }
7. Next time user visits "helium.ai":
   → Step 4 finds it in activeDomains → blocks INSTANTLY (no HTTP fetch needed)
```

---

## 🛡️ Edge Cases & How We Handle Them

| Edge Case | How Handled |
|-----------|-------------|
| URL fetch fails (timeout, no internet) | `MetaDescriptionFetcher` returns `null` → no block → no crash |
| Page has no meta description | Returns `null` → no block |
| Same domain visited multiple times | `isDuplicateUrlEvent()` (existing) + `countByDomain()` prevents double-save |
| Very slow page load | 3-second timeout hard-coded → fetch abandoned, no block |
| HTTPS pages with SSL issues | Silently caught in try-catch, returns null |
| Keywords too generic (e.g., "the") | User's responsibility — warn in UI if keyword < 3 chars |
| Private/intranet URLs | Will fail to fetch → no action (safe) |
| User adds a domain already auto-blocked | `OnConflictStrategy.IGNORE` prevents duplicate DB entry |
| Smart Scan blocks a legit site | User can go to Smart Blocked Domains screen and delete it |

---

## 🧪 Testing Strategy

### Unit Tests (New)

| Test Class | What to Test |
|-----------|-------------|
| `MetaDescriptionFetcherTest` | Mock HTTP responses, verify regex extraction, test malformed HTML |
| `SmartScanUseCaseTest` | Mock fetcher + mock repo, verify: match triggers save, no match = null returned |
| `BlockingRepositoryImplTest` | Verify `addAutoBlockedDomain` inserts correctly, duplicate skipped |

### Integration Test

```kotlin
// Simulate full flow:
// 1. Set activeKeywords = ["gaming"]
// 2. Mock fetcher to return "Best gaming sites..."  
// 3. Call SmartScanUseCase with a fake URL
// 4. Verify domain saved in DB + usecase returns "gaming"
```

---

## ✅ Pre-Implementation Checklist

- [ ] Read `BlockedDomainEntity.kt` actual current code to confirm field names
- [ ] Read `BlockedDomainDao.kt` to confirm existing method signatures
- [ ] Read `FocusDatabase.kt` to confirm current version number
- [ ] Read `BlockingServiceEntryPoint.kt` to confirm how to add new injection
- [ ] Confirm `INTERNET` permission exists in `AndroidManifest.xml`
- [ ] Confirm `CheckUrlForBlockedContentUseCase` return type is `BlockCheckResult`
- [ ] Check if `BlockCheckResult.kt` domain model exists and its structure

---

## 🚀 Recommended Execution Order

```
Phase 1 → Phase 3 → Phase 2 → Phase 4 → Phase 5 → Tests
  (DB)  → (Repo) → (Logic) → (Wire)  →  (UI)   → (Verify)
```

> [!IMPORTANT]
> Always run `./gradlew testDebugUnitTest` after each phase before proceeding to the next. The DB migration is the highest-risk step — test it on a clean install AND an upgrade from the existing version.

> [!WARNING]
> The Smart Scan runs asynchronously but the `performGlobalAction(GLOBAL_ACTION_BACK)` must run on the **Main thread**. Always use `withContext(Dispatchers.Main)` before triggering the overlay or the back action. Failure to do this will cause a `CalledFromWrongThreadException` crash.
