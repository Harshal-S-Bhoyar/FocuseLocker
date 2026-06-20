# Plan: Phase 2 - Hardening Domain Layer Tests
## Acceptance Criteria
- AC-1: `DoHQueryUseCaseTest` covers network timeouts, malformed JSON, 429 rate limiting, and unrecognized Status codes.
- AC-2: `SmartScanUseCaseTest` handles repository exceptions, varied keyword matching scenarios, and explicit cancellations.
- AC-3: `MetaDescriptionFetcherTest` covers edge cases like oversized payloads, non-UTF-8 encodings, malformed HTML, and SSRF via redirects.
- AC-4: All new and existing Domain Layer tests pass consistently without flakiness (`testDebugUnitTest --tests "com.focuslock.domain.usecase.*"`).

## Tasks
- [x] Task 1: Harden `DoHQueryUseCaseTest.kt` with edge-case scenarios. → verify: `gradlew testDebugUnitTest --tests "com.focuslock.domain.usecase.DoHQueryUseCaseTest"`
- [x] Task 2: Harden `SmartScanUseCaseTest.kt` with edge-case scenarios. → verify: `gradlew testDebugUnitTest --tests "com.focuslock.domain.usecase.SmartScanUseCaseTest"`
- [x] Task 3: Harden `MetaDescriptionFetcherTest.kt` with edge-case scenarios (SSRF, malformed HTML, encoding). → verify: `gradlew testDebugUnitTest --tests "com.focuslock.domain.usecase.MetaDescriptionFetcherTest"`
- [x] Task 4: Verify Domain Layer suite and output results. → verify: `gradlew testDebugUnitTest --tests "com.focuslock.domain.usecase.*"`

## Skills
- @master-skill
- @fable
- @ponytail
