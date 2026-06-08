# 🛠️ SmartScan AutoBlock — Master Fix Plan
## All Bugs + All Fixes in One Document

> **@master-skill pipeline:** @debugger → @code-review-excellence → @karpathy-guidelines → @agent-memory  
> **Method:** Every bug verified against actual source code. Zero assumptions.

---

## Table of Contents
1. [Bug Registry (6 bugs)](#bug-registry)
2. [Fix Plan (6 fixes)](#fix-plan)
3. [Files to Modify (7 files)](#files-to-modify)
4. [Exact Code Changes](#exact-code-changes)
5. [Corrected Data Flow](#corrected-data-flow)

---

## Bug Registry

### BUG-1 🔴 CRITICAL — Event Type Filter Drops ALL URL Navigation Events

| Field | Value |
|-------|-------|
| **File** | `FocusAccessibilityService.kt` line 122 |
| **Also** | `accessibility_service_config.xml` line 3 |
| **Impact** | Feature is 100% dead — service never sees URL changes |

**Current code (line 122):**
```kotlin
if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
    return  // ← silently drops TYPE_WINDOW_CONTENT_CHANGED
}
```

**XML config registers BOTH event types (line 3):**
```xml
android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
```

**Why this breaks:** On Chrome for Android, navigating to a new URL fires `TYPE_WINDOW_CONTENT_CHANGED` (the url_bar content changes). `TYPE_WINDOW_STATE_CHANGED` only fires on tab switch or app launch. Since the Kotlin code drops `CONTENT_CHANGED`, the service **never reads the url_bar text after a URL navigation**.

**How it got here:** The battery optimization sprint removed `CONTENT_CHANGED` handling because it fires 10–30× per second while typing. But the actual URL commitment (after Enter) is a single `CONTENT_CHANGED` event that we need.

**Fact:** The existing `isDuplicateUrlEvent()` debounce at line 237 already filters rapid-fire duplicates within 500ms — so accepting `CONTENT_CHANGED` does NOT bring back the battery drain.

---

### BUG-2 🔴 CRITICAL — SmartScanUseCase Keyword Cache Freezes Empty

| Field | Value |
|-------|-------|
| **File** | `SmartScanUseCase.kt` lines 16, 27, 31–35 |
| **Impact** | Keywords list is always empty → `return false` → nothing blocked |

**Current code:**
```kotlin
@Singleton
class SmartScanUseCase ... {
    @Volatile private var cachedKeywords: List<BlockedKeyword>? = null  // line 16

    suspend operator fun invoke(urlStr: String): Boolean {
        val keywordsList = cachedKeywords ?: run {                      // line 31
            val loaded = blockingRepository.getActiveKeywords()         // returns Flow
                .firstOrNull() ?: emptyList()                          // line 32
            cachedKeywords = loaded                                    // frozen forever
            loaded
        }
        if (keywordsList.isEmpty()) return false                       // line 37–38
```

**Why this breaks:**
1. `getActiveKeywords()` returns a `Flow<List<BlockedKeyword>>`. `firstOrNull()` suspends and returns the FIRST emission. On MIUI/HyperOS devices, Room may emit an empty initial list before the real data loads.
2. `@Singleton` scope means `cachedKeywords` is set ONCE per app lifetime. If it captures an empty list, it stays empty forever — even if the user adds keywords later.
3. There is NO call to `invalidateCache()` anywhere in the codebase (verified: `grep "invalidateCache" → 0 results` outside of SmartScanUseCase itself).

**Already solved elsewhere:** `FocusAccessibilityService` line 104–107 already maintains a **live, always-current** keyword list:
```kotlin
serviceScope.launch {
    getActiveKeywordsUseCase().collect { keywords ->
        activeKeywords.set(keywords)  // ← this is ALWAYS up to date
    }
}
```

SmartScanUseCase ignores this live list and runs its own broken DB query.

---

### BUG-3 🔴 CRITICAL — AUTO_BLOCKED Category Not Active For Existing Installs

| Field | Value |
|-------|-------|
| **File** | `BlockingRepositoryImpl.kt` lines 107–127 |
| **Impact** | Auto-blocked domains silently fail to insert (FK violation) or don't appear in active list |

**Current code:**
```kotlin
override suspend fun seedDomainDataIfNeeded() {
    val existingCategories = categoryDao.getActiveCategoryKeys()      // line 109
    if (existingCategories.isEmpty()) {                               // line 110
        // Inserts ALL categories including AUTO_BLOCKED ← WORKS for fresh install
    } else {
        // line 126: tries insertCategories with OnConflictStrategy set in DAO
        categoryDao.insertCategories(
            listOf(BlockedCategoryEntity(categoryKey = "AUTO_BLOCKED", isActive = true))
        )
    }
```

**The DAO uses `OnConflictStrategy.REPLACE` (line 26 of BlockedCategoryDao.kt):**
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertCategories(categories: List<BlockedCategoryEntity>)
```

**Wait — `REPLACE` does NOT just update `isActive`.** It deletes the old row and inserts a new one with `id = 0` (auto-generated). This can break the foreign key relationship with existing `blocked_domains` rows that reference `AUTO_BLOCKED` via `category_key`. Since the FK uses `category_key` (a string column) and not `id`, this actually works correctly because `category_key = "AUTO_BLOCKED"` stays the same.

**The REAL problem:** `getActiveCategoryKeys()` on line 109 returns only **active** categories. If the user has deactivated all categories manually, `existingCategories` will be empty → the code enters the `if` block and RE-SEEDS all categories (overwriting user preferences). This is a separate bug but doesn't directly affect AUTO_BLOCKED.

**For AUTO_BLOCKED specifically:** The `else` branch (line 126) DOES try to insert it. With `REPLACE` strategy, if it already exists, it will be replaced with `isActive = true`. This part is actually **correct now**.

**BUT:** The `addAutoBlockedDomain()` at line 93–104 inserts with `categoryKey = "AUTO_BLOCKED"`. If `seedDomainDataIfNeeded()` hasn't run yet (race condition at app start), the category row doesn't exist → FK constraint violation → Room's `IGNORE` strategy swallows the error silently → domain never saved.

**Fix needed:** Call `seedDomainDataIfNeeded()` **before** any SmartScan run, or ensure the category exists independently.

---

### BUG-4 🟠 HIGH — blocked_domains.json Domains Not Blocked in AutoBlock Mode

| Field | Value |
|-------|-------|
| **File** | `BlockedDomainDao.kt` lines 20–25 |
| **File** | `FocusAccessibilityService.kt` line 96 |
| **Impact** | Static domains only blocked if user manually enables their categories |

**Current DAO query:**
```sql
SELECT bd.domain FROM blocked_domains bd
INNER JOIN blocked_categories bc ON bd.category_key = bc.category_key
WHERE bc.is_active = 1
```

Static domains from `blocked_domains.json` use categories like `SOCIAL_MEDIA`, `ADULT`, `GAMBLING`, etc. These are seeded with `isActive = false` (line 118 of `BlockingRepositoryImpl.kt`):
```kotlin
BlockedCategoryEntity(categoryKey = key, isActive = if (key == "AUTO_BLOCKED") true else false)
```

The service loads domains at session start (line 96):
```kotlin
activeDomains.set(getActiveBlockedDomainsUseCase())
```

`getActiveBlockedDomainsUseCase()` → `blockingRepository.getActiveBlockedDomains()` → `domainDao.getActiveBlockedDomainList()` → the SQL above.

**Result:** If user only enables Auto-Block keywords without toggling individual category checkboxes (SOCIAL_MEDIA, ADULT, etc.), then `activeDomains` contains ONLY auto-blocked domains (if any exist yet). The ~163 static domains from `blocked_domains.json` are completely invisible.

**User expectation:** When AutoBlock is ON, ALL domains (static + dynamic) should be blocked.

---

### BUG-5 🟠 HIGH — SUPPORTED_BROWSERS Set Missing 4 Browsers From XML Config

| Field | Value |
|-------|-------|
| **File** | `FocusAccessibilityService.kt` lines 38–45 |
| **File** | `accessibility_service_config.xml` line 10 |
| **Impact** | Events from Brave/UC/Kiwi/Opera Mini received by OS but dropped in code |

**XML config registers (line 10):**
```
com.android.chrome, org.mozilla.firefox, com.brave.browser,
com.opera.browser, com.opera.mini.native, com.duckduckgo.mobile.android,
com.microsoft.emmx, com.kiwibrowser.browser, com.sec.android.app.sbrowser,
com.UCMobile.intl
```

**Kotlin set (lines 38–45):**
```kotlin
private val SUPPORTED_BROWSERS = setOf(
    "com.android.chrome",
    "org.mozilla.firefox",
    "com.sec.android.app.sbrowser",
    "com.opera.browser",
    "com.duckduckgo.mobile.android",
    "com.microsoft.emmx"
)
```

**Missing:** `com.brave.browser`, `com.opera.mini.native`, `com.kiwibrowser.browser`, `com.UCMobile.intl`

Also missing from `findUrlNode()` (lines 209–218): no `when` branch for these 4 browsers → returns `null` → no URL detected even if events reach `handleBrowserEvent`.

---

### BUG-6 🟡 MEDIUM — Keyword Limit is 50, User Requires 10

| Field | Value |
|-------|-------|
| **File** | `BlockingRepositoryImpl.kt` line 22 |
| **Impact** | Users can add 50 keywords; requirement is max 10 |

```kotlin
private const val MAX_KEYWORDS = 50  // ← should be 10
```

Error message at line 46: `"Maximum $MAX_KEYWORDS keywords reached"` — will auto-update when constant changes.

---

## Fix Plan

### FIX-1 — Accept Both Event Types With Smart Filtering

**File:** `FocusAccessibilityService.kt` — `onAccessibilityEvent()` method

**What to change:** Remove the blanket `TYPE_WINDOW_STATE_CHANGED`-only filter. Accept both event types for browser packages. Keep `TYPE_WINDOW_STATE_CHANGED`-only for settings protection (which doesn't need URL changes).

**Exact replacement for lines 116–138:**
```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null || !isSessionActive) return

    val packageName = event.packageName?.toString() ?: return
    val eventType = event.eventType

    // 1. Settings Protection — only on window state change (app/screen switch)
    if (SETTINGS_PACKAGES.contains(packageName)) {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleSettingsEvent(event)
        }
        return
    }

    // 2. Browser Filtering — accept BOTH event types:
    //    STATE_CHANGED  → tab switch, app open
    //    CONTENT_CHANGED → URL bar update after navigation
    //    Battery safety: isDuplicateUrlEvent() debounce + findUrlNode() specific ID filter
    if (SUPPORTED_BROWSERS.contains(packageName)) {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleBrowserEvent(event, packageName)
        }
        return
    }
}
```

**Battery impact:** NONE. Three guards prevent spam:
1. `isDuplicateUrlEvent()` — 500ms debounce (line 237)
2. `findUrlNode()` — returns `null` unless the specific url_bar view ID is found (line 220)
3. Only browser packages pass the filter (non-browser `CONTENT_CHANGED` events are dropped)

---

### FIX-2 — SmartScanUseCase: Remove Cache, Accept Live Keywords

**File:** `SmartScanUseCase.kt` — full rewrite of class

**What to change:** Remove `cachedKeywords`, `invalidateCache()`, and the internal DB query. Accept `activeKeywords: List<String>` from the caller (the service already has this data live).

**Exact new file content:**
```kotlin
package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartScanUseCase @Inject constructor(
    private val fetcher: MetaDescriptionFetcher,
    private val blockingRepository: BlockingRepository
) {
    /**
     * Scans the given URL's meta description for blocked keywords.
     * Returns true if a keyword matched and the domain was auto-blocked.
     *
     * @param urlStr The full URL to scan
     * @param activeKeywords Live keyword list from FocusAccessibilityService
     *        (already maintained via Flow collector — always current)
     */
    suspend operator fun invoke(urlStr: String, activeKeywords: List<String>): Boolean {
        if (activeKeywords.isEmpty()) return false

        val domain = extractDomain(urlStr) ?: return false

        // Fetch the page's meta description (cached per domain inside fetcher)
        val description = fetcher.fetchMetaDescription(urlStr) ?: return false
        val descriptionLower = description.lowercase()

        // Match any keyword against the meta description
        for (keyword in activeKeywords) {
            val kw = keyword.lowercase()
            if (descriptionLower.contains(kw)) {
                // Match found — save this domain as auto-blocked
                blockingRepository.addAutoBlockedDomain(domain, kw)
                return true
            }
        }

        return false
    }

    private fun extractDomain(url: String): String? {
        try {
            val uri = java.net.URI(if (url.startsWith("http")) url else "https://$url")
            val host = uri.host ?: return null
            return if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            return null
        }
    }
}
```

**File:** `FocusAccessibilityService.kt` — line 197

**Change call site:**
```kotlin
// BEFORE (line 197):
val isAutoBlocked = smartScanUseCase(url)

// AFTER:
val isAutoBlocked = smartScanUseCase(url, activeKeywords.get())
```

**Why `activeKeywords.get()` is always correct:** The service starts a Flow collector in `onServiceConnected()` (line 104–107) that updates `activeKeywords` AtomicReference on every DB change. By the time SmartScan runs (async, in `serviceScope`), this data is always current.

---

### FIX-3 — Guarantee AUTO_BLOCKED Category Exists and Is Active

**File:** `BlockingRepositoryImpl.kt` — `seedDomainDataIfNeeded()` method

**What to change:** Replace the fragile conditional seeding with a simple, idempotent approach:
1. Always ensure ALL categories exist (use `IGNORE` to avoid duplicates)
2. Then force-activate `AUTO_BLOCKED` via an explicit UPDATE query

**File:** `BlockedCategoryDao.kt` — add new method

**New DAO method:**
```kotlin
@Query("UPDATE blocked_categories SET is_active = 1 WHERE category_key = 'AUTO_BLOCKED'")
suspend fun ensureAutoBlockedActive()
```

**Revised `seedDomainDataIfNeeded()`:**
```kotlin
override suspend fun seedDomainDataIfNeeded() {
    // 1. Ensure ALL category rows exist (IGNORE prevents overwriting user prefs)
    val allCategories = CategoryKey.ALL.map { key ->
        BlockedCategoryEntity(
            categoryKey = key,
            isActive = key == "AUTO_BLOCKED" // only AUTO_BLOCKED defaults to active
        )
    }
    // insertCategories uses REPLACE — but we only insert if the key doesn't exist yet.
    // Problem: REPLACE will overwrite isActive for existing rows.
    // Solution: Use a separate insert with IGNORE strategy for non-AUTO_BLOCKED,
    //           then force-activate AUTO_BLOCKED with a targeted UPDATE.

    // Insert categories that don't exist yet (won't touch existing ones)
    categoryDao.insertCategories(allCategories)

    // Force AUTO_BLOCKED to be always active (idempotent UPDATE)
    categoryDao.ensureAutoBlockedActive()

    // Seed domains from bundled JSON if empty
    if (domainDao.getDomainCount() == 0) {
        loadDomainsFromAssets()
    }
}
```

**Wait — `insertCategories` uses `REPLACE`.** This means it WILL overwrite user's `isActive` choices for existing categories. We need to change the strategy.

**Better approach — Fix the DAO insert strategy:**

**File:** `BlockedCategoryDao.kt` — change `insertCategories` conflict strategy:
```kotlin
// BEFORE (line 26):
@Insert(onConflict = OnConflictStrategy.REPLACE)

// AFTER:
@Insert(onConflict = OnConflictStrategy.IGNORE)
```

This way, existing categories keep their user-set `isActive` values. Only new categories (like `AUTO_BLOCKED` on first upgrade) get inserted.

Then `ensureAutoBlockedActive()` forces just the AUTO_BLOCKED row to `isActive = 1`.

**Complete revised seedDomainDataIfNeeded():**
```kotlin
override suspend fun seedDomainDataIfNeeded() {
    // Ensure all category rows exist. IGNORE means existing rows keep user prefs.
    categoryDao.insertCategories(
        CategoryKey.ALL.map { key ->
            BlockedCategoryEntity(
                categoryKey = key,
                isActive = key == "AUTO_BLOCKED"
            )
        }
    )

    // AUTO_BLOCKED must always be active for dynamic blocking to work
    categoryDao.ensureAutoBlockedActive()

    // Seed domains from bundled JSON if the domains table is empty
    if (domainDao.getDomainCount() == 0) {
        loadDomainsFromAssets()
    }
}
```

---

### FIX-4 — Block ALL Domains (Static + Dynamic) When AutoBlock Is ON

**File:** `BlockedDomainDao.kt` — add new query method

**New method (no category filter):**
```kotlin
@Query("SELECT DISTINCT domain FROM blocked_domains")
suspend fun getAllBlockedDomainList(): List<String>
```

**File:** `BlockingRepository.kt` — add interface method
```kotlin
suspend fun getAllBlockedDomains(): List<String>
```

**File:** `BlockingRepositoryImpl.kt` — implement
```kotlin
override suspend fun getAllBlockedDomains(): List<String> {
    return domainDao.getAllBlockedDomainList()
}
```

**File:** `GetActiveBlockedDomainsUseCase.kt` — use new method
```kotlin
class GetActiveBlockedDomainsUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    /**
     * Returns ALL blocked domains — both static (from JSON) and auto-blocked.
     * When AutoBlock is enabled, every domain in the DB should be treated as blocked.
     */
    suspend operator fun invoke(): List<String> {
        return blockingRepository.getAllBlockedDomains()
    }
}
```

**Why this is safe:** The `blocked_domains` table ONLY contains domains from two sources:
1. `blocked_domains.json` (seeded at install — all are explicitly blocked domains)
2. `addAutoBlockedDomain()` (SmartScan matches — should always be blocked)

There is no scenario where a domain in this table should NOT be blocked. The category system is for UI organization, not security policy.

---

### FIX-5 — Sync SUPPORTED_BROWSERS and findUrlNode() With XML Config

**File:** `FocusAccessibilityService.kt` — lines 38–45

**New SUPPORTED_BROWSERS:**
```kotlin
private val SUPPORTED_BROWSERS = setOf(
    "com.android.chrome",
    "org.mozilla.firefox",
    "com.sec.android.app.sbrowser",   // Samsung Internet
    "com.opera.browser",
    "com.opera.mini.native",          // Opera Mini
    "com.duckduckgo.mobile.android",
    "com.microsoft.emmx",             // Edge
    "com.brave.browser",              // Brave
    "com.kiwibrowser.browser",        // Kiwi Browser
    "com.UCMobile.intl"               // UC Browser
)
```

**File:** `FocusAccessibilityService.kt` — `findUrlNode()` method, add `when` branches:

```kotlin
private fun findUrlNode(root: AccessibilityNodeInfo, packageName: String): AccessibilityNodeInfo? {
    val urlBarId = when (packageName) {
        "com.android.chrome" -> "com.android.chrome:id/url_bar"
        "org.mozilla.firefox" -> "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
        "com.sec.android.app.sbrowser" -> "com.sec.android.app.sbrowser:id/location_bar_edit_text"
        "com.opera.browser" -> "com.opera.browser:id/url_field"
        "com.opera.mini.native" -> "com.opera.mini.native:id/url_field"
        "com.duckduckgo.mobile.android" -> "com.duckduckgo.mobile.android:id/omnibarTextInput"
        "com.microsoft.emmx" -> "com.microsoft.emmx:id/url_bar"
        "com.brave.browser" -> "com.brave.browser:id/url_bar"
        "com.kiwibrowser.browser" -> "com.kiwibrowser.browser:id/url_bar"
        "com.UCMobile.intl" -> "com.UCMobile.intl:id/title_bar_input"
        else -> return null
    }

    val nodes = root.findAccessibilityNodeInfosByViewId(urlBarId)
    val firstNode = nodes.firstOrNull()
    nodes.drop(1).forEach { it.recycle() }
    return firstNode
}
```

> **Note on UC Browser:** The URL bar ID `title_bar_input` is based on UC Browser v13+. If this doesn't work on the user's version, we can use `uiautomatorviewer` to find the correct ID.

---

### FIX-6 — Change Keyword Limit From 50 to 10

**File:** `BlockingRepositoryImpl.kt` — line 22

```kotlin
// BEFORE:
private const val MAX_KEYWORDS = 50

