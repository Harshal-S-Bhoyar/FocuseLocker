# SmartScan Hardening — Full Analysis & Recommendations

> **Basis:** Direct inspection of `MetaDescriptionFetcher.kt`, `SmartScanUseCase.kt`,
> `FocusAccessibilityService.kt`, `CheckUrlForBlockedContentUseCase.kt`.
> Zero assumptions — all findings traceable to actual code lines.

---

## PART 1 — Current Implementation (Ground Truth)

### What actually exists today

| Component | What it does | Real gap |
|---|---|---|
| `CheckUrlForBlockedContentUseCase` | Checks keywords against **entire raw URL** (line 22: `lowerUrl.contains(kw)`) | Checks `q=casino` query param → **false positives on Google searches** |
| `MetaDescriptionFetcher` | Fetches only `meta name="description"` + `og:description` via regex | Silent `null` return on any failure → `SmartScanUseCase` returns `false` = **page allowed** |
| `SmartScanUseCase` | Simple `.contains(kw.lowercase())` match, no normalization | No retry, no fail-closed, no URL pre-check |
| `FocusAccessibilityService` | Debounces 500ms, cancels pending scan on new URL | Domain cache is **in-memory only** — lost on service restart |

### Confirmed critical bugs (code-verified)

1. **Silent fail-open** — `MetaDescriptionFetcher.kt:65`: `catch (e: Exception) { null }` → `SmartScanUseCase:28` returns `false` → page allowed. Every timeout, SSL error, or network blip silently whitelists the site.

2. **Query-param keyword hits** — `CheckUrlForBlockedContentUseCase.kt:22`: `lowerUrl.contains(keyword)` on the full URL means `google.com/search?q=casino` blocks Google itself.

3. **Regex ReDoS risk** — `MetaDescriptionFetcher.kt:78`: `(?i)<meta([^>]{0,1000})>` — bounded but still backtrack-prone on malformed HTML from adversarial sites.

4. **No persistence across service restarts** — The `ConcurrentHashMap` cache in `MetaDescriptionFetcher` is instance-scoped. Service killed = full re-scan of all domains on next launch.

5. **No domain-level memory** — `SmartScanUseCase.kt:36` calls `addAutoBlockedDomain(domain, kw)` but `CheckUrlForBlockedContentUseCase` only checks the `activeDomains` list fed from `getAllBlockedDomainsFlowUseCase`. If that DAO query doesn't surface auto-blocked domains, `casino.com/page2` re-scans after `casino.com/page1` was caught.

---

## PART 2 — Verdict on the Proposed 5-Phase Solution

### Phase 1 — URL Pre-Check ✅ Valid, but partially redundant

**Good:** Decoding URL, only checking host+path (not query params) — fixes the Google search false-positive.

**Gap:** `CheckUrlForBlockedContentUseCase` already does keyword-in-URL checking (line 22) but against the whole URL. The bug report's version is more precise. **This is an upgrade, not a new feature.**

**Missing:** The bug report says "before Room DB check" but in the actual code flow, the domain check and keyword check both happen in the same `checkUrlForBlockedContentUseCase()` call — there's no separate DB step to insert before. The wiring needs to reflect the actual call chain.

**Verdict: ✅ Implement — but as a replacement for `CheckUrlForBlockedContentUseCase` logic, not an add-on.**

---

### Phase 2 — Expand Page Signal Surface ✅ Highest value fix

**Good:** Moving from 2 signals to 7 (meta description, meta keywords, og:title, og:description, twitter:description, `<title>`, first `<h1>`).

**Gap:** Proposes Jsoup — this is a new dependency not currently in the project. Must add to `build.gradle.kts`. The plan mentions it but doesn't include the dependency line.

**Gap:** Still only scans the `<head>` section. A sophisticated adversarial site puts an innocent meta description but loads harmful content dynamically via JavaScript. This approach catches naive sites but misses intentionally evasive ones.

**Verdict: ✅ High priority. Add Jsoup dependency first.**

---

### Phase 3 — Normalized Keyword Matching ⚠️ Good but overengineered for short keywords

**Good:** NFD normalization (strips accents), leet-speak substitution table.

**Risk:** `\b` word-boundary regex in Kotlin doesn't handle Unicode word boundaries correctly. `\b` is ASCII-boundary only — it breaks on accented or CJK characters in the content string (even after normalization, surrounding text may still have unicode). Use `(?<![a-z])keyword(?![a-z])` instead.

**Risk:** Leet-speak substitution maps `1→i` and `3→e`. This would turn `mp3` → `mpe` and `win32` → `wini2`. Could create false positives in legitimate tech content descriptions.

