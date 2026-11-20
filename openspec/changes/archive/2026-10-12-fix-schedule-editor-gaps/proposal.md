# Proposal

## Why

The weekly schedule editor lets users add windows but has two UI gaps: saved rows cannot be tapped to edit, and cancelling the dialog leaves a stale overlap warning on the list. Both erode trust in setup right before archive.

## What Changes

- Tap a saved window row to reopen the editor prefilled with that window's day plus start plus end.
- Save from edit reuses the existing `saveWindow(id, ...)` path, keeping overlap plus validation behavior unchanged.
- Cancel, dismiss, and successful save all clear the transient dialog error so the list never shows a stale banner.
- No engine, persistence, firing, or navigation changes.

## Capabilities

### New Capabilities

- None. This restores intended editor behavior already covered by the ViewModel edit path.

### Modified Capabilities

- None. No requirement text changes. Pure UI bugfix, so this change opts out of specs via `skip_specs: true`.

## Impact

- Affected code: `app/src/main/java/com/focuslock/ui/setup/ScheduleEditorScreen.kt` only.
- No API, data model, dependency, or migration impact.
- Tests: existing `ScheduleEditorViewModelTest` covers the edit path. New coverage targets dialog prefill plus error clearing.
