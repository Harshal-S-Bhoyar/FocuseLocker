# Plan: Production-Readiness Performance Audit

## Acceptance Criteria
- AC-1: ProGuard rules strip Timber logs in release mode.
- AC-2: `ListSyncWorker` and `StevenBlackParser` use streaming (`byteStream()`) and avoid full-file memory allocations.
- AC-3: `ListSyncWorker` hoists `getSharedPreferences()` to reduce disk I/O.
- AC-4: `material-icons-extended` dependency is removed from `build.gradle.kts` to reduce APK bloat.
- AC-5: P2 issues (ByteArray pooling) and P3 issues (Network Timeout) are verified as already fixed in previous sessions.

## Tasks
- [ ] Task 1: Update `proguard-rules.pro` to add Timber `assumenosideeffects` rules.
- [ ] Task 2: Refactor `StevenBlackParser.kt` to accept `InputStream`, and update `ListSyncWorker.kt` to use `response.body?.byteStream()` and hoist SharedPrefs.
- [ ] Task 3: Remove `material-icons-extended` from `build.gradle.kts`.

## Skills
- master-skill
- fable
- ponytail (ultra)
