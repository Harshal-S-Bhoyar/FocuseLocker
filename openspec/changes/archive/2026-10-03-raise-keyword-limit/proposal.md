# Proposal

## Why

The custom-keyword block list is capped at 10 keywords while the setup UI already advertises a 50-keyword limit ("N / 50" counter, Add enabled below 50), so users adding an 11th keyword hit a contradictory "Maximum 10 keywords reached" failure. Raising the enforced limit to 50 aligns behavior with the UI and removes the dead end, and unifying the two hardcoded numbers into one source prevents the drift from recurring.

## What Changes

- Raise the enforced custom-keyword maximum from 10 to 50 in `BlockingRepository`.
- Replace the UI-local `maxKeywords = 50` literal with the single shared limit so counter, Add-button enable, error styling, and repository enforcement can never disagree again.
- Update the repository error message (interpolates the constant, updates itself) and the unit test asserting the old "Maximum 10" text.
- Add a test locking UI max == repository max so future drift fails the suite instead of confusing users.
- No schema, migration, matcher, manifest, or permission changes: the cap is count-based and the matching engine (Aho-Corasick) is flat in keyword count.

## Capabilities

### New Capabilities

- `custom-keywords`: user-managed custom keyword block list for focus sessions, including the maximum size, counter display, and limit-refusal behavior.

### Modified Capabilities

(none — no existing spec covers keyword behavior)

## Impact

- Affected code: `BlockingRepository` (limit const), `KeywordSelectionScreen` / `KeywordSelectionViewModel` (limit source), `BlockingRepositoryTest` + alignment test.
- Affected tests: message assertion update; suite must stay green (`testDebugUnitTest`).
- No API, database, navigation, or permission impact. Existing users with ≤10 keywords are unaffected and can add up to 50.
