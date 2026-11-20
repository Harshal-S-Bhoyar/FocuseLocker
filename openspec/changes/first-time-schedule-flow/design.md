# Design: first-time-schedule-flow

## Context

See proposal.md Why. Current first-time order is Category, Apps, Keywords, ScheduleMode chooser, then contact only inside the one-shot branch. The schedule branch checks contact at editor Done and can strand users short of confirm. CategorySelection also hosts a manage-schedule entry plus routing that no longer belongs in the wizard.

## Goals / Non-Goals

- Goals: contact before chooser for first-time setup; one confirm terminal; Category without manage entry; contact guard preserved on both paths.
- Non-Goals: no firing, expiry, unlock, enforcement, or battery-efficiency change; one-shot internals untouched.

## Decisions

- **Reorder via navigation graph only.** Move the TrustedContactSetup plus VerifyContact destinations between Keywords and ScheduleMode for first-time flow, gated on the existing verified-contact check. No ViewModel logic changes. Rejected alternative: duplicating contact screens per path (two flows to maintain).
- **Single confirm destination.** Both ScheduleMode branches navigate to the existing confirm screen with their own args. Lock-in calls the existing arm path, which already immediate-activates live windows. Rejected alternative: separate confirm screens (splits the terminal the user asked to unify).
- **Delete, not hide, the Category manage entry.** Remove the button plus its route and any now-unreferenced navigation callbacks. Banner stays, driven by the existing schedule-enabled flow.
- **Keep the editor Done guard as-is.** It already routes unverified users to contact setup; it becomes the safety net rather than the primary path. No signature change.

## Risks / Trade-offs

- [Risk] Reorder breaks the one-shot path. Mitigation: its branch keeps identical destinations and args; end-to-end regression test covers one-shot setup exactly as before.
- [Risk] Verified users see contact screens again. Mitigation: skip is driven by the same verified-contact source the Done guard uses; test both states.
- [Risk] Dead code removal orphans strings or resources. Mitigation: lint plus build after removal; delete only what the compiler plus tests prove unreferenced.

## Migration Plan

- Single releasable unit, plain revert for rollback. No data migration. Spec delta merges into `scheduled-sessions` at archive.

## Open Questions

- None.
