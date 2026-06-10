# SmartScan Hardening — Definitive Implementation Plan

> **Basis:** Every finding verified against live code. Zero assumptions.
> **Supersedes:** `smart_scan_analysis.md` (which had 8 correctable inaccuracies).

---

## PART 1 — OPTIMALITY VERDICT ON PREVIOUS ANALYSIS

### Original 5-Phase Verdicts: Were They Correct?

| Phase | Previous Verdict | Re-Verified Verdict | Corrections Needed |
|---|---|---|---|
| 1 — URL Pre-Check | ✅ Valid, partially redundant | **⚠️ NOT redundant — but the PLACEMENT was wrong** | Bug report says "before Room DB check" — this is correct. `CheckUrlForBlockedContentUseCase` already does keyword+domain checks in a single call (`FocusAccessibilityService.kt:202`). Phase 1's URL pre-check must be a **separate call before line 202**, not a replacement. Previous analysis said "replacement" — that would break the domain check. |
| 2 — Multi-Signal | ✅ Excellent | **✅ Confirmed excellent** | No corrections. Jsoup still not in `build.gradle.kts` (verified). |
| 3 — Normalized Matching | ⚠️ Good with fixes | **⚠️ Confirmed — but previous fix for `\b` was also wrong** | Previous analysis said use `(?<![a-z])keyword(?![a-z])` — this STILL fails on content starting/ending with the keyword (lookahead finds nothing ≠ boundary). Correct approach: `(?:^|[^a-z])keyword(?:[^a-z]|$)` or Kotlin `Regex("""\bkeyword\b""")` which works fine for ASCII-only normalized text. |
| 4 — Fail-Closed | ✅ Critical | **✅ Confirmed critical** | Previous analysis recommended "scan inconclusive overlay auto-dismiss 30s" instead of 1-hour block. This is **too lenient** — a user on a harmful site would simply wait 30 seconds. Better: temporary block for 15 minutes (not 1 hour, not 30 seconds). |
| 5 — Domain Cache | ✅ Excellent | **⚠️ Partially wrong — new `domains` table is unnecessary** | Current `BlockedDomainEntity` already has `domain` (unique index), `isAutoBlocked`, `matchedKeyword`, `autoBlockedAt`. Adding a separate `domains` table duplicates this. The correct approach: add `blockStatus` and `expiresAt` columns to the EXISTING `blocked_domains` table via Room migration v5→v6. |

### Previous 5 Additional Options: Were They Optimal?

| Option | Previous Rating | Re-Verified | Corrections |
|---|---|---|---|
| A — Synonym Expansion | High impact, 1 day | **⚠️ Over-scoped** | A full synonym JSON requires maintenance. Better: use category-based expansion from `CategoryKey`. When user adds "casino", the category `GAMBLING` is already active → all `GAMBLING` domains are already blocked. Synonym expansion only adds value for SmartScan content matching, not domain matching. Scope it to SmartScan only. |
| B — Body Text Scan | 30 min, extremely high | **✅ Confirmed — but buffer size matters** | Previous analysis said "fetch 32KB instead of 8KB". Current code (`MetaDescriptionFetcher.kt:56`) reads `CharArray(8192)` = 8KB. Increasing to 32KB on mobile is risky — many sites serve 200KB+ HTML. Better: keep 8KB for head, add a **second focused read** of next 8KB for body text (16KB total, not 32KB). |
| C — Safe Browsing API | 2-3 days, high | **❌ Sub-optimal for this app** | FocusLocker blocks by user-defined keywords + categories. Google Safe Browsing blocks by Google's categories (malware, phishing). The overlap is small — Safe Browsing won't block a casino site unless Google has flagged it as malware. The 2-3 day effort doesn't justify the marginal gain. **Defer to post-v1.** |
| D — TLD Heuristics | 2 hours, medium | **⚠️ High false-positive risk** | `.xyz` hosts millions of legitimate sites. `wikipedia.org` ends in `.org`. TLD-based signals create a maintenance burden (which TLDs are "suspicious"?) with minimal payoff. **Not recommended.** |
| E — Risk Scoring | 2-3 days, high | **❌ Over-engineered for current stage** | Binary block/allow is correct for a parental/focus-lock app. Users expect "blocked" or "not blocked" — not "60% blocked." Scoring adds UX confusion and testing complexity. **Not recommended for v1.** |

---

