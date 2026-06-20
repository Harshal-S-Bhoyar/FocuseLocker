# MASTER_BLOCKING_SYSTEM.md
# FocusLock — Complete Blocking System

---

## ⚡ AGENT EXECUTION INSTRUCTIONS

**You are reading the master plan.** Execute the following steps automatically:

1. Read this entire file to understand the current codebase state and what's already built.
2. Open and read `STEVENBLACK_LIST_PROMPT.md` (same directory). Execute all `[TODO]` phases. Skip `[ALREADY DONE]` phases.
3. After completing and verifying Order 1, open and read `HARDENED_BLOCKER_PROMPT.md`. Execute all `[TODO]` phases.
4. After completing and verifying Order 2, open and read `NEXTDNS_DOH_PROMPT.md`. Execute all `[TODO]` phases.
5. After completing and verifying Order 3, open and read `KEYWORD_META_SCAN_PROMPT.md`. Execute all `[TODO]` phases.

**Rules:**
- All prompt files are in this same directory: `Focuse_locker_phase_two/Part_1/`
- Each prompt file has `[ALREADY DONE — SKIP]` and `[TODO]` markers — only execute `[TODO]` phases
- Each prompt file has **Acceptance Criteria** at the bottom — verify before moving to the next file
- Do NOT recreate classes/files listed in the "Current Codebase State" table below
- Ask the user before proceeding to the next prompt file

---

## Current Codebase State (READ FIRST)

The following components are ALREADY implemented from Phase 1:

| Component | Class | Status |
|---|---|---|
| Room DB entity | `BlockedDomainEntity` (table: `blocked_domains`) | ✅ Done |
| DAO | `BlockedDomainDao` | ✅ Done |
| URL pre-check | `CheckUrlForBlockedContentUseCase` | ✅ Done |
| Normalized matching | `NormalizedMatcher` (NFD + leet + word boundary) | ✅ Done |
| Multi-signal fetch | `MetaDescriptionFetcher.extractSignals()` (7 signals + body_text) | ✅ Done |
| Retry fetch | `MetaDescriptionFetcher` (2 attempts, 4s timeout, 500ms delay) | ✅ Done |
| Domain cache | `SmartScanUseCase` (PERMANENT / CLEAN_CACHE with expiresAt) | ✅ Done |
| Blocking flow | `FocusAccessibilityService.handleBrowserEvent()` | ✅ Done |
| Database | `FocusDatabase` (version 6, 7 entities) | ✅ Done |

**Do NOT recreate any of the above.** Each prompt file below marks already-done phases
with `[ALREADY DONE]` — skip them and start from the first `[TODO]` phase.

---

## Execution Order

| Order | Prompt File | What It Builds | Depends On |
|---|---|---|---|
| 1 | `STEVENBLACK_LIST_PROMPT.md` | Hosts file parser + WorkManager sync worker | Nothing (DB schema exists) |
| 2 | `HARDENED_BLOCKER_PROMPT.md` | Fail-closed timeout policy + `source` column | Order 1 |
| 3 | `NEXTDNS_DOH_PROMPT.md` | Dynamic DoH category check + cache | Order 1, 2 |
| 4 | `KEYWORD_META_SCAN_PROMPT.md` | Aho-Corasick keyword matcher | Order 1, 2, 3 |

---

## Final Blocking Flow (after all prompts are implemented)

```
URL captured by FocusAccessibilityService.handleBrowserEvent()
        │
        ▼
[1] URL Pre-Check (CheckUrlForBlockedContentUseCase) [ALREADY DONE]
    Keyword in domain/path via NormalizedMatcher?
    YES → performGlobalAction(BACK) + launchOverlayService() (0ms, no network)
        │ NO
        ▼
[2] Room DB Fast Lane (SmartScanUseCase → BlockedDomainDao.findActiveDomain())  [ALREADY DONE]
    Domain in blocked_domains table?
    PERMANENT → Block instantly (< 50ms, no network)
    CLEAN_CACHE (not expired) → Allow instantly
        │ NOT FOUND
        ▼
[3] StevenBlack Offline List [TODO — Order 1]
    Domain in pre-downloaded hosts entries?
    YES → Block instantly (< 50ms, no network)
        │ NO
        ▼
[4] NextDNS DoH Query [TODO — Order 3]
    Ask NextDNS/AdGuard if domain is in a blocked category
    BLOCKED (Status 3) → Block + cache PERMANENT in blocked_domains
    CLEAN (Status 0)   → Allow + cache CLEAN_CACHE (24h) in blocked_domains
    FAILED             → Fall through
        │ FAILED / UNKNOWN
        ▼
[5] Keyword Meta Scan (SmartScanUseCase → MetaDescriptionFetcher) [PARTIALLY DONE]
    Fetch <head>, extract 7 signals, keyword match
    BLOCKED → Block + cache PERMANENT in blocked_domains
    CLEAN   → Allow + cache CLEAN_CACHE (24h)
    FAILED  → [TODO: Fail-closed — temp block 1hr instead of current allow]
```

---

## Performance Targets

| Layer | Expected time | Network call |
|---|---|---|
| URL pre-check (CheckUrlForBlockedContentUseCase) | < 1ms | No |
| Room DB lookup (BlockedDomainDao.findActiveDomain) | < 50ms | No |
| StevenBlack list | < 50ms | No (after initial sync) |
| NextDNS DoH | < 300ms | Yes (1KB query) |
| Keyword meta scan (MetaDescriptionFetcher) | < 7s worst case | Yes (head fetch only) |

---

## Key Codebase References

| Concept | File Path |
|---|---|
| DB Entity | `data/local/entity/BlockedDomainEntity.kt` |
| DAO | `data/local/dao/BlockedDomainDao.kt` |
| Database | `data/local/FocusDatabase.kt` (version 6) |
| Repository | `data/repository/BlockingRepositoryImpl.kt` |
| URL Pre-Check | `domain/usecase/CheckUrlForBlockedContentUseCase.kt` |
| Normalized Matching | `domain/usecase/NormalizedMatcher.kt` |
| Signal Fetcher | `domain/usecase/MetaDescriptionFetcher.kt` |
| Smart Scan | `domain/usecase/SmartScanUseCase.kt` |
| Scan Result Model | `domain/model/SmartScanResult.kt` |
| Fetch Outcome Model | `domain/model/FetchOutcome.kt` |
| Blocking Flow | `service/accessibility/FocusAccessibilityService.kt` |
| DI Entry Point | `di/BlockingServiceEntryPoint.kt` |
| Existing Workers | `data/worker/SessionWorkerSchedulerImpl.kt` |

---

## Notes

- `STEVENBLACK_LIST_PROMPT.md` Phase 1 (Room schema) is ALREADY DONE — the
  `blocked_domains` table exists with `block_status`, `expires_at`, `category_key`.
  Skip directly to Phase 2 (parser + sync worker).
- `HARDENED_BLOCKER_PROMPT.md` Phases 1–3 are ALREADY DONE. Start from Phase 4
  (fail-closed timeout policy).
- The existing `blocked_domains` table uses `category_key` with a FK to
  `blocked_categories`. New StevenBlack entries should use category keys like
  `ADULT`, `GAMBLING`, `SOCIAL`, etc. matching `CategoryKey.ALL`.
- NextDNS requires a free account for full category control. AdGuard fallback
  works with no account but only covers adult and gambling categories.
- The meta scan in Order 4 is a last resort — in practice it will rarely
  trigger once Orders 1–3 are running.

---

_End of MASTER_BLOCKING_SYSTEM.md_
