# Phase 2 Part 1 — Comprehensive Solution Plan

**Date:** 2026-06-11 | **Status:** Awaiting Approval  
**Scope:** 8 issues (1 critical, 1 medium, 6 minor) from code review

---

## Fix 1 — [CRITICAL] Implement Aho-Corasick Keyword Matcher

**Problem:** `SmartScanUseCase` uses O(n×k) nested loop for keyword matching. `AhoCorasick.kt` and `KeywordScanner.kt` do not exist. Entire KEYWORD_META_SCAN Phase 3 is missing.

**Current code (SmartScanUseCase.kt:88-102):**
```kotlin
for (keyword in activeKeywords) {
    val kw = keyword.lowercase()
    val expansion = listOf(kw) + (synonymsMap[kw] ?: emptyList())
    for (term in expansion) {
        for ((signalName, signalValue) in signals) {
            if (NormalizedMatcher.matches(term, signalValue)) { ... }
        }
    }
}
```

### Solution — 3 new/modified files

#### [NEW] `app/src/main/java/com/focuslock/domain/usecase/AhoCorasick.kt`

```kotlin
package com.focuslock.domain.usecase

/**
 * Aho-Corasick automaton for O(n + m + z) multi-pattern matching.
 * n = text length, m = total pattern chars, z = number of matches.
 * 
 * Build once via [Builder], then call [search] on any text.
 * Thread-safe after build (immutable state arrays).
 */
class AhoCorasick private constructor(
    private val goto: Array<IntArray>,     // goto[state][charIndex] → next state
    private val fail: IntArray,            // failure links
    private val output: Array<List<String>> // output[state] → matched keywords
) {
    data class Match(val keyword: String, val position: Int)

    /**
     * Single-pass search. Returns first match found (short-circuit) or null.
     * For blocking we only need to know IF any keyword matches, not all of them.
     */
    fun searchFirst(text: String): Match? {
        var state = 0
        for (i in text.indices) {
            val c = charIndex(text[i])
            if (c < 0) { state = 0; continue }

            while (state != 0 && goto[state][c] == -1) {
                state = fail[state]
            }
            state = if (goto[state][c] != -1) goto[state][c] else 0

            // Check output at this state + all failure-linked states
            var tmp = state
            while (tmp != 0) {
                if (output[tmp].isNotEmpty()) {
                    return Match(output[tmp][0], i)
                }
                tmp = fail[tmp]
            }
        }
        return null
    }

    class Builder {
        private val gotoList = mutableListOf(IntArray(ALPHABET_SIZE) { -1 }) // state 0
        private val outputList = mutableListOf<MutableList<String>>(mutableListOf())
        private var stateCount = 1

        fun addPattern(pattern: String): Builder {
            var state = 0
            for (ch in pattern) {
                val c = charIndex(ch)
                if (c < 0) continue
                if (gotoList[state][c] == -1) {
                    gotoList[state][c] = stateCount++
                    gotoList.add(IntArray(ALPHABET_SIZE) { -1 })
                    outputList.add(mutableListOf())
                }
                state = gotoList[state][c]
            }
            outputList[state].add(pattern)
            return this
        }

        fun build(): AhoCorasick {
            val fail = IntArray(stateCount)
            val queue = ArrayDeque<Int>()

            // Depth-1 states: fail → 0
            for (c in 0 until ALPHABET_SIZE) {
                val s = gotoList[0][c]
                if (s > 0) { fail[s] = 0; queue.addLast(s) }
            }

            // BFS to build failure links
            while (queue.isNotEmpty()) {
                val r = queue.removeFirst()
                for (c in 0 until ALPHABET_SIZE) {
                    val s = gotoList[r][c]
                    if (s == -1) continue
                    queue.addLast(s)

                    var state = fail[r]
                    while (state != 0 && gotoList[state][c] == -1) {
                        state = fail[state]
                    }
                    fail[s] = if (gotoList[state][c] != -1 && gotoList[state][c] != s) {
                        gotoList[state][c]
                    } else {
                        0
                    }
                    outputList[s].addAll(outputList[fail[s]])
                }
            }

            return AhoCorasick(
                goto = gotoList.toTypedArray(),
                fail = fail,
                output = outputList.map { it.toList() }.toTypedArray()
            )
        }
    }

    companion object {
        // a-z + 0-9 + space = 37 chars. All others mapped to -1 (ignored).
        private const val ALPHABET_SIZE = 37

        private fun charIndex(c: Char): Int = when (c) {
            in 'a'..'z' -> c - 'a'           // 0-25
            in '0'..'9' -> 26 + (c - '0')    // 26-35
            ' ' -> 36
            else -> -1
        }
    }
}
```