## PART 2 — 8 FACTUAL CORRECTIONS TO PREVIOUS ANALYSIS

| # | Previous Claim | Actual Code Truth | Impact |
|---|---|---|---|
| 1 | "Phase 1 is a replacement for `CheckUrlForBlockedContentUseCase` logic" | `CheckUrlForBlockedContentUseCase` does BOTH keyword AND domain checks. Replacing it loses domain checking. Phase 1 must be an INSERT before line 202, not a replacement. | **HIGH — would break domain blocking** |
| 2 | "Use `(?<![a-z])keyword(?![a-z])` for word boundary" | This fails at string start/end. After NFD normalization strips non-ASCII, standard `\b` works correctly on the resulting ASCII-only text. | **MEDIUM — regex still broken** |
| 3 | "1-hour block too aggressive; use 30-second auto-dismiss" | 30-second dismiss lets users simply wait out the scan. 15-minute temp block is the correct middle ground. | **HIGH — defeats fail-closed purpose** |
| 4 | "Add new `domains` table, migrate v5→v6" | `blocked_domains` table already has a unique domain index, auto-blocked tracking, and the FK structure. Add columns to existing table instead of duplicating. | **MEDIUM — unnecessary schema complexity** |
| 5 | "Room migration should be v5→v6" | Correct version, confirmed: `FocusDatabase.kt:30` shows `version = 5`, `autoMigrations = [AutoMigration(from = 4, to = 5)]`. Next migration IS v5→v6. | **Confirmed correct** |
| 6 | "Query-param false positive: Google search blocks Google" | Code at `CheckUrlForBlockedContentUseCase.kt:22`: `lowerUrl.contains(keyword.lowercase())` — confirmed. But note: the URL bar in Chrome typically shows `google.com/search?q=casino` WITHOUT scheme prefix, so `URI()` parsing at line 29 may also fail on schemeless input. | **HIGH — parse also breaks** |
| 7 | "ConcurrentHashMap cache lost on service restart" | `MetaDescriptionFetcher` is `@Singleton` (line 11). As a Hilt singleton, it survives as long as the application process lives. AccessibilityService restart does NOT kill the process. Cache is only lost on process death (system kill or app force-stop). Previous analysis overstated the problem. | **LOW — less critical than stated** |
| 8 | "Synonym JSON ~10KB, loaded once at startup" | `GetActiveKeywordsUseCase` returns keywords from Room Flow (live). Synonyms must be injected at this layer, not "loaded at startup." Must map each keyword → synonyms dynamically as the Flow emits. | **MEDIUM — wrong injection point** |

---

## PART 3 — 3 NEW SUGGESTIONS (NOT IN PREVIOUS ANALYSIS)

### New-1: Structured ScanResult Propagation to Overlay UI

**Problem discovered:** `FocusAccessibilityService.kt:217` hardcodes `"Smart Scan Match"` as `matchedTerm` and `"AUTO_BLOCKED"` as `blockReason`. The user never sees WHICH keyword matched or WHY the site was blocked.

**Fix:** Propagate `SmartScanUseCase` return type from `Boolean` to a data class:
```kotlin
data class SmartScanResult(
    val isBlocked: Boolean,
    val matchedKeyword: String? = null,
    val signalSource: String? = null  // "meta_description", "og:title", "body_text", etc.
)
```
Then pass `matchedKeyword` to `launchOverlayService()` so the user sees "Blocked: keyword 'casino' found in page title."

**Effort:** 30 minutes. Zero architectural change.

---

### New-2: Service-Restart Domain Warmup from Room

**Problem discovered:** When AccessibilityService reconnects (`onServiceConnected`), the `activeDomains` AtomicReference starts empty. Line 98 collects `getAllBlockedDomainsFlowUseCase()` which emits from Room, but there's a startup gap where domains haven't loaded yet. Any URL visited in the first 100-500ms after service connect will only be checked against an empty domain list.

**Fix:** Add a `suspendCoroutine` barrier or preload `activeDomains` synchronously from `runBlocking` in `onServiceConnected` before starting event processing. Or simpler: check `activeDomains.get().isEmpty()` in `handleBrowserEvent` and skip processing until loaded.

**Effort:** 15 minutes.

---

### New-3: SmartScan Race Condition on Rapid Navigation

