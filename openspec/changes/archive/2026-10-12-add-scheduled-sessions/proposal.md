# Proposal

## Why

FocusLock starts sessions only when the user taps through setup. Users with fixed routines, such as nightly study hours, repeat the same setup every day. A weekly schedule removes that repetition and locks the phone on time without daily effort.

## What Changes

- Add a Schedule step to the setup flow after Keyword selection and before Timer setup, offering Schedule and One-shot paths that converge on Confirm.
- Add a weekly template editor: each weekday holds a list of windows with start and end times, a full-day preset filling 00:00 to 23:59, or an off state.
- Validate the template across the whole week: overlapping windows refuse the save and show an overlap warning.
- Fire due windows automatically through a periodic WorkManager check with minute-level start drift accepted.
- Resolve conflicts with a live-session-wins rule: a scheduled fire during a live manual session skips that occurrence, and a manual start during a live scheduled session stays blocked.
- Post a scheduled-start notice through the existing session notification channel on every automatic fire.
- Add a schedule management home with per-day edit and delete controls plus a master switch that pauses all fires while keeping rules.
- Open the management home from setup-adjacent entries on the setup start screen and the session complete screen.

## Capabilities

### New Capabilities

- `scheduled-sessions`: weekly template storage, window validation, automatic firing, conflict resolution, start notices, and schedule management.

### Modified Capabilities

- `session-timer`: the setup flow gains a Schedule branch before Timer setup, so one-shot timer setup becomes one of two paths to Confirm.

## Impact

- Affected areas: `ui/setup` (new screens, navigation branch in `AppNavigation.kt`), `ui/navigation`, domain layer (new schedule use cases), data layer (new schedule store and DAO), workers (new periodic schedule-check worker, reuse of `SessionTimerWorker` and `SessionExpiryWorker`), `BootReceiver` (re-arm of schedule checks), session notification channel (start notices).
- Reused unchanged: `ActivateSessionUseCase` single-active-session rule, session expiry path, OTP unlock flow, category/app/keyword stores as the shared settings source.
- No backend changes. No API changes. Maximum session length rules stay as defined in `session-timer`.