#### [NEW] `app/src/main/java/com/focuslock/domain/usecase/KeywordScanner.kt`

```kotlin
package com.focuslock.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton keyword scanner backed by Aho-Corasick.
 * Call [rebuild] when keywords change → atomically swaps the automaton.
 * Call [scan] from SmartScanUseCase → O(n) single-pass matching.
 */
@Singleton
class KeywordScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val automatonRef = AtomicReference<AhoCorasick?>(null)
    private val keywordMapRef = AtomicReference<Map<String, String>>(emptyMap())

    // Loaded once from assets/keyword_synonyms.json
    private val synonymsMap: Map<String, List<String>> by lazy {
        try {
            val jsonStr = context.assets.open("keyword_synonyms.json")
                .bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, List<String>>()
            for (key in json.keys()) {
                val array = json.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                map[key] = list
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Rebuild the automaton with new keywords. Thread-safe via AtomicReference swap.
     * Maps each synonym/expansion back to its parent keyword for match reporting.
     */
    fun rebuild(keywords: List<String>) {
        val builder = AhoCorasick.Builder()
        val termToKeyword = mutableMapOf<String, String>()

        for (keyword in keywords) {
            val kw = keyword.lowercase()
            val normalized = NormalizedMatcher.normalize(kw)
            builder.addPattern(normalized)
            termToKeyword[normalized] = kw

            val synonyms = synonymsMap[kw] ?: emptyList()
            for (syn in synonyms) {
                val normalizedSyn = NormalizedMatcher.normalize(syn)
                builder.addPattern(normalizedSyn)
                termToKeyword[normalizedSyn] = kw
            }
        }

        automatonRef.set(builder.build())
        keywordMapRef.set(termToKeyword)
    }

    /**
     * Scan signals map for any keyword match. Returns (parentKeyword, signalName) or null.
     * Uses Aho-Corasick O(n) single-pass for long keywords (≥5 chars).
     * Short keywords (≤4 chars) still use NormalizedMatcher.matches() for word-boundary
     * safety — prevents "ass" matching inside "class" or "assassin".
     */
    fun scan(signals: Map<String, String>): Pair<String, String>? {
        val automaton = automatonRef.get() ?: return null
        val termMap = keywordMapRef.get()

        for ((signalName, signalValue) in signals) {
            val normalizedContent = NormalizedMatcher.normalize(signalValue)

            // Phase 1: Aho-Corasick pass for long patterns (≥5 chars, substring match)
            val match = automaton.searchFirst(normalizedContent)
            if (match != null && match.keyword.length >= 5) {
                val parentKeyword = termMap[match.keyword] ?: match.keyword
                return Pair(parentKeyword, signalName)
            }

            // Phase 2: Word-boundary check for short patterns (≤4 chars)
            // AC can't enforce \b boundaries, so we fall back to NormalizedMatcher
            for ((term, parentKw) in termMap) {
                if (term.length <= 4 && NormalizedMatcher.matches(term, signalValue)) {
                    return Pair(parentKw, signalName)
                }
            }
        }
        return null
    }
}
```

#### [MODIFY] `app/src/main/java/com/focuslock/domain/usecase/SmartScanUseCase.kt`

Replace the nested loop (lines 85-109) with `KeywordScanner.scan()`:

```kotlin
// BEFORE (lines 15-20, constructor):
@Singleton
class SmartScanUseCase @Inject constructor(
    private val fetcher: MetaDescriptionFetcher,
    private val blockingRepository: BlockingRepository,
    @ApplicationContext private val context: Context
) {

// AFTER:
@Singleton
class SmartScanUseCase @Inject constructor(
    private val fetcher: MetaDescriptionFetcher,
    private val blockingRepository: BlockingRepository,
    private val keywordScanner: KeywordScanner
) {
```

> **Note:** Keep the `invoke(urlStr: String, activeKeywords: List<String>)` signature.
> The `activeKeywords` param is still used for the `isEmpty()` early-return guard at line 48.
> The actual matching is delegated to `keywordScanner.scan()` (which uses its own rebuilt state).

