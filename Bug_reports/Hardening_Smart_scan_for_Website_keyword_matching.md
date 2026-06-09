Here is how the system actually works behind the scenes, broken down into logical steps:
1. The Silent Observer (Screen Monitoring) The app constantly watches what is happening on the screen. Whenever the user opens a supported web browser and types in an address or clicks a link, the app captures the text inside the URL bar. It immediately runs a quick check to make sure it captured a real website address and not just a Google search query or a page title.
2. The Fast Lane (Local Database Check) Before doing any heavy lifting, the app checks the captured website against a local database stored directly on the phone. This database contains a massive list of known bad websites. If the website is on this list, the app instantly throws up a blocking screen. This happens in milliseconds, completely offline.
3. The Deep Scan (Live Keyword Detection) If the website is not on the local blocklist, the app kicks off a background "smart scan."

* It silently visits that exact website in the background.
* It doesn't load the images or videos; instead, it looks strictly at the hidden HTML "meta tags" built into the webpage (the short descriptions developers put in the code for search engines like Google).
* It pulls that description text, converts it all to lowercase, and scans it against the user's custom list of "bad keywords" (like "casino" or "porn").
4. The Punishment (Auto-Blocking) If the background scan finds even one of the forbidden keywords hidden in the website's description, it does two things:

1. It immediately blocks the user's current screen.
2. It permanently adds that specific website to the phone's local database under an "auto-blocked" category.
5. The Memory Effect Because the site was added to the local database in Step 4, the app never has to run the slow "Deep Scan" for that website again. The next time the user tries to visit it, the app catches it in Step 2 (The Fast Lane) and blocks it instantly.
This hybrid approach ensures that known sites are blocked immediately without draining battery, while unknown sites are still caught dynamically based on their actual content.

## after analyzing this how can we make this feature more harden and powerfull 

# findings:- 
Root Causes First
Before solutions, here's why it's likely failing silently right now:

Silent fetch failure — if the background HTTP request times out or gets blocked, the code probably returns null and proceeds without blocking
Meta description is empty or absent — many sites don't have <meta name="description"> at all
Scanning too narrow a surface — a casino or adult site might have an innocent-sounding meta description intentionally
No domain-level memory — you block site.com/page1 but site.com/page2 gets a fresh scan

# suggested solution ;-

# HARDENED_BLOCKER_PROMPT.md
# Feature: Hardened Content Blocking — Smart Scan Reinforcement
# App: FocusLock (Android)
# Total Phases: 5

---

## CONTEXT (Read before every phase)

This prompt file is for hardening the existing "Smart Scan" content blocking feature
of the FocusLock Android app. The feature monitors URLs opened in browsers using
an Accessibility Service, checks them against a local Room database blocklist, and
if not found, runs a background deep scan of the webpage to detect forbidden keywords
in the page's meta description.

The current implementation has a known gap: websites are sometimes NOT blocked even
when their content contains a forbidden keyword. The root causes are:

- Scanning only `<meta name="description">` — too narrow, often empty
- Silent fetch failures that allow pages through without blocking
- No normalization — obfuscated or accented keywords bypass matching
- No domain-level memory — same domain gets re-scanned for every new page path
- No URL-level pre-check — keywords visible in the URL itself are ignored

These five phases harden each gap one by one. Each phase is fully self-contained.
Do not mix phases. Complete and test each phase before starting the next.

---

---

## PHASE 1 — URL Pre-Check Before Any Fetch

### GOAL
Catch websites where the forbidden keyword is visible directly in the URL itself —
in the domain name, path segments, or query parameters — before wasting time on
any background fetch. This is the fastest possible block.

### WHAT TO BUILD
Create a new utility function that takes the raw captured URL string and the user's
list of forbidden keywords as inputs. The function must do the following:

1. URL-decode the raw string (handle `%20`, `%2F`, etc.) so encoded characters
   become readable text before any comparison.
2. Extract only the host (domain) and the path from the URL. Ignore the scheme
   (`https://`) and query parameters that look like session tokens or tracking IDs.
