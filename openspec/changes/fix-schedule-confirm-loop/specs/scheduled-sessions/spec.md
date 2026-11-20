# Spec Delta: scheduled-sessions

## MODIFIED Requirements

### Requirement: Weekly schedule confirmation terminal state

The system SHALL provide a terminal confirmation state on `ScheduleConfirmScreen` when arming a weekly schedule whose active windows do not cover the current time, and SHALL NOT redirect the user back into the Category Selection setup wizard.

#### Scenario: Future weekly schedule reaches terminal confirmation

- **WHEN** the user completes the weekly schedule editor with future windows
- **AND** taps confirm on `ScheduleConfirmScreen`
- **THEN** the schedule is armed in the database, background schedule checks are scheduled, and onboarding is marked complete
- **AND** the screen displays a confirmed terminal state confirming the schedule is active without redirecting to `CategorySelectionScreen`

#### Scenario: Active window immediately navigates to active session

- **WHEN** the user completes the weekly schedule editor with a window covering the current time
- **AND** taps confirm on `ScheduleConfirmScreen`
- **THEN** the schedule is armed, onboarding is marked complete, and the app immediately navigates to `ActiveSessionScreen`

#### Scenario: Category screen with armed schedule does not loop

- **WHEN** a weekly schedule is active (`scheduleEnabled == true`)
- **AND** the user navigates to `CategorySelectionScreen`
- **THEN** the screen displays the armed status banner and does not provide a "Next" action that loops through the first-time setup wizard