```kotlin
// REMOVE entirely: lines 21-38 (synonymsMap lazy block)
// REMOVE: import android.content.Context
// REMOVE: import dagger.hilt.android.qualifiers.ApplicationContext
// REMOVE: import org.json.JSONObject
```

```kotlin
// BEFORE (lines 85-109, inside Success branch):
is FetchOutcome.Success -> {
    val signals = outcome.signals
    
    for (keyword in activeKeywords) {
        val kw = keyword.lowercase()
        val expansion = listOf(kw) + (synonymsMap[kw] ?: emptyList())
        for (term in expansion) {
            for ((signalName, signalValue) in signals) {
                if (NormalizedMatcher.matches(term, signalValue)) {
                    blockingRepository.addAutoBlockedDomain(domain, kw, "PERMANENT", null)
                    val result = SmartScanResult.Blocked(kw, signalName)
                    memoryCache.put(domain, result)
                    return result
                }
            }
        }
    }
    
    val expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
    blockingRepository.addAutoBlockedDomain(domain, "", "CLEAN_CACHE", expiresAt)
    val result = SmartScanResult.Clean
    memoryCache.put(domain, result)
    return result
}

// AFTER:
is FetchOutcome.Success -> {
    val signals = outcome.signals
    val match = keywordScanner.scan(signals)

    if (match != null) {
        val (kw, signalName) = match
        blockingRepository.addAutoBlockedDomain(domain, kw, "PERMANENT", null)
        val result = SmartScanResult.Blocked(kw, signalName)
        memoryCache.put(domain, result)
        return result
    }

    val expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
    blockingRepository.addAutoBlockedDomain(domain, "", "CLEAN_CACHE", expiresAt)
    val result = SmartScanResult.Clean
    memoryCache.put(domain, result)
    return result
}
```

#### [MODIFY] `di/BlockingServiceEntryPoint.kt` — Expose KeywordScanner

```kotlin
// ADD to the interface:
fun keywordScanner(): com.focuslock.domain.usecase.KeywordScanner
```

#### [MODIFY] `FocusAccessibilityService.kt` — Inject and trigger rebuild

Add field (after line 74):
```kotlin
private lateinit var keywordScanner: com.focuslock.domain.usecase.KeywordScanner
```

Initialize in `onServiceConnected()` (after line 94):
```kotlin
keywordScanner = entryPoint.keywordScanner()
```

In the keyword observer (line 116-118), add `rebuild` call:
```kotlin
// BEFORE:
serviceScope.launch {
    getActiveKeywordsUseCase().collect { keywords ->
        activeKeywords.set(keywords)
    }
}

// AFTER:
serviceScope.launch {
    getActiveKeywordsUseCase().collect { keywords ->
        activeKeywords.set(keywords)
        keywordScanner.rebuild(keywords)
    }
}
```

**Verification:** With 10 keywords × ~7 synonyms each = ~80 patterns → automaton builds in <1ms. Search on 2KB of HTML signals → O(n) single pass vs O(n×80) before.

---

## Fix 2 — [MEDIUM] NextDNS Profile from SharedPreferences

**Problem:** `DoHQueryUseCase` reads `BuildConfig.NEXTDNS_PROFILE_ID` (compile-time). User cannot change profile at runtime.

#### [MODIFY] `DoHQueryUseCase.kt` — Read from SharedPreferences with BuildConfig fallback

```kotlin
// BEFORE (line 27):
val profileId = com.focuslock.BuildConfig.NEXTDNS_PROFILE_ID

// AFTER:
val prefs = context.getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE)
val profileId = prefs.getString("nextdns_profile_id", null)
    ?: com.focuslock.BuildConfig.NEXTDNS_PROFILE_ID
```

#### [MODIFY] `BlockingRepository.kt` — Add profile methods

```kotlin
// Add to interface:
suspend fun saveNextDnsProfileId(profileId: String)
fun getNextDnsProfileId(): String
```

#### [MODIFY] `BlockingRepositoryImpl.kt` — Implement

```kotlin
override suspend fun saveNextDnsProfileId(profileId: String) {
    context.getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE)
        .edit().putString("nextdns_profile_id", profileId).apply()
}

override fun getNextDnsProfileId(): String {
    return context.getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE)
        .getString("nextdns_profile_id", null)
        ?: com.focuslock.BuildConfig.NEXTDNS_PROFILE_ID
}
```

**Verification:** Default behavior unchanged (falls back to BuildConfig). Settings screen can call `saveNextDnsProfileId()` when user enters a new profile.

