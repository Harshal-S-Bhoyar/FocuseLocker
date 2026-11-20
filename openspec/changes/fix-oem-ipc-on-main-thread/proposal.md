# Proposal

## Why

`PermissionSetupViewModel.refreshPermissions()` runs `PackageManager.resolveActivity()` Binder IPC on the main thread (`viewModelScope`). Each refresh performs up to 2 synchronous cross-process calls (autostart + battery lists; 1 component each on Xiaomi-class OEMs), repeated up to 3 times per refresh while permissions are incomplete. Moving the blocking calls to `Dispatchers.IO` removes main-thread stalls with zero behavior change.

## What Changes

- Wrap the two OEM intent-list resolutions in `refreshPermissions()` in `withContext(Dispatchers.IO)`; state emission stays on Main.
- No signature changes, no new dependencies, no new dispatcher parameter (the ViewModel has none; `Dispatchers.IO` inline keeps the diff minimal).
- Retry loop (3 attempts × 800ms), early return on `allCriticalGranted`, and all emitted states stay exactly as-is.

## Capabilities

### New Capabilities

- None. Threading of an existing read is not a user-observable behavior.

### Modified Capabilities

- None. No requirement in `app-blocking`, `app-startup`, `custom-keywords`, `scheduled-sessions`, or `session-timer` constrains which thread permission state is read on. Per schema rules this change sets `skip_specs: true` instead of inventing a requirement.

## Impact

- Affected code: `app/src/main/java/com/focuslock/ui/onboarding/PermissionSetupViewModel.kt` only (lines ~62–87).
- No API, data model, dependency, migration, navigation, or string impact.
- Tests: existing permission-setup tests must stay green; no new test framework needed (logic unchanged, thread-only move).
