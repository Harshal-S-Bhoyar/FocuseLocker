# STEVENBLACK_LIST_PROMPT.md
# Feature: Offline Domain Blocklist (StevenBlack)
# App: FocusLock (Android)
# Language: Kotlin
# Total Phases: 2 (Phase 1 ALREADY DONE)

---

## CONTEXT

FocusLock blocks websites across multiple user-defined categories. This module
handles downloading and storing pre-curated domain blocklists from the StevenBlack
GitHub repository. These lists cover adult, gambling, social media, fake news, and
general ad/tracking domains. Once stored in Room DB, blocked domains are caught
instantly at the Fast Lane with zero network calls — no fetch, no scan.

This module runs once on first app launch and repeats weekly in the background
via WorkManager.

### Existing Codebase (DO NOT RECREATE)

The following already exist and must be reused as-is:

- **Entity**: `BlockedDomainEntity` in `data/local/entity/BlockedDomainEntity.kt`
  - Table: `blocked_domains`
  - Columns: `id` (PK auto), `domain` (unique index), `category_key` (FK → blocked_categories),
    `is_auto_blocked`, `matched_keyword`, `auto_blocked_at`, `block_status` (PERMANENT/TEMPORARY/CLEAN_CACHE),
    `expires_at`
- **DAO**: `BlockedDomainDao` in `data/local/dao/BlockedDomainDao.kt`
  - Has: `insertDomains(List<BlockedDomainEntity>)` with IGNORE strategy,
    `upsertDomain()` with REPLACE, `findActiveDomain()`, `deleteExpiredEntries()`
- **Database**: `FocusDatabase` at version 6, entity already registered
- **Repository**: `BlockingRepositoryImpl` — already has `loadDomainsFromAssets()` that
  reads `blocked_domains.json` and inserts in 500-row batches
- **Category FK**: `blocked_domains.category_key` → `blocked_categories.category_key`.
  Categories are seeded in `BlockingRepositoryImpl.seedDomainDataIfNeeded()` using
  `CategoryKey.ALL`. Any new category keys (e.g. `STEVENBLACK_ADULT`) must be
  added to `CategoryKey.ALL` and seeded first.

---

---

## PHASE 1 — Room DB Schema for Domains [ALREADY DONE — SKIP]

The `blocked_domains` table and `BlockedDomainDao` already exist with all needed
columns. The only minor addition needed is a `source` column for audit tracking,
but this is LOW PRIORITY and optional for Phase 2 to work.

**DO NOT create a new entity or table. Use `BlockedDomainEntity` and
`BlockedDomainDao` directly.**

---

---

## PHASE 2 — StevenBlack List Download and Sync [TODO]

### GOAL
Download the StevenBlack amalgamated domain lists for each active category,
parse them, and bulk-insert into the existing `blocked_domains` table using
`BlockedDomainDao.insertDomains()`. Run on first launch and weekly via WorkManager.

### WHAT TO BUILD

**Part A — List URLs per category:**
Map each app category to its StevenBlack raw GitHub URL:

| App Category | StevenBlack URL |
|---|---|
| Adult | `https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts` |
| Gambling | `https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/gambling.txt` |
| Social Media | `https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling-porn-social/hosts` |
| Fake News | `https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews/hosts` |
| General (ads/tracking) | `https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts` |

Store these mappings in a constant object. Only download lists for categories
the user has enabled — query `BlockedCategoryDao.getActiveCategoryKeys()` via
`BlockingRepository.getActiveCategoryKeys()`.

**Part B — Parser:**
Create a function `parseHostsFile(raw: String, categoryKey: String): List<BlockedDomainEntity>`
that:

1. Splits the raw text by newline.
2. Skips lines starting with `#` (comments) or blank lines.
3. For hosts file format lines like `0.0.0.0 casino.com` — extract the second
   token (the domain). Skip lines where the domain is `0.0.0.0` or `localhost`.
4. For plain domain list format (one domain per line, no prefix) — use the
   line directly.
5. Strip `www.` prefix from every domain.
6. Build a `BlockedDomainEntity` for each domain with:
   - `domain` = the stripped domain
   - `categoryKey` = the passed categoryKey string (e.g. `ADULT`, `GAMBLING`)
   - `isAutoBlocked = false` (these are list-sourced, not auto-blocked)
   - `matchedKeyword = null`
   - `autoBlockedAt = null`
   - `blockStatus = "PERMANENT"`
   - `expiresAt = null`
7. Return the list. Skip malformed lines silently.

**IMPORTANT**: The `category_key` you pass MUST exist in the `blocked_categories`
table due to the FK constraint. Use keys from `CategoryKey.ALL`. If you need new
keys, add them there and seed them in `BlockingRepositoryImpl.seedDomainDataIfNeeded()`.

**Part C — WorkManager sync worker:**
Create a `ListSyncWorker` extending `CoroutineWorker` in `data/worker/` that:

1. Gets the active category keys from `BlockingRepository.getActiveCategoryKeys()`.
2. For each active category that has a StevenBlack URL mapping, downloads the
   corresponding URL using OkHttp with a 30-second read timeout.
3. Passes the raw text to `parseHostsFile()`.
4. Calls `BlockedDomainDao.insertDomains()` with the parsed entries in chunks
   of 1000 rows per call.
5. On success, saves `lastSyncTimestamp` to SharedPreferences (the app does not
   currently use DataStore — do NOT introduce it for this).
6. Returns `Result.success()` on completion, `Result.retry()` on network failure.

Schedule this worker in `FocusLockApplication.onCreate()` (or wherever
`seedDomainDataIfNeeded()` is called) with `ExistingWorkPolicy.KEEP` so it
doesn't re-run if already scheduled. Re-schedule weekly using
`PeriodicWorkRequestBuilder` with a 7-day interval.

Inject dependencies using `@HiltWorker` and `@AssistedInject` — follow the
same pattern as existing `SessionWorkerSchedulerImpl.kt`.

**Part D — Fast Lane lookup:**
The fast lane check is ALREADY DONE in `SmartScanUseCase` via
`BlockingRepository.getCachedDomainStatus(domain)` which calls
`BlockedDomainDao.findActiveDomain()`. No changes needed here.

**Part E — Initial load from bundled assets (OPTIONAL OPTIMIZATION):**
The `hosts.txt` file already exists at `app/src/main/assets/hosts.txt`.
To provide offline coverage on first launch BEFORE the WorkManager sync runs,
add a parser call in `BlockingRepositoryImpl.seedDomainDataIfNeeded()` that:
1. Reads `assets/hosts.txt`
2. Parses it using the same `parseHostsFile()` function
3. Inserts via `BlockedDomainDao.insertDomains()` (IGNORE strategy prevents dupes)

This is optional — the sync worker will handle it, but this provides Day 1 coverage.

### ACCEPTANCE CRITERIA
- 200,000 domain entries insert without ANR or OOM (chunked in 1000-row batches)
- Parser handles both hosts file format (`0.0.0.0 domain`) and plain domain list
- `www.casino.com` and `casino.com` both resolve to `casino.com` before insertion
- Worker retries on network failure — does not fail silently
- Only categories enabled by the user are downloaded
- FK constraint not violated — all `categoryKey` values exist in `blocked_categories`
- `BlockedDomainDao.insertDomains()` uses IGNORE strategy — no overwriting of
  existing user-modified entries

---

_End of STEVENBLACK_LIST_PROMPT.md_