3. Clean the extracted host and path into a plain readable sentence by replacing
   all forward slashes, hyphens, underscores, and dots with single spaces.
4. Run keyword matching against this cleaned string using simple lowercase
   substring containment — no word boundaries needed for URL checking, since
   domain names like `casinobonus.com` should match the keyword `casino`.
5. Return `true` (block) if any keyword matches, `false` (allow) otherwise.

### WHERE TO PLUG IT IN
This function must be called inside the Accessibility Service callback, immediately
after the URL is captured and validated as a real URL, but BEFORE the local
Room database check. If this function returns `true`, show the block screen
immediately and do not proceed to any further checks.

### ACCEPTANCE CRITERIA
- URL `https://www.casino-free-spins.com/offers` with keyword `casino` → BLOCKED
- URL `https://www.google.com/search?q=casino+games` → NOT blocked (query param
  ignored, domain and path have no match)
- URL `https://news.com/article-about-casino-regulation` with keyword `casino`
  → BLOCKED (path contains the word)
- Function must not crash on malformed URLs — wrap parsing in a try-catch and
  return `false` on any parse exception.

---

---

## PHASE 2 — Expand the Page Signal Surface

### GOAL
Replace the current single `<meta name="description">` extraction with a
multi-signal extraction that gathers text from every meaningful part of the
page's head section. A site that hides its real purpose in its meta description
will almost certainly reveal it in at least one of the other signals.

### WHAT TO BUILD
Refactor the existing HTML parsing logic inside the background deep scan. Instead
of extracting only `<meta name="description">`, extract ALL of the following and
store them as separate string fields:

| Signal | HTML selector |
|---|---|
| Meta description | `<meta name="description" content="...">` |
| Meta keywords | `<meta name="keywords" content="...">` |
| OG title | `<meta property="og:title" content="...">` |
| OG description | `<meta property="og:description" content="...">` |
| Twitter description | `<meta name="twitter:description" content="...">` |
| Page title | `<title>` tag text |
| First H1 | First `<h1>` tag text |

After extracting all seven fields, concatenate them into a single long string with
a space between each field. Convert the full concatenated string to lowercase.
This combined string is the input to keyword matching — not just the meta description.

Use Jsoup for parsing. Do not use regex to extract these tags. If any individual
field is missing or empty, treat it as an empty string and continue — never throw
on a missing tag.

### WHERE TO PLUG IT IN
This is a drop-in replacement inside the existing deep scan background worker or
coroutine. The output of this phase (the combined string) replaces whatever
content string was previously being passed to the keyword matching step.

### ACCEPTANCE CRITERIA
- A page with an empty meta description but a revealing `og:title` is still caught
- A page with all empty meta tags but a revealing `<title>` is still caught
- A page with all signals empty returns an empty string — no crash, no block
- The combined string for a typical news article should be at least 50–200 chars

---

---

## PHASE 3 — Normalized Keyword Matching

### GOAL
Make keyword matching resistant to obfuscation. Users trying to circumvent the
blocker sometimes visit sites that substitute numbers for letters (l33t speak),
use accented characters, or insert separators inside words. Standard `.contains()`
will miss all of these.

### WHAT TO BUILD
Create a standalone text normalization function and a keyword match function that
uses it. The normalization function takes any string input and applies these
transformations in order:

1. Lowercase the entire string.
2. Decompose Unicode into base characters + combining marks (NFD normalization),
   then strip all non-ASCII characters. This converts `pörn → porn`,
   `cásino → casino`.
3. Replace these leet-speak digit substitutions:
   - `0` → `o`
   - `1` → `i`
   - `3` → `e`
   - `4` → `a`
   - `5` → `s`
   - `@` → `a`
   - `$` → `s`
4. Remove all hyphens, underscores, dots, and plus signs.
5. Collapse multiple consecutive spaces into a single space and trim.

The keyword match function takes a keyword string and a content string as inputs.
It normalizes BOTH before comparing. Then apply these matching rules:

- If the keyword is 4 characters or shorter: require a whole-word boundary match
  (use a regex with `\b` anchors). This prevents `ass` from matching `class` or
  `mass`.
