# Spec Delta

## MODIFIED Requirements

### Requirement: Weekly template with per-day windows

The system SHALL store one weekly template where each weekday holds a list of windows with start and end times, a full-day preset filling 00:00 to 24:00 end-of-day, or an off state. Start times SHALL stay within 00:00 to 23:59 and end times within 00:00 to 24:00 on the clock face, where 24:00 means midnight closing the window's day.

#### Scenario: Setting a weekday window

- **WHEN** the user sets Monday 20:00 to 23:59 plus Tuesday 00:00 to 14:00 equivalent through overnight entry
- **THEN** the template stores both windows against Monday ownership and shows them on the Monday row

#### Scenario: Full-day preset

- **WHEN** the user taps Full-day on Saturday
- **THEN** the editor fills 00:00 to 24:00 and Saturday fires a full-day lock including the final minute

#### Scenario: Late-minute coverage

- **WHEN** a window ends at 24:00 and the check runs at 23:59
- **THEN** the window counts as due with 1 minute remaining instead of leaving the minute uncovered

#### Scenario: Day off

- **WHEN** the user leaves Sunday with no windows
- **THEN** no session fires on Sunday
