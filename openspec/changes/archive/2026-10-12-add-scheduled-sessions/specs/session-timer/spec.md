# Spec Delta

## ADDED Requirements

### Requirement: Setup flow branches to Schedule or One-shot

The system SHALL offer a Schedule path and a One-shot path after keyword selection, where the Schedule path leads to the weekly template editor and then a schedule confirmation screen, and the One-shot path leads to the existing timer setup and then session confirmation. Saving a template SHALL NOT start a session.

#### Scenario: Choosing the schedule path

- **WHEN** the user picks Schedule after keyword selection
- **THEN** the app shows the weekly template editor instead of the timer setup

#### Scenario: Schedule save starts nothing

- **WHEN** the user saves the weekly template
- **THEN** no session starts and the app shows the schedule confirmation screen with the saved weekly summary

#### Scenario: Confirm ends setup cleanly

- **WHEN** the user confirms the armed schedule
- **THEN** the app returns to a fresh setup start with no setup screens left on the back stack

#### Scenario: One-shot path unchanged

- **WHEN** the user picks One-shot after keyword selection
- **THEN** the app shows the existing timer setup with days, hours, and minutes unchanged
