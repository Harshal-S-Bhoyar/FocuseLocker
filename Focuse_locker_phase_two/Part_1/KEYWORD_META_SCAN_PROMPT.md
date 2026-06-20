# KEYWORD_META_SCAN_PROMPT.md
# Feature: Fast Keyword Meta Scanning
# App: FocusLock (Android)
# Language: Kotlin
# Total Phases: 3 (Phases 1–2 ALREADY DONE)

---

## CONTEXT

FocusLock is an Android self-discipline browser content blocker. It uses an
Accessibility Service (`FocusAccessibilityService`) to capture URLs from browser
URL bars. This prompt covers building a fast keyword meta scanning module used
as a last-resort fallback when all other blocking layers (Room DB cache, URL
pre-check, NextDNS DoH) fail to classify a domain.

The scan must be fast. The network fetch is the bottleneck — not string matching.
Use Kotlin Coroutines for async fetch and Aho-Corasick algorithm for keyword
matching. Never loop `.contains()` per keyword — that is O(n×k) and will slow
down as the keyword list grows.

### Existing Codebase (DO NOT RECREATE)

| Component | Class / File | Status |
|---|---|---|
| Async page fetch | `MetaDescriptionFetcher.fetchPageSignals()` — 2 retries, 4s timeout, OkHttp, SSRF protection | ✅ Done (Phase 1) |
| Signal extraction | `MetaDescriptionFetcher.extractSignals()` — 7 signals + body_text via Jsoup | ✅ Done (Phase 2) |
| Normalization | `NormalizedMatcher.normalize()` — NFD + leet + separator removal | ✅ Done (partial Phase 3) |
| Word boundary | `NormalizedMatcher.matches()` — ≤4 char: `\b` regex, ≥5 char: substring | ✅ Done (partial Phase 3) |
| Keyword match loop | `SmartScanUseCase.invoke()` — O(n×k) nested for-loop with synonym expansion | ⚠️ Works but not optimal |
| Scan result | `SmartScanResult` sealed class — `Blocked(keyword, signal)`, `Clean`, `FetchFailed(reason)` | ✅ Done |
| Fetch outcome | `FetchOutcome` sealed class — `Success(signals)`, `Failed(reason)` | ✅ Done |

---

---

## PHASE 1 — Async Page Fetch with Coroutines [ALREADY DONE — SKIP]

**Implemented in**: `MetaDescriptionFetcher.fetchPageSignals()`
- OkHttp via `HttpURLConnection`, 3s connect / 3s read / 4s coroutine timeout
- Reads first 16KB (two 8KB chunks), not full body
- 2 retry attempts with 500ms delay
- SSRF protection via `isPrivateHost()`
- Returns `FetchOutcome.Success(signals)` or `FetchOutcome.Failed(reason)`

No action needed.

---

---

## PHASE 2 — Multi-Signal Extraction [ALREADY DONE — SKIP]

**Implemented in**: `MetaDescriptionFetcher.extractSignals()`
- Extracts: meta_description, meta_keywords, og_title, og_description,
  twitter_description, page_title, first_h1, body_text
- Uses Jsoup, handles missing tags gracefully
- Returns `Map<String, String>` filtered to non-blank values

No action needed.

---

---

## PHASE 3 — Aho-Corasick Keyword Matching [TODO]

### GOAL
Replace the current O(n×k) nested loop in `SmartScanUseCase` with an O(n)
single-pass Aho-Corasick automaton. The current implementation loops through
each keyword × each signal × synonym expansion, which degrades as the keyword
list grows beyond ~20 entries.

### WHAT TO BUILD

**Part A — Aho-Corasick implementation:**
Create `domain/usecase/AhoCorasick.kt` implementing the Aho-Corasick algorithm
from scratch in Kotlin (no external library needed — the implementation is small).

1. A `TrieNode` class with:
   - `children: HashMap<Char, TrieNode>`
   - `failureLink: TrieNode?`
   - `outputKeywords: MutableList<String>` — keywords that end at this node

2. An `AhoCorasick` class with:
   - `build(keywords: List<String>)` — constructs the trie and failure links
     using a BFS queue. Normalize each keyword using
     `NormalizedMatcher.normalize()` before inserting into the trie.
   - `search(text: String): String?` — runs the automaton over the text in a
     single pass, returns the first matched keyword or null if no match.