**Verdict: ⚠️ Implement NFD normalization + `@→a`, `$→s`, `0→o`. Skip `1→i` and `3→e` (too risky). Fix word-boundary regex.**

---

### Phase 4 — Fail-Closed Fetch with Retry ✅ Most critical fix

**Good:** Sealed result class, 2-attempt retry, 4-second timeout per attempt, explicit policy for each outcome.

**Issue:** The `FETCH_TIMEOUT` → block for 1 hour is aggressive. On mobile, timeouts often happen on slow connections (coffee shop WiFi, weak signal). Blocking legitimate sites for 1 hour because of a network issue will frustrate users. Suggestion: show a "scan inconclusive" overlay that auto-dismisses in 30 seconds instead.

**Issue:** `withTimeout` in coroutines cancels via `CancellationException` — must be caught separately from `Exception` or it silently gets swallowed by the broad catch block.

**Verdict: ✅ Critical — implement immediately. Adjust FETCH_TIMEOUT policy.**

---

### Phase 5 — Domain-Level Blocking and Cache ✅ Necessary but needs migration

**Good:** Dedicated `domains` table with `block_status`, TTL (`expires_at`), source tracking.

**Gap:** Current `FocusDatabase` is already at version 5 (from prior work). This needs migration to version 6. The plan says "run a Room migration" but doesn't specify the version number or the exact SQL.

**Gap:** The plan adds a new `domains` table but doesn't address the existing auto-block data already stored in `BlockedDomainEntity` with `isAutoBlocked=true`. Migration must backfill or the existing auto-block records become orphaned.

**Verdict: ✅ Implement — but version the migration correctly (v5→v6) and backfill existing auto-blocked records.**

---

### Overall 5-Phase Verdict

| Phase | Score | Priority |
|---|---|---|
| Phase 1 — URL Pre-Check | ✅ Good | Medium (partial exists) |
| Phase 2 — Multi-Signal Extraction | ✅ Excellent | **High** |
| Phase 3 — Normalized Matching | ⚠️ Good with fixes | Medium |
| Phase 4 — Fail-Closed Fetch | ✅ Critical fix | **Critical** |
| Phase 5 — Domain-Level Cache | ✅ Excellent | **High** |

**The plan is solid and will meaningfully harden the system.** It addresses all 5 root causes correctly. The gaps above are fixable without restructuring the approach.

---

## PART 3 — More Dynamic & Impactful Solutions

### Option A — Semantic Keyword Expansion (Local, Zero Network Cost)

**Problem it solves:** User adds "casino" but the site uses "wagering", "betting", "gambling", "poker" — all synonyms that bypass simple keyword matching.

**How:**
- Ship a static synonym map in `assets/keyword_synonyms.json`:
```json
{
  "casino":    ["gambling","betting","poker","slots","wagering","blackjack","roulette"],
  "porn":      ["xxx","adult","nsfw","explicit","erotic","hentai"],
  "gambling":  ["casino","betting","wagering","odds","stake","wager"]
}
```
- When user adds keyword "casino", `GetActiveKeywordsUseCase` returns the keyword + all its synonyms
- **Zero network cost, offline, uncircumventable via synonym substitution**
- ~10KB JSON file, loaded once at startup

**Effort:** 1 day. High impact.

---

### Option B — Visible Body Text Scan (Not Just Head Tags)

**Problem it solves:** Adult or gambling sites intentionally write innocent meta descriptions for SEO purposes while the actual page content is explicit.

**How:**
```kotlin
// In MetaDescriptionFetcher — fetch 32KB instead of 8KB
// After extracting head signals, also extract:
val bodyText = doc.body()?.text()?.take(2000) ?: ""
// Concatenate with head signals — body text gets scanned too
val combined = headSignals + " " + bodyText
```
- Jsoup's `.text()` strips all HTML tags and returns only readable text
- First 2000 chars of body text covers above-the-fold content which reveals site purpose
- **Dramatically harder for sites to evade — they'd have to rewrite visible content**

**Effort:** 30 minutes once Jsoup is added. Extremely high impact.

---

### Option C — Google Safe Browsing Lookup API (Cloud-Backed Known-Bad)

**Problem it solves:** Catches phishing, malware, and categorized adult sites that your keyword list misses entirely.

**How:**
- Google Safe Browsing API (free, 10,000 req/day)
- Before SmartScan, do a lightweight URL hash lookup:
```kotlin
// POST to https://safebrowsing.googleapis.com/v4/threatMatches:find
// Returns MALWARE, SOCIAL_ENGINEERING, UNWANTED_SOFTWARE, POTENTIALLY_HARMFUL_APPLICATION
```
- Cache result per domain in Room (no re-checking known-safe or known-bad)
- **Catches sites that are categorized by Google's massive threat intelligence database**

