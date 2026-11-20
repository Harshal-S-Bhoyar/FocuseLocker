# session-timer

## Purpose

Lets users express focus session length in days, hours, and minutes with natural unit caps, and shows the chosen duration back in the same terms before lock-in.

## Requirements

### Requirement: Days unit in timer setup

The system SHALL offer days (0-7), hours (0-23), and minutes (0-59) pickers for session duration, SHALL keep the total within 1 minute and 7 days, and SHALL compute duration millis as days*1440 + hours*60 + minutes.

#### Scenario: Setting a multi-day duration
- **WHEN** the user sets 2 days, 3 hours, 0 minutes
- **THEN** the session duration is 2 days 3 hours in millis and the flow continues

#### Scenario: Range bounds enforced
- **WHEN** the total is 0 minutes or exceeds 7 days
- **THEN** the continue control is disabled and an invalid-range message shows

### Requirement: Hour overflow rolls into days

The system SHALL roll hours past 23 into days (23h +1 becomes 0h with days +1, capped at the 7-day maximum) and SHALL borrow symmetrically when decrementing hours at 0 with days above 0 (0h becomes 23h with days -1). Decrementing days directly SHALL only lower days, leaving hours and minutes unchanged.

#### Scenario: Increment rolls over
- **WHEN** hours reads 23 and the user increments hours while the total is below 7 days
- **THEN** hours resets to 0 and days increments by 1

#### Scenario: Decrement borrows a day
- **WHEN** hours reads 0 with days above 0 and the user decrements hours
- **THEN** hours becomes 23 and days decrements by 1

#### Scenario: Rollover blocked at maximum
- **WHEN** a rollover would exceed 7 days (6d 23h 59m incrementing hours, or any state where days would pass 7)
- **THEN** the increment is refused and the values stay unchanged

#### Scenario: Reaching exactly 7 days
- **WHEN** the user raises days toward 7 with nonzero hours or minutes
- **THEN** days may reach 7 only once hours and minutes are both 0

### Requirement: Confirmation summary renders days

The system SHALL render the confirmed duration with a days component when days are nonzero (e.g., "2 days, 3 hours") instead of folding days into hours.

#### Scenario: Multi-day summary
- **WHEN** the chosen duration includes whole days
- **THEN** the confirmation shows days plus remaining hours/minutes in the same pluralized style as today

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