**Problem discovered:** `FocusAccessibilityService.kt:211-220` — `pendingSmartScanJob?.cancel()` cancels the previous scan when a new URL arrives. But `cancel()` is cooperative — the coroutine only stops at the next suspension point. If `smartScanUseCase` is between the `fetchMetaDescription` return and the `addAutoBlockedDomain` write, the cancel may arrive too late, causing a stale domain to be persisted.

**Fix:** Check `isActive` after the fetch returns and before persisting:
```kotlin
// Inside SmartScanUseCase.invoke()
val description = fetcher.fetchMetaDescription(urlStr) ?: return SmartScanResult(false)
coroutineContext.ensureActive()  // ← throws CancellationException if cancelled
```

**Effort:** 1 line. Critical correctness fix.

---

## PART 4 — DEFINITIVE IMPLEMENTATION PLAN

### Execution Order Rationale
- **Tier 1:** Fix confirmed bugs that currently cause wrong behavior
- **Tier 2:** Expand detection surface (what we scan)
- **Tier 3:** Harden how we scan (resilience)
- **Tier 4:** Optimize persistence (caching, TTL)

---

### TIER 1 — Bug Fixes (DO FIRST — currently causing wrong behavior)

#### Step 1: Fix CancellationException Swallowing
- **File:** `MetaDescriptionFetcher.kt`
- **Line:** 65
- **Change:** Add `catch (e: CancellationException) { throw e }` before the generic `catch (e: Exception)`
- **Why:** Without this, `withTimeout` in Phase 4 cannot cancel the fetch. Coroutine cooperative cancellation is broken.
- **Verify:** Write a test that calls `fetchMetaDescription` with `withTimeout(1)` — it should throw `TimeoutCancellationException`, not return `null`.
- **Risk:** None. Standard Kotlin coroutine practice.

#### Step 2: Fix Query-Param False Positives
- **File:** `CheckUrlForBlockedContentUseCase.kt`
- **Lines:** 18-25
- **Change:** Replace `lowerUrl.contains(keyword.lowercase())` with:
  ```kotlin
  val uri = try { java.net.URI(if (url.startsWith("http")) url else "https://$url") } catch (_: Exception) { null }
  val hostAndPath = ((uri?.host ?: "") + (uri?.path ?: "")).lowercase()
  for (keyword in keywords) {
      if (hostAndPath.contains(keyword.lowercase())) {
          return BlockCheckResult.Blocked(keyword, BlockReason.KEYWORD_MATCH)
      }
  }
  ```
- **Why:** `google.com/search?q=casino` must NOT block Google. Only host+path should be checked.
- **Verify:** Unit test: `invoke("google.com/search?q=casino", listOf("casino"), emptyList())` returns `Allowed`.
- **Risk:** Sites with keyword in query param only will no longer be caught at URL level. This is correct — SmartScan catches them at content level.

#### Step 3: Fix Fail-Open in SmartScanUseCase
- **File:** `SmartScanUseCase.kt`
- **Line:** 28
- **Change:** `null` from fetcher must NOT silently return `false`. Instead, it should return a result indicating fetch failure so the caller can decide policy.
- **Depends on:** Step 6 (ScanResult sealed class). Implement Step 6 first, then update this.
- **Verify:** Test: mock fetcher to return null → result is `SmartScanResult(isBlocked = false, fetchFailed = true)`.

#### Step 4: Fix SmartScan Race Condition
- **File:** `SmartScanUseCase.kt`
- **Line:** After line 28 (after fetch, before keyword loop)
- **Change:** Add `coroutineContext.ensureActive()` to check if the calling coroutine was cancelled during fetch.
- **Verify:** Launch scan, cancel the job mid-flight → no domain persisted to Room.
- **Risk:** None.

#### Step 5: Fix Overlay Match Info
- **File:** `FocusAccessibilityService.kt`
- **Line:** 217
- **Change:** Replace hardcoded `"Smart Scan Match"` with the actual matched keyword from `SmartScanResult`.
- **Depends on:** Step 6.
- **Verify:** Trigger SmartScan block → overlay shows "Blocked: 'casino' found in meta description".

---

### TIER 2 — Expand Detection Surface (WHAT we scan)

#### Step 6: Introduce SmartScanResult Sealed Class
- **New file:** `domain/model/SmartScanResult.kt`
- **Content:**
  ```kotlin
  sealed class SmartScanResult {
      data class Blocked(val keyword: String, val signal: String) : SmartScanResult()
      object Clean : SmartScanResult()
      data class FetchFailed(val reason: String) : SmartScanResult()
  }
  ```
