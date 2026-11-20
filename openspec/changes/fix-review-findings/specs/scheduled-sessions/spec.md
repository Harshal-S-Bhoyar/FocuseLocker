# Spec Delta

## ADDED Requirements

### Requirement: Setup arming is all-or-nothing on onboarding completion

The system SHALL NOT mark onboarding complete when enabling the schedule fails during setup arming. A failed enable SHALL surface an error and leave onboarding incomplete so the user can retry, instead of recording success for a schedule that was never enabled.

#### Scenario: Enable failure blocks completion

- **WHEN** the user confirms the schedule but persisting the enabled flag fails
- **THEN** onboarding stays incomplete, an error is shown, and no success navigation occurs

#### Scenario: Successful arming completes onboarding

- **WHEN** the user confirms the schedule and every arming step succeeds
- **THEN** onboarding is marked complete exactly once

### Requirement: Setup completion waits for known contact state

The system SHALL keep the schedule editor's Done control disabled until the trusted-contact verification state has finished loading. The completion callback SHALL receive the loaded verification result, never a pre-load default.

#### Scenario: Fast tap before load

- **WHEN** the user taps Done before verification state loads
- **THEN** nothing happens (the control is disabled) and no navigation occurs

#### Scenario: Loaded state routes correctly

- **WHEN** verification state loads as verified and the user taps Done
- **THEN** the flow proceeds with the verified result
