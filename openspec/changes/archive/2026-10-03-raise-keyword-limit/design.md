# Design

## Context

See proposal.md (Why) for motivation. Current state, verified in code:
- `BlockingRepository` owns `private const MAX_KEYWORDS = 10`, enforced in `addKeyword()` via `getKeywordCount() >= MAX_KEYWORDS`; the failure message interpolates the const.
- `KeywordSelectionScreen` hardcodes `val maxKeywords = 50` for the Add-button enable, the "N / 50" counter, and the red threshold.
- `BlockingRepositoryTest` asserts the literal text "Maximum 10 keywords reached".
- Matching (`KeywordScanner`, Aho-Corasick) is flat in keyword count; no engine, DB, migration, manifest, or permission changes are involved.

## Goals / Non-Goals

**Goals:**
- Enforced limit becomes 50 with one owner both gates compile against.
- Counter, Add enable, error styling, and repository enforcement agree by construction.
- Suite locks the agreement so drift fails the build, not the user.

**Non-Goals:**
- No unlimited mode, no per-user/per-session configurability, no settings UI for the limit.
- No matcher, synonym, normalization, DAO, schema, or navigation changes.
- No change to blank/duplicate rejection semantics.

## Decisions

- **Single const owned by the data layer (`BlockingRepository.MAX_KEYWORDS = 50`, widened visibility) over a new domain limits object.** Rationale: the enforcer already owns a const with the right shape and the message interpolates it; introducing a new home for one number adds indirection without a second consumer. Alternative (domain-level `KeywordLimits` object) reconsidered if a second limit (e.g., per-session caps) ever appears.
- **Screen reads the limit through `KeywordSelectionViewModel`** (which already bridges UI and domain) rather than importing the repository const directly. Rationale: preserves SoC — Composables don't reach past the ViewModel; keeps the DIP direction UI -> ViewModel -> domain/data.
- **Keep the refusal UX identical, only renumbered.** Rationale: Point 7 — same error path, same styling triggers, zero new states to test.
- **Alignment test asserts UI max == repository max** (plus updated message assertion). Rationale: Point 8 — turns the exact bug class that caused this change into a build failure.

## Risks / Trade-offs

- [Risk] Users add vague keywords across 50 slots and over-block themselves → Mitigation: existing ≥5-char AC rule and word-boundary fallback unchanged; out of scope to retune matching here.
- [Risk] Widening const visibility slightly broadens API surface → Mitigation: single `val`, no behavior; acceptable per YAGNI vs. a new abstraction.
- [Risk] None on performance: AC scan is O(content), rebuild O(patterns); 50 patterns is orders of magnitude below any cliff, verified by inspection (no benchmark needed).

## Migration Plan

No migration: the cap is a count check, not stored state. Existing users keep current keywords and can add up to 50. Rollback is a const revert.

## Open Questions

None — limit value (50), single-source approach, and test evidence were confirmed in exploration.