- **Why:** Replaces the current `Boolean` return, enabling fail-closed policy and rich overlay info.
- **Files updated:** `SmartScanUseCase.kt` (return type), `FocusAccessibilityService.kt` (handle result), `BlockingServiceEntryPoint.kt` (unchanged — SmartScanUseCase is already exposed).
- **Verify:** Compile succeeds. All 3 branches handled in `handleBrowserEvent`.

#### Step 7: Add Jsoup Dependency
- **File:** `app/build.gradle.kts`
- **Change:** Add `implementation("org.jsoup:jsoup:1.17.2")` to dependencies block.
- **Why:** All subsequent head/body parsing depends on this.
- **Verify:** `./gradlew app:dependencies | grep jsoup` shows the artifact.

#### Step 8: Multi-Signal Extraction (Phase 2)
- **File:** `MetaDescriptionFetcher.kt`
- **Change:** Replace `extractDescriptionFromHtml()` with Jsoup-based extraction:
  ```kotlin
  fun extractSignals(html: String): Map<String, String> {
      val doc = Jsoup.parse(html)
      return mapOf(
          "meta_description" to (doc.select("meta[name=description]").attr("content")),
          "meta_keywords" to (doc.select("meta[name=keywords]").attr("content")),
          "og_title" to (doc.select("meta[property=og:title]").attr("content")),
          "og_description" to (doc.select("meta[property=og:description]").attr("content")),
          "twitter_description" to (doc.select("meta[name=twitter:description]").attr("content")),
          "page_title" to (doc.selectFirst("title")?.text().orEmpty()),
          "first_h1" to (doc.selectFirst("h1")?.text().orEmpty())
      ).filterValues { it.isNotBlank() }
  }
  ```
- **Return type change:** `fetchMetaDescription` → `fetchPageSignals` returning `Map<String, String>?` instead of `String?`.
- **Files updated:** `SmartScanUseCase.kt` (iterate over signal map values), `MetaDescriptionFetcher.kt`.
- **Verify:** Unit test with HTML containing only `<title>Casino Bonus</title>` → signals map has `"page_title" to "Casino Bonus"`.

#### Step 9: Body Text Scan (Option B)
- **File:** `MetaDescriptionFetcher.kt`
- **Change:** After extracting head signals, also extract first 2000 chars of `doc.body()?.text()`:
  ```kotlin
  "body_text" to (doc.body()?.text()?.take(2000).orEmpty())
  ```
- **Buffer size:** Keep current 8KB read (`CharArray(8192)`). Jsoup parses partial HTML gracefully — missing closing tags are auto-closed. For body text beyond 8KB, add a second `reader.read(charBuffer)` call (total 16KB).
- **Verify:** HTML with empty meta but `<body><p>Welcome to the best casino</p></body>` → signals map has `"body_text"` containing "casino".

---

### TIER 3 — Harden Resilience (HOW we scan)

#### Step 10: Fail-Closed Retry Logic (Phase 4)
- **File:** `MetaDescriptionFetcher.kt`
- **Change:** Wrap fetch in retry logic:
  ```kotlin
  suspend fun fetchPageSignals(urlStr: String): FetchOutcome = withContext(Dispatchers.IO) {
      // ... cache check ...
      var lastException: Exception? = null
      repeat(2) { attempt ->
          try {
              return@withContext withTimeout(4000L) {
                  // ... existing fetch + Jsoup parse ...
                  FetchOutcome.Success(signals)
              }
          } catch (e: CancellationException) { throw e }
          catch (e: Exception) {
              lastException = e
              if (attempt == 0) delay(500)
          }
      }
      FetchOutcome.Failed(lastException?.message ?: "Unknown")
  }
  ```
- **New sealed class:** `FetchOutcome { Success(signals), Failed(reason) }`
- **Policy in SmartScanUseCase:**
  - `Success` + keyword match → `SmartScanResult.Blocked`
  - `Success` + no match → `SmartScanResult.Clean`
  - `Failed` → `SmartScanResult.FetchFailed` → caller blocks for 15 minutes
- **Verify:** Mock `URL.openConnection()` to throw `SocketTimeoutException` → result is `FetchFailed` after ~8.5 seconds.