---

## Fix 3 — [MINOR] Unify `extractDomain()` into Shared Utility

**Problem:** `DoHQueryUseCase` strips `www.`, `m.`, `mobile.`. `SmartScanUseCase` only strips `www.`. Same URL can produce different domain keys → cache misses.

#### [NEW] `app/src/main/java/com/focuslock/domain/usecase/DomainExtractor.kt`

```kotlin
package com.focuslock.domain.usecase

/**
 * Single source of truth for URL → domain extraction.
 * Strips www., m., mobile. prefixes for consistent cache keying.
 */
object DomainExtractor {
    fun extract(url: String): String? {
        return try {
            val uri = java.net.URI(if (url.startsWith("http")) url else "https://$url")
            val host = uri.host?.lowercase() ?: return null
            host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
        } catch (e: Exception) {
            null
        }
    }
}
```

#### [MODIFY] `SmartScanUseCase.kt`

```kotlin
// BEFORE (lines 113-121):
private fun extractDomain(url: String): String? { ... }

// AFTER: Delete the method entirely. Replace call at line 50:
val domain = DomainExtractor.extract(urlStr) ?: return SmartScanResult.Clean
```

#### [MODIFY] `DoHQueryUseCase.kt`

```kotlin
// BEFORE (lines 85-94):
private fun extractDomain(url: String): String? { ... }

// AFTER: Delete the method entirely. Replace call at line 63:
val domain = DomainExtractor.extract(url) ?: return DoHResult.QueryFailed
```

**Verification:** `m.pornhub.com` → both DoH and SmartScan now resolve to `pornhub.com`. No duplicate cache entries.

---

## Fix 4 — [MINOR] Fix MetaDescriptionFetcher Cache Eviction

**Problem:** `ConcurrentHashMap` evicts random element via `keys().nextElement()`. Not LRU.

#### [MODIFY] `MetaDescriptionFetcher.kt`

```kotlin
// BEFORE (line 22-23):
private val descriptionCache = ConcurrentHashMap<String, Map<String, String>>()
private val MAX_CACHE_SIZE = 200

// AFTER:
private val descriptionCache = object : LinkedHashMap<String, Map<String, String>>(
    200, 0.75f, true  // accessOrder = true → LRU
) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>): Boolean {
        return size > 200
    }
}
```

```kotlin
// REMOVE lines 38-41 (manual eviction block):
if (descriptionCache.size >= MAX_CACHE_SIZE) {
    val firstKey = descriptionCache.keys().nextElement()
    descriptionCache.remove(firstKey)
}
```

Since `LinkedHashMap` is not thread-safe, wrap access with `@Synchronized`:

```kotlin
// Add to class:
@Synchronized
private fun getCachedEntry(key: String): Pair<Boolean, Map<String, String>?> {
    if (!descriptionCache.containsKey(key)) return Pair(false, null)
    return Pair(true, descriptionCache[key])
}

@Synchronized
private fun putCache(key: String, value: Map<String, String>) { descriptionCache[key] = value }
```

```kotlin
// Replace lines 33-36 (containsKey + get MUST be atomic — empty map is a valid cache entry):
// BEFORE:
if (descriptionCache.containsKey(cacheKey)) {
    val cached = descriptionCache[cacheKey]
    return@withContext if (cached.isNullOrEmpty()) FetchOutcome.FetchEmpty else FetchOutcome.Success(cached)
}

// AFTER:
val (found, cached) = getCachedEntry(cacheKey)
if (found) {
    return@withContext if (cached.isNullOrEmpty()) FetchOutcome.FetchEmpty else FetchOutcome.Success(cached!!)
}

// Replace all other direct descriptionCache[] calls with putCache()
```

**Verification:** Least-recently-accessed domains evicted first. No `Enumeration` overhead.

---

## Fix 5 — [MINOR] Add TTL to SmartScan Memory Cache

**Problem:** `LruCache<String, SmartScanResult>(50)` has no expiration. Domain marked `Clean` stays cached even after StevenBlack sync adds it to block list.

#### [MODIFY] `SmartScanUseCase.kt`