3. Word boundary rule inside `search()`:
   - For keywords of 4 characters or fewer: only match if the character before
     the match start and the character after the match end are non-alphabetic
     (word boundary). This prevents `ass` matching inside `class`.
   - For keywords of 5 characters or longer: substring match is fine — no
     boundary check needed.

**Part B — KeywordScanner singleton:**
Create `domain/usecase/KeywordScanner.kt` as a `@Singleton` that:

1. Holds a single `AhoCorasick` instance via `AtomicReference`.
2. Exposes `rebuild(keywords: List<String>)` — builds a NEW `AhoCorasick`
   instance and swaps it atomically. This includes synonym expansion from
   `keyword_synonyms.json` — read the JSON once, expand each keyword into
   `[keyword] + synonyms`, normalize all, build trie.
3. Exposes `scan(text: String): String?` — calls the current automaton's
   `search(NormalizedMatcher.normalize(text))` and returns the matched
   keyword or null.
4. Thread-safe: `rebuild()` creates a new instance, `scan()` reads the current
   one. Use `AtomicReference<AhoCorasick>` for lock-free swap.

Inject via Hilt `@Singleton`. Call `rebuild()` in `FocusAccessibilityService.onServiceConnected()`
whenever `getActiveKeywordsUseCase()` emits a new list.

**Part C — Wire into SmartScanUseCase:**
Replace the nested loop in `SmartScanUseCase.invoke()`:

Current code (lines 81-95):
```kotlin
for (keyword in activeKeywords) {
    val kw = keyword.lowercase()
    val expansion = listOf(kw) + (synonymsMap[kw] ?: emptyList())
    for (term in expansion) {
        for ((signalName, signalValue) in signals) {
            if (NormalizedMatcher.matches(term, signalValue)) {
                // ...block...
            }
        }
    }
}
```

Replace with:
```kotlin
// Concatenate all signal values into one string for single-pass scan
val combinedSignals = signals.values.joinToString(" ")
val matchedKeyword = keywordScanner.scan(combinedSignals)
if (matchedKeyword != null) {
    blockingRepository.addAutoBlockedDomain(domain, matchedKeyword, "PERMANENT", null)
    val result = SmartScanResult.Blocked(matchedKeyword, "aho_corasick")
    memoryCache.put(domain, result)
    return result
}
```

Inject `KeywordScanner` into `SmartScanUseCase` constructor. Remove the
`synonymsMap` lazy property from `SmartScanUseCase` — synonym expansion
moves into `KeywordScanner.rebuild()`.

**Part D — Define MetaScanResult (OPTIONAL):**
The plan originally called for a `MetaScanResult` sealed class. This is NOT
needed — `SmartScanResult` already serves this purpose. Keep using
`SmartScanResult.Blocked`, `SmartScanResult.Clean`, `SmartScanResult.FetchFailed`.

### ACCEPTANCE CRITERIA
- `AhoCorasick.search()` makes exactly one pass over the text regardless of
  how many keywords exist — O(n) complexity
- Keyword `ass` does NOT match in text containing `class` or `assassin`
- Keyword `casino` DOES match in text containing `casinobonus`
- `KeywordScanner.rebuild()` can be called safely at any time without crashing
  an in-progress scan (atomic reference swap, not in-place mutation)
- `SmartScanUseCase` no longer contains the nested for-loop
- `synonymsMap` loading moves from `SmartScanUseCase` to `KeywordScanner`
- All existing unit tests in `SmartScanUseCaseTest` (if any) still pass

---

## EXECUTION ORDER (UPDATED)

| Phase | Builds | Status |
|---|---|---|
| 1 | Async page fetch | ✅ DONE — `MetaDescriptionFetcher` |
| 2 | Signal extraction + normalization | ✅ DONE — `extractSignals()` + `NormalizedMatcher` |
| 3 | Aho-Corasick matcher + wire-up | ⚠️ TODO |

**Only Phase 3 requires implementation work.**

---

_End of KEYWORD_META_SCAN_PROMPT.md_