#### Step 11: Normalized Keyword Matching (Phase 3 — corrected)
- **New file:** `domain/usecase/NormalizedMatcher.kt`
- **Content:**
  ```kotlin
  object NormalizedMatcher {
      private val LEET_MAP = mapOf('0' to 'o', '4' to 'a', '5' to 's', '@' to 'a', '$' to 's')
      // Intentionally EXCLUDE '1'→'i' and '3'→'e' (false positives: mp3→mpe, win32→wini2)

      fun normalize(input: String): String {
          val nfd = java.text.Normalizer.normalize(input.lowercase(), java.text.Normalizer.Form.NFD)
          return buildString {
              for (c in nfd) {
                  if (c.code > 127) continue  // strip combining marks + non-ASCII
                  val mapped = LEET_MAP[c] ?: c
                  if (mapped in setOf('-', '_', '.', '+')) continue  // strip separators
                  append(mapped)
              }
          }.replace(Regex("\\s+"), " ").trim()
      }

      fun matches(keyword: String, content: String): Boolean {
          val nk = normalize(keyword)
          val nc = normalize(content)
          return if (nk.length <= 4) {
              Regex("""\b${Regex.escape(nk)}\b""").containsMatchIn(nc)
          } else {
              nc.contains(nk)
          }
      }
  }
  ```
- **Why `\b` is now safe:** After `normalize()`, both keyword and content are pure ASCII lowercase. `\b` works correctly on ASCII.
- **Files updated:** `SmartScanUseCase.kt` (replace `descriptionLower.contains(kw)` with `NormalizedMatcher.matches(kw, signalValue)`), `CheckUrlForBlockedContentUseCase.kt` (replace `hostAndPath.contains(keyword.lowercase())` with `NormalizedMatcher.matches(keyword, hostAndPath)`).
- **Verify:** `matches("porn", "p0rn site")` → true. `matches("ass", "class")` → false. `matches("casino", "c@sino bonus")` → true.

---

### TIER 4 — Persistence & Cache (Prevent redundant work)

#### Step 12: Add TTL Columns to Existing Table (Phase 5 — corrected)
- **File:** `BlockedDomainEntity.kt`
- **Change:** Add 2 columns:
  ```kotlin
  @ColumnInfo(name = "block_status", defaultValue = "PERMANENT")
  val blockStatus: String = "PERMANENT",  // PERMANENT, TEMPORARY, CLEAN_CACHE

  @ColumnInfo(name = "expires_at")
  val expiresAt: Long? = null
  ```
- **File:** `FocusDatabase.kt`
- **Change:** Bump `version = 6`, add `AutoMigration(from = 5, to = 6)`.
- **File:** `BlockedDomainDao.kt`
- **Change:** Add queries:
  ```kotlin
  @Query("SELECT * FROM blocked_domains WHERE domain = :domain AND (expires_at IS NULL OR expires_at > :now)")
  suspend fun findActiveDomain(domain: String, now: Long = System.currentTimeMillis()): BlockedDomainEntity?

  @Query("DELETE FROM blocked_domains WHERE expires_at IS NOT NULL AND expires_at < :now")
  suspend fun deleteExpiredEntries(now: Long = System.currentTimeMillis())
  ```
- **Why not a new table:** `blocked_domains` already has unique domain index, category FK, and auto-block tracking. Adding 2 columns to this table is simpler, avoids data duplication, and requires no JOIN changes.
- **Verify:** Insert domain with `expiresAt = now - 1` → `findActiveDomain` returns null.

#### Step 13: Integrate Domain Cache into Scan Flow
- **File:** `SmartScanUseCase.kt`
- **Change:** Before fetching, check `BlockedDomainDao.findActiveDomain(domain)`:
  - If found with `PERMANENT` or unexpired `TEMPORARY` → return `Blocked` immediately (no fetch)
  - If found with unexpired `CLEAN_CACHE` → return `Clean` immediately (no fetch)
  - If not found or expired → proceed with fetch
- After fetch:
  - `Blocked` → persist with `blockStatus = "PERMANENT"`
  - `Clean` → persist with `blockStatus = "CLEAN_CACHE"`, `expiresAt = now + 24h`
  - `FetchFailed` → persist with `blockStatus = "TEMPORARY"`, `expiresAt = now + 15min`
- **Verify:** Scan `casino.com/page1` → blocked → visit `casino.com/page2` → blocked with ZERO network fetch.