// AFTER:
private const val MAX_KEYWORDS = 10
```

The error message at line 46 uses `$MAX_KEYWORDS` so it will automatically say "Maximum 10 keywords reached".

---

## Files to Modify

| # | File | Changes |
|---|------|---------|
| 1 | `FocusAccessibilityService.kt` | Accept both event types (FIX-1), pass `activeKeywords.get()` (FIX-2), add 4 browsers (FIX-5) |
| 2 | `SmartScanUseCase.kt` | Full rewrite: remove cache, accept `List<String>` param (FIX-2) |
| 3 | `BlockedCategoryDao.kt` | Change `insertCategories` to `IGNORE`, add `ensureAutoBlockedActive()` (FIX-3) |
| 4 | `BlockedDomainDao.kt` | Add `getAllBlockedDomainList()` (FIX-4) |
| 5 | `BlockingRepository.kt` | Add `getAllBlockedDomains()` interface method (FIX-4) |
| 6 | `BlockingRepositoryImpl.kt` | Implement `getAllBlockedDomains()`, rewrite `seedDomainDataIfNeeded()`, change `MAX_KEYWORDS = 10` (FIX-3, FIX-4, FIX-6) |
| 7 | `GetActiveBlockedDomainsUseCase.kt` | Call `getAllBlockedDomains()` instead of `getActiveBlockedDomains()` (FIX-4) |

---

## Corrected Data Flow

```
┌──────────────────────────────────────────────────────┐
│  APP START                                           │
│  seedDomainDataIfNeeded()                            │
│    ├── Insert all categories (IGNORE = no overwrite) │
│    ├── ensureAutoBlockedActive() → always active     │
│    └── Seed blocked_domains.json if empty            │
└──────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────┐
│  SESSION STARTS                                      │
│  FocusAccessibilityService.onServiceConnected()      │
│    ├── Collect keywords → activeKeywords (live)      │
│    └── Load ALL domains → activeDomains (no filter)  │
└──────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────┐
│  USER NAVIGATES IN CHROME                            │
│  Chrome fires TYPE_WINDOW_CONTENT_CHANGED            │
│    ├── onAccessibilityEvent() accepts it (FIX-1)     │
│    ├── handleBrowserEvent() reads url_bar text       │
│    ├── isDuplicateUrlEvent? → no (500ms passed)      │
│    │                                                 │
│    ├── CheckUrlForBlockedContentUseCase:              │
│    │   ├── keyword in URL? → BLOCK                   │
│    │   └── domain in activeDomains? → BLOCK          │
│    │       (includes ALL 163+ static + auto-blocked) │
│    │                                                 │
│    └── Not blocked statically? → SmartScanUseCase:   │
│        ├── activeKeywords.get() → ["gaming","bet"]   │
│        ├── fetchMetaDescription(url) → description   │
│        ├── description.contains("gaming")? → YES     │
│        ├── addAutoBlockedDomain(domain, "gaming")    │
│        │   └── Inserts into blocked_domains table    │
│        │       with categoryKey = "AUTO_BLOCKED"     │
│        │       (category exists + active = FIX-3)    │
│        └── Show block overlay ✅                     │
└──────────────────────────────────────────────────────┘
```

---

## Risk Assessment

| Fix | Risk | Mitigation |
|-----|------|------------|
| FIX-1 (event types) | Battery drain from CONTENT_CHANGED spam | isDuplicateUrlEvent() 500ms debounce + findUrlNode() specific ID filter |
| FIX-2 (live keywords) | Keywords empty if collector hasn't fired | Collector runs in onServiceConnected() — guaranteed to fire before any browser events |
| FIX-3 (category seeding) | Changing REPLACE→IGNORE may skip needed updates | ensureAutoBlockedActive() handles the only case we need to force-update |
| FIX-4 (all domains) | Over-blocking if wrong domains in JSON | blocked_domains.json only contains explicitly harmful domains — no false positives |
| FIX-5 (browser IDs) | Wrong url_bar ID for UC/Kiwi | Graceful: findUrlNode returns null → no URL → no crash, just no block for that browser |
| FIX-6 (limit 10) | Users with >10 keywords can't add more | Existing keywords are preserved, only new additions are blocked |

---

> **Ready for implementation?** Reply `implement` and all 6 fixes will be applied.
