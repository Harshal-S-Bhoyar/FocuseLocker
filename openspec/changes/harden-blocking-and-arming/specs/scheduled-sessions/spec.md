# Spec Delta

## MODIFIED Requirements

### Requirement: Weekly schedule confirmation terminal state

The system SHALL provide a terminal confirmation state on `ScheduleConfirmScreen` when arming a
weekly schedule whose active windows do not cover the current time, and SHALL NOT redirect the user
back into the Category Selection setup wizard. Arming SHALL be reported as succeeded only when the
schedule is enabled **and** the background schedule checks have been enqueued.

#### Scenario: Future weekly schedule reaches terminal confirmation

- **WHEN** the user completes the weekly schedule editor with future windows
- **AND** taps confirm on `ScheduleConfirmScreen`
- **THEN** the schedule is armed in the database, background schedule checks are scheduled, and onboarding is marked complete
- **AND** the screen displays a confirmed terminal state confirming the schedule is active without redirecting to `CategorySelectionScreen`

#### Scenario: Active window immediately navigates to active session

- **WHEN** the user completes the weekly schedule editor with a window covering the current time
- **AND** taps confirm on `ScheduleConfirmScreen`
- **THEN** the schedule is armed, onboarding is marked complete, and the app immediately navigates to the session confirmation screen with the live window's remaining duration

#### Scenario: Enabling the schedule fails

- **WHEN** enabling the schedule fails while arming
- **THEN** arming is reported as failed and onboarding is not marked complete

#### Scenario: Enqueuing the scheduled checks fails

- **WHEN** the schedule is enabled but enqueuing the background schedule checks fails
- **THEN** arming is reported as failed, the error is exposed to the user, and onboarding is not marked complete

#### Scenario: Reading the saved schedule fails

- **WHEN** reading the saved windows fails while arming
- **THEN** arming is reported as failed with an error and the failure does not escape as a crash

#### Scenario: A failed arming is reported as failure, not as the armed state

- **WHEN** arming fails for any reason
- **THEN** the confirmation screen does not present the armed terminal state, keeps the error visible,
  and leaves its confirm action available so the user can retry

#### Scenario: Marking onboarding complete fails

- **WHEN** the schedule is enabled and the checks are enqueued but marking onboarding complete fails
- **THEN** the error is exposed to the user and the armed schedule is left enabled

### Requirement: Session confirmation does not absorb an unrelated live session

`SessionConfirmationScreen` SHALL treat an already-running session as the one being confirmed only
when the screen was reached from the schedule hand-off for a live window. For every other entry
path the requested duration SHALL be honoured and an unrelated running session SHALL NOT be adopted
silently.

#### Scenario: Schedule hand-off enters the live window

- **WHEN** the schedule hand-off opened session confirmation for a window that is already running
- **AND** the user taps lock in
- **THEN** the session is reported as started without activating a second session

#### Scenario: One-shot start while a session is running

- **WHEN** the user starts a one-shot session with a chosen duration while an unrelated session is already running
- **AND** the user taps lock in
- **THEN** the requested duration is honoured and the unrelated running session is not adopted as the requested session

#### Scenario: One-shot start with no running session

- **WHEN** the user starts a one-shot session and no session is running
- **THEN** the session is activated with the requested duration
