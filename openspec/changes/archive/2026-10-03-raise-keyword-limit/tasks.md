# Tasks

## 1. Single-source the keyword limit

- [x] 1.1 Widen `BlockingRepository.MAX_KEYWORDS` to 50 with a comment stating why the cap exists (anti-footgun, not a technical ceiling), verify the refusal message renders "Maximum 50 keywords reached" via a unit test
- [x] 1.2 Replace the UI-local `maxKeywords = 50` literal so counter, Add-button enable, and red threshold read the shared limit through `KeywordSelectionViewModel`, verify manual check: counter shows "N / 50" and 11th keyword adds cleanly

## 2. Lock the agreement in tests

- [x] 2.1 Update `BlockingRepositoryTest` message assertion from 10 to 50 and add boundary tests (50th add succeeds, 51st refused), verify `./gradlew :app:testDebugUnitTest --tests "com.focuslock.data.repository.BlockingRepositoryTest"` green
- [x] 2.2 Add an alignment test asserting the UI-surfaced max equals the repository max, verify it fails if either side drifts (mutate one side locally, observe red, revert)
- [x] 2.3 Run full `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug`, verify 0 failures/errors and no new lint findings in touched files