- If the keyword is 5 characters or longer: use simple substring `.contains()`.
  This is intentional — `casino` inside `casinobonus` should match.

The function returns `true` if the keyword matches, `false` otherwise.

### WHERE TO PLUG IT IN
Replace every existing `.contains(keyword)` or `.lowercase().contains()` call
in the codebase with a call to this new match function. This includes the URL
pre-check from Phase 1 and the combined signal string check from Phase 2.

### ACCEPTANCE CRITERIA
- `p0rn` matches keyword `porn` → BLOCKED
- `c@sino` matches keyword `casino` → BLOCKED
- `pörn` matches keyword `porn` → BLOCKED
- `class` does NOT match keyword `ass` (short keyword, word boundary enforced)
- `casinobonus` DOES match keyword `casino` (long keyword, substring allowed)
- `gambling` matches keyword `gambling` (baseline, must still work)
- Both inputs are independently normalized — do not assume pre-normalized input

---

---

## PHASE 4 — Fail-Closed Fetch with Retry

### GOAL
Eliminate silent fetch failures. Right now if the background HTTP request times
out, throws an exception, or returns an empty body, the code most likely exits
quietly and lets the page through. This is the most dangerous gap. The fix is a
"fail-closed" policy: if we cannot conclusively confirm a page is clean, we
treat it as suspect.

### WHAT TO BUILD
Wrap the existing HTTP fetch call in a retry-with-timeout manager. The rules are:

**Retry logic:**
- Attempt the fetch up to 2 times total.
- Each individual attempt has a hard timeout of 4 seconds. Use coroutine
  `withTimeout` — do not rely on OkHttp connection/read timeouts alone.
- If attempt 1 fails for any reason (timeout, IOException, HTTP error code),
  wait 500ms then try attempt 2.
- If attempt 2 also fails, do not try again — move to the failure handling below.

**Result classification:**
Classify every scan outcome into one of these explicit result types (use a sealed
class or enum):
- `BLOCKED` — fetch succeeded and a keyword was found in the signals
- `CLEAN` — fetch succeeded and no keyword was found
- `FETCH_TIMEOUT` — all attempts exceeded the 4-second timeout
- `FETCH_EMPTY` — fetch returned HTTP 200 but the body was blank or under 50 chars
- `FETCH_ERROR` — any other exception (IOException, SSL error, etc.)

**Failure handling policy:**
Handle each result type as follows:
- `BLOCKED` → block the user and persist to local DB (existing behavior)
- `CLEAN` → allow the page and cache the domain as recently-scanned-clean
  with a 24-hour TTL so it is not re-scanned within that window
- `FETCH_TIMEOUT` → show the block screen. Add the domain to a
  "temporarily blocked" category in the local DB with a 1-hour TTL. After 1 hour
  it expires and will be re-scanned if visited again.
- `FETCH_EMPTY` → same treatment as `FETCH_TIMEOUT`
- `FETCH_ERROR` → log the error with the URL and error message for debugging.
  Do NOT block — allow the page but do not cache it, so it will be re-scanned
  next visit. The reasoning: `FETCH_ERROR` often means a network problem on
  the device, not a suspicious site.

### WHERE TO PLUG IT IN
This fully replaces the existing fetch + keyword check logic in the background
deep scan worker or coroutine. The retry manager is the outermost wrapper; the
Phase 2 signal extraction and Phase 3 keyword matching happen inside it after a
successful fetch.

### ACCEPTANCE CRITERIA
- A fetch that hangs for 5 seconds → user sees block screen within ~8.5 seconds
  (4s attempt 1 + 0.5s wait + 4s attempt 2)
- A fetch that throws an SSL exception → page is allowed but not cached
- A fetch returning an empty body → domain is temporarily blocked for 1 hour
- A fetch returning a page with a matching keyword → domain is permanently blocked
- No scenario should result in the scan function completing without returning
  one of the five result types — no null returns, no unhandled exceptions

---

---

## PHASE 5 — Domain-Level Blocking and Cache

