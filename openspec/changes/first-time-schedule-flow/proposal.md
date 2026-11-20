# Proposal

## Why

First-time users cannot complete a scheduled session today: trusted contact comes after the session-type chooser, the Category screen carries a schedule entry that dead-ends the wizard, and the schedule path never reliably reaches confirmation. This reorders first-time setup so both session types end at one confirm screen.

## What Changes

- Remove the manage-schedule entry button from CategorySelection; keep the armed-schedule banner.
- Remove dead schedule-routing code on the Category screen.
- Move trusted email plus OTP before the session-type chooser for first-time setup only; verified users skip it and it leaves the normal flow after.
- Keep the chooser split: weekly schedule goes to the schedule editor, one-shot timer goes to set-time; one-shot path otherwise byte-identical.
- Both paths end at the single confirm screen; lock-in arms always and applies constraints immediately when the window covers now.
- Keep the Done-time contact guard on the weekly schedule path as the safety net for unverified users.

## Capabilities

### New Capabilities

- None. All behavior refines the existing scheduled setup flow.

### Modified Capabilities

- `scheduled-sessions`: first-time setup ordering requirements (contact before chooser, single confirm terminal, Category without manage entry, contact guard on both paths).

## Impact

- Affected code: `ui/navigation/AppNavigation.kt`, `ui/setup/CategorySelectionScreen.kt`, schedule editor plus confirm screens, session-type chooser routing.
- No change to firing, expiry, unlock, enforcement, or battery-efficiency semantics.
- One-shot timer flow must be regression-tested end to end; its code path stays identical.
- Tests: navigation unit tests plus new flow-order tests for both paths and the unverified-contact guard.