#### Step 14: Synonym Expansion for SmartScan Only
- **New file:** `assets/keyword_synonyms.json`
- **Content:**
  ```json
  {
    "casino":   ["gambling","betting","poker","slots","wagering","blackjack","roulette"],
    "porn":     ["xxx","adult","nsfw","explicit","erotic","hentai"],
    "gambling": ["casino","betting","wagering","odds","wager"],
    "drugs":    ["narcotics","cocaine","marijuana","weed","meth"],
    "alcohol":  ["beer","wine","liquor","vodka","whiskey","drunk"]
  }
  ```
- **Injection point:** `SmartScanUseCase.invoke()` — when iterating `activeKeywords`, also check all synonyms for each keyword.
- **NOT injected into:** `GetActiveKeywordsUseCase` or `CheckUrlForBlockedContentUseCase` — synonyms only apply to content scanning, not URL pre-check (where "poker.com" in the URL should be caught by the user adding "poker" as a keyword, not by synonym expansion from "casino").
- **Verify:** User has keyword "casino" → site with meta description "Best betting odds" → SmartScan catches "betting" as synonym → blocked.

---

## PART 5 — RISK REGISTER

| Step | Risk | Mitigation |
|---|---|---|
| 2 (query-param fix) | Sites with keyword ONLY in query params escape URL check | SmartScan (content check) catches them at Phase 2 |
| 7 (Jsoup) | APK size increase | Jsoup JAR is ~400KB — acceptable |
| 9 (body text) | Reading 16KB per site on mobile data | ConcurrentHashMap cache prevents re-reads; 16KB ≈ 1 second on 3G |
| 10 (retry) | Fetch hangs for 8.5s before decision | This is the correct tradeoff — 8.5s is acceptable for a background task |
| 10 (15-min temp block) | Legitimate site temporarily blocked on bad network | 15 min is recoverable; user can add to whitelist if needed (future feature) |
| 11 (normalization) | `0→o` turns "101" into "ioi" | NOT a problem — we EXCLUDED `1→i`. "101" becomes "10o" which matches nothing. |
| 12 (migration v5→v6) | Room AutoMigration fails if columns lack defaults | Both new columns have `defaultValue` specified — AutoMigration handles this |
| 14 (synonyms) | Synonym list maintenance burden | JSON is bundled with APK updates; small, static, version-controlled |

---

## PART 6 — DEPENDENCY GRAPH

```
Step 1 (CancellationException fix)     ← No deps, do first
Step 2 (Query-param fix)               ← No deps, do first
Step 4 (Race condition fix)            ← Depends on Step 6
Step 6 (SmartScanResult sealed class)  ← No deps, do early
Step 3 (Fail-open fix)                 ← Depends on Step 6
Step 5 (Overlay match info)            ← Depends on Step 6
Step 7 (Jsoup dependency)              ← No deps
Step 8 (Multi-signal extraction)       ← Depends on Step 7
Step 9 (Body text scan)                ← Depends on Step 8
Step 10 (Fail-closed retry)            ← Depends on Steps 1, 6, 8
Step 11 (Normalized matching)          ← No deps
Step 12 (TTL columns)                  ← No deps
Step 13 (Domain cache integration)     ← Depends on Steps 6, 10, 12
Step 14 (Synonym expansion)            ← No deps
```

**Critical path:** 1 → 6 → 7 → 8 → 10 → 13

---

## PART 7 — FILES TOUCHED (COMPLETE LIST)

| File | Steps |
|---|---|
| `domain/usecase/MetaDescriptionFetcher.kt` | 1, 8, 9, 10 |
| `domain/usecase/CheckUrlForBlockedContentUseCase.kt` | 2, 11 |
| `domain/usecase/SmartScanUseCase.kt` | 3, 4, 5, 11, 13, 14 |
| `service/accessibility/FocusAccessibilityService.kt` | 5, 13 |
| `domain/model/SmartScanResult.kt` | 6 (NEW) |
| `domain/usecase/NormalizedMatcher.kt` | 11 (NEW) |
| `data/local/entity/BlockedDomainEntity.kt` | 12 |
| `data/local/FocusDatabase.kt` | 12 |
| `data/local/dao/BlockedDomainDao.kt` | 12, 13 |
| `data/repository/BlockingRepositoryImpl.kt` | 13, 14 |
| `domain/repository/BlockingRepository.kt` | 13 |
| `app/build.gradle.kts` | 7 |
| `assets/keyword_synonyms.json` | 14 (NEW) |

---

*Plan complete. Every step traceable to code. No assumptions. No patchwork.*