```kotlin
// BEFORE (line 41):
private val memoryCache = LruCache<String, SmartScanResult>(50)

// AFTER:
private data class CacheEntry(val result: SmartScanResult, val timestamp: Long)
private val memoryCache = LruCache<String, CacheEntry>(50)
private val CACHE_TTL_MS = 15 * 60 * 1000L  // 15 minutes

// Update cache reads (line 53):
// BEFORE:
memoryCache.get(domain)?.let { return it }
// AFTER:
memoryCache.get(domain)?.let { entry ->
    if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
        return entry.result
    } else {
        memoryCache.remove(domain)
    }
}

// Update all cache writes — wrap result in CacheEntry:
// BEFORE:
memoryCache.put(domain, result)
// AFTER:
memoryCache.put(domain, CacheEntry(result, System.currentTimeMillis()))
```

**Verification:** Stale entries expire after 15 min. New blocks from StevenBlack sync take effect within 15 min max.

---

## Fix 6 — [MINOR] ListSyncWorker Conditional Download

**Problem:** Worker downloads full ~10MB hosts file every 7 days even if unchanged.

#### [MODIFY] `ListSyncWorker.kt`

```kotlin
// BEFORE (line 34):
val request = Request.Builder().url(url).build()

// AFTER:
val prefs = context.getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE)
val etagKey = "etag_${categoryKey}"
val lastEtag = prefs.getString(etagKey, null)

val requestBuilder = Request.Builder().url(url)
if (lastEtag != null) {
    requestBuilder.header("If-None-Match", lastEtag)
}
val request = requestBuilder.build()
```

```kotlin
// BEFORE (line 37):
if (response.isSuccessful) {

// AFTER:
if (response.code == 304) {
    // Not modified — skip parsing
    continue
}
if (response.isSuccessful) {
    // Save ETag for next sync
    response.header("ETag")?.let { etag ->
        prefs.edit().putString(etagKey, etag).apply()
    }
```

**Verification:** Second sync with unchanged list returns 304 → no download, no parsing, no DB writes.

---

## Fix 7 — [MINOR] Suppress BODY Logging for Large Downloads

**Problem:** Shared `OkHttpClient` logs full response body at `Level.BODY` in debug. Downloading 10MB hosts file floods logcat.

#### [MODIFY] `ListSyncWorker.kt`

```kotlin
// BEFORE (line 35):
val response = okHttpClient.newCall(request).execute()

// AFTER:
val syncClient = okHttpClient.newBuilder()
    .apply {
        // Remove logging interceptor for bulk downloads
        interceptors().removeAll { it is okhttp3.logging.HttpLoggingInterceptor }
    }
    .build()
val response = syncClient.newCall(request).execute()
```

**Verification:** No log flooding during sync. All other API calls retain debug logging.

---

## Execution Order

| Order | Fix | Files Changed | Risk |
|---|---|---|---|
| 1 | Fix 3 — DomainExtractor | NEW: 1, MOD: 2 | Low (refactor, no logic change) |
| 2 | Fix 1 — Aho-Corasick | NEW: 2, MOD: 3 | Medium (new algorithm + DI) |
| 3 | Fix 5 — Cache TTL | MOD: 1 | Low (additive) |
| 4 | Fix 4 — LRU eviction | MOD: 1 | Low (internal change) |
| 5 | Fix 2 — NextDNS prefs | MOD: 3 | Low (fallback preserves behavior) |
| 6 | Fix 6 — Conditional download | MOD: 1 | Low (additive) |
| 7 | Fix 7 — Logging suppress | MOD: 1 | Low (debug only) |

## Verification Checklist

- [ ] `AhoCorasick.searchFirst("onlinecasinobonus")` matches `casino` (long keyword, substring)
- [ ] `KeywordScanner.scan({"body_text": "classroom"})` does NOT match `ass` (short keyword, word-boundary enforced)
- [ ] `KeywordScanner.scan({"body_text": "nice ass photo"})` DOES match `ass` (word boundary satisfied)
- [ ] `KeywordScanner.rebuild()` completes in <5ms for 80 patterns
- [ ] `DomainExtractor.extract("https://m.facebook.com/page")` → `facebook.com`
- [ ] `DomainExtractor.extract("https://www.mobile.test.com")` → `test.com`
- [ ] DoH and SmartScan produce identical domain keys for same URL
- [ ] `BlockingServiceEntryPoint.keywordScanner()` resolves without Hilt error
- [ ] Keywords change → `keywordScanner.rebuild()` fires in service observer
- [ ] Memory cache entries expire after 15 min
- [ ] Second ListSyncWorker run with unchanged list → 304, no DB writes
- [ ] App builds and all existing tests pass
