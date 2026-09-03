# Plan: Phase 5 - Architectural Remediation - Domain Purity

## Acceptance Criteria
- [ ] AC-1: Zero `android.*` imports in the `domain` module (except `androidx.annotation` if needed).
- [ ] AC-2: Zero `okhttp3.*` imports in the `domain` module.
- [ ] AC-3: `WorkManager` usages in domain are replaced by abstract schedulers (e.g., `BackgroundSyncScheduler`).
- [ ] AC-4: `CheckUrlForBlockedContentUseCase` is removed or refactored.
- [ ] AC-5: `SmartScanUseCase` caching is abstracted away from Android `LruCache`.
- [ ] AC-6: `KeywordScanner` does not depend on Android `Context`.
- [ ] AC-7: DI (Hilt) modules updated to provide platform-specific implementations for all new domain interfaces.

## Tasks
- [ ] Task 1: Audit and extract Android dependencies from `InitializeAppUseCase` (create `BackgroundSyncScheduler`).
- [ ] Task 2: Abstract HTTP and HTML parsing out of `MetaDescriptionFetcher` and `DoHQueryUseCase` into repository interfaces.
- [ ] Task 3: Replace `LruCache` in `SmartScanUseCase` with a pure Kotlin cache or repository cache interface.
- [ ] Task 4: Extract `Context` from `KeywordScanner` by creating an `AssetProvider` interface.
- [ ] Task 5: Remove dead imports and clean up `CheckUrlForBlockedContentUseCase`.
- [ ] Task 6: Implement the platform-specific adapters in the `data` or `framework` layer and bind them in Hilt modules.