### GOAL
Right now the app persists the full URL when blocking (e.g.
`casino.com/free-spins/offer-2024`). This means `casino.com/promotions` gets
a completely fresh scan on the next visit. Once any page on a domain is caught,
every page on that domain should be blocked instantly at the Fast Lane level —
no further fetches, ever.

### WHAT TO BUILD

**Part A — Upgrade the local DB schema:**
Add a `domains` table to the existing Room database with these columns:
- `domain` (TEXT, PRIMARY KEY) — the bare domain without `www.`, scheme, or path
- `block_status` (TEXT) — one of: `PERMANENT`, `TEMPORARY`, `CLEAN_CACHE`
- `trigger_keyword` (TEXT, nullable) — the keyword that caused the block, if any
- `source` (TEXT) — how it was added: `AUTO_DEEP_SCAN`, `URL_PRE_CHECK`,
  `MANUAL`, `TEMP_FETCH_FAILURE`
- `expires_at` (INTEGER, nullable) — Unix timestamp in ms; null means never expires.
  Used only for `TEMPORARY` and `CLEAN_CACHE` entries.
- `created_at` (INTEGER) — Unix timestamp in ms

**Part B — Domain extraction utility:**
Create a utility function that takes any URL string and returns the bare domain —
strip `www.`, `m.`, `mobile.` prefixes, and remove the scheme and path. For
example: `https://www.casino.com/page?ref=1` → `casino.com`. Return null on
parse failure.

**Part C — Upgrade the Fast Lane check:**
The existing Fast Lane check currently queries by full URL. Upgrade it so it
checks the `domains` table first before checking any URL-level table. If the
domain is found with status `PERMANENT` → block immediately. If `TEMPORARY` and
not yet expired → block immediately. If `TEMPORARY` but expired → delete the
entry and allow the scan to proceed. If `CLEAN_CACHE` and not expired → skip
the deep scan entirely and allow. If `CLEAN_CACHE` but expired → delete and
allow the scan to proceed.

**Part D — Upgrade block persistence:**
Whenever a block is triggered (from Phase 1 URL pre-check, or from a
`BLOCKED` or `FETCH_TIMEOUT` deep scan result), persist to the `domains` table,
not just the URL table. Use the domain extracted from the blocked URL. Set
`block_status` to `PERMANENT` for keyword-matched blocks, `TEMPORARY` (1-hour
TTL) for fetch-failure blocks.

**Part E — Upgrade clean caching:**
When the deep scan returns `CLEAN`, write the domain to the `domains` table
with `block_status = CLEAN_CACHE` and `expires_at = now + 24 hours`. This
prevents repeat scanning of known-clean sites within the same day.

### WHERE TO PLUG IT IN
- Fast Lane check: modify the existing Accessibility Service URL check method
- Block persistence: modify the existing block-and-save method called after
  every block event
- Clean caching: add a save call at the end of the deep scan flow when result
  is `CLEAN`
- Run a Room migration to add the new `domains` table — do not drop existing tables

### ACCEPTANCE CRITERIA
- `casino.com/page1` triggers a keyword block → `casino.com/page2` is blocked
  at the Fast Lane with zero fetch
- `suspicious.com` causes a fetch timeout → blocked for 1 hour, then re-scanned
- `legit-news.com` scans clean → not re-scanned for 24 hours on subsequent visits
- `www.casino.com` and `casino.com` and `m.casino.com` all resolve to `casino.com`
  and share the same domain block entry
- Expired temporary entries are cleaned up on read — no background cleanup job needed

---

## PHASE EXECUTION ORDER

| Phase | What it fixes | Can run standalone |
|---|---|---|
| 1 | URL contains keyword — no fetch needed | Yes |
| 2 | Meta description is empty or lying | Yes |
| 3 | Obfuscated / accented keywords bypass match | Yes |
| 4 | Fetch failure silently lets page through | Needs Phase 2+3 |
| 5 | Same domain re-scanned on every new path | Needs Phase 4 |

Run phases in order 1 → 2 → 3 → 4 → 5. Each phase should be committed
and smoke-tested before the next begins.

---

_End of HARDENED_BLOCKER_PROMPT.md_