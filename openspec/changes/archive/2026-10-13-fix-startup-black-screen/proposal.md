# Proposal

## Why

Every cold launch sits on a black screen for about fifteen seconds because onboarding init runs cleanup, settings, an unreliable seed gate, sync scheduling, status reads, and two permission probes strictly in series with no timeout, while the loading state renders only a blank-until-ready Lottie animation. Users kill the app before first paint, so the product reads as broken on its first impression.

## What Changes

- Make seed-once actually once: persist the seeded flag in the same transaction as the seed so reruns never happen.
- Parallelize independent init branches (cleanup plus settings plus seed gate plus sync scheduling) and move permission probing off the navigation critical path.
- Bound every init step with timeouts so one wedged call degrades to a retryable error instead of an endless loader.
- Show real loading UI from the first frame: text plus progress fallback that does not depend on Lottie composition.
- Consume navigation events after navigating, never before.

## Capabilities

### New Capabilities

- `app-startup`: fast, always-progressing application launch with observable loading state and bounded initialization.

### Modified Capabilities

- None. No existing requirement text changes.

## Impact

- Affected code: `InitializeAppUseCase`, `AppSettingsRepository` plus `AppSettingDao`, `OnboardingViewModel` plus `OnboardingScreen`, `FocusLockApplication`, permission status provider.
- No API, data model migration (flag row upsert instead of bare update), or dependency changes.
- Tests: unit tests for seed-once idempotence, init timeouts, event ordering; device timing assertion on launch-to-content.