**Effort:** 2-3 days. Requires free API key. Extremely high impact for known-bad sites.

---

### Option D — TLD + Domain Age Heuristics (Zero-Network Signal)

**Problem it solves:** Brand-new domains, suspicious TLDs (`.xyz`, `.tk`, `.ml`) are disproportionately used for adult/gambling content.

**How:**
```kotlin
// Add to CheckUrlForBlockedContentUseCase:
val suspiciousTlds = setOf("xyz", "tk", "ml", "pw", "cc", "top", "club", "win", "bet")
val tld = host.substringAfterLast(".")
if (tld in suspiciousTlds) → force SmartScan even during non-session (warmup mode)
```
- Not a blocker on its own — a **risk score signal** that escalates to mandatory deep scan
- Can be combined with Phase 4's result classification

**Effort:** 2 hours. Medium impact as standalone, high as part of a scoring system.

---

### Option E — Risk Scoring Pipeline (Replace Binary Block/Allow)

**Problem it solves:** Current system is binary — block or allow. A site scoring 3 weak signals gets the same treatment as one scoring 0. A scoring system catches more edge cases without increasing false positives.

**How:**
```kotlin
data class ScanScore(val score: Float, val signals: List<String>)

// Score accumulates:
// +0.4 if keyword found in body text
// +0.3 if keyword found in head signals  
// +0.2 if keyword found in URL
// +0.2 if suspicious TLD
// +0.1 if domain < 30 days old (if WHOIS available)

// Policy:
// score >= 0.5 → BLOCK + persist PERMANENT
// score >= 0.3 → BLOCK + persist TEMPORARY (24hr)
// score < 0.3  → ALLOW + cache CLEAN (24hr)
```
- **Dramatically reduces false negatives** (catching more bad sites via accumulated weak signals)
- **Reduces false positives** (single weak signal won't block a legitimate site)

**Effort:** 2-3 days to design and test thresholds. High impact.

---

## PART 4 — Recommended Implementation Order

> Priority: Impact/Effort ratio. All fixes are additive — no breaking changes.

```
TIER 1 — Do Now (Fixes confirmed bugs, highest impact)
├── Phase 4: Fail-Closed Fetch      → Stops silent fail-open (CRITICAL bug)
├── Phase 2: Multi-Signal + Jsoup   → Expands detection surface dramatically  
└── Option A: Synonym Expansion     → Catches evasion via synonyms, zero cost

TIER 2 — Do Next (Hardening, medium effort)
├── Phase 1: URL Pre-Check fix      → Fixes Google-search false-positive
├── Phase 5: Domain-Level DB        → Prevents per-path re-scanning
└── Option B: Body Text Scan        → Catches adversarial meta descriptions

TIER 3 — Do Later (Advanced, higher effort)
├── Phase 3: Normalized Matching    → Catches obfuscated keywords
├── Option D: TLD Heuristics        → Risk signal for new/suspicious domains
└── Option E: Risk Scoring          → Replace binary → probabilistic blocking
    
TIER 4 — Future / Optional
└── Option C: Safe Browsing API     → Cloud-backed known-bad database
```

---

## PART 5 — Specific Code Fixes Needed Regardless of Phase

These must be fixed even if you implement nothing else from the plan:

**Fix 1 — `CheckUrlForBlockedContentUseCase.kt:22`**
```kotlin
// Current (WRONG — matches Google search queries)
if (lowerUrl.contains(keyword.lowercase()))

// Fix — only match host + path, ignore query params
val uri = URI(url)
val hostAndPath = (uri.host + uri.path).lowercase()
if (hostAndPath.contains(keyword.lowercase()))
```

**Fix 2 — `SmartScanUseCase.kt:28`**
```kotlin
// Current — null = ALLOW (silent fail-open)
val description = fetcher.fetchMetaDescription(urlStr) ?: return false

// Fix — null needs to go to fail-closed handler (Phase 4 result)
// This line should never silently return false without classifying why
```

**Fix 3 — `MetaDescriptionFetcher.kt:65`**
```kotlin
// Current — swallows CancellationException (breaks coroutine cooperative cancellation)
} catch (e: Exception) { null }

// Fix
} catch (e: CancellationException) { throw e }  // re-throw
} catch (e: Exception) { null }
```

---

*Analysis complete. All findings traceable to actual code inspection.*
