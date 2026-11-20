# scheduled-sessions

## Purpose

Lets users run focus sessions on an automatic weekly timetable so routine locks start on time without daily setup.

## Requirements

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

### Requirement: Overnight windows belong to the start day

The system SHALL treat a window whose end time is at or before its start time as crossing midnight, with the run owned by the start day and covering the next morning up to the end time.

#### Scenario: Monday night run covers Tuesday morning

- **WHEN** Monday holds 20:00 to 14:00
- **THEN** the lock runs Monday 20:00 to Tuesday 14:00 and Tuesday morning inherits the lock before its own windows

### Requirement: Week-wide overlap validation

The system SHALL expand every window into absolute weekly minutes before saving, SHALL refuse any save where two windows intersect, and SHALL show an overlap warning naming the colliding windows.

#### Scenario: Overlapping save refused

- **WHEN** Monday holds 20:00 to 14:00 next day and the user adds Tuesday 10:00 to 12:00
- **THEN** the save is refused and the overlap warning names both windows

#### Scenario: Adjacent windows allowed

- **WHEN** one window ends Tuesday 14:00 and the next starts Tuesday 20:00
- **THEN** the save succeeds with no warning

### Requirement: Automatic firing with shared live settings

The system SHALL start due windows automatically through a periodic background check, SHALL compute the session length from the window, and SHALL enforce the block list as it stands at fire time. Start times MAY drift by minutes past the set time.

#### Scenario: Evening fire uses latest edits

- **WHEN** the user edits the block list Tuesday afternoon and the Tuesday 20:00 window fires
- **THEN** the session enforces the edited list

#### Scenario: Fire duration matches window

- **WHEN** the Monday 20:00 to Tuesday 14:00 window fires
- **THEN** the session expires Tuesday at 14:00 through the normal expiry path

### Requirement: Live session wins conflicts

The system SHALL skip a scheduled fire while another session is live, and SHALL keep a manual start blocked while a scheduled session is live until the user ends it.

#### Scenario: Scheduled fire meets manual session

- **WHEN** the Tuesday 20:00 window fires during a live manual session
- **THEN** the fire is skipped for that occurrence and the manual lock continues undisturbed

#### Scenario: Manual start meets scheduled session

- **WHEN** the user starts a manual session during a live scheduled session
- **THEN** the start stays blocked with the existing active-session message

### Requirement: Scheduled-start notice

The system SHALL post a notice through the existing session notification channel on every automatic fire, naming the rule that fired.

#### Scenario: Notice names the rule

- **WHEN** the Monday 20:00 window fires
- **THEN** the notice names the Monday 20:00 window as the cause

### Requirement: Identical completion and unlock

Scheduled sessions SHALL complete and unlock exactly like manual sessions, with the same completion state and the same OTP unlock flow.

#### Scenario: Scheduled run ends like a manual run

- **WHEN** a scheduled session reaches its window end
- **THEN** the app shows the same completion state manual sessions show

### Requirement: Management home with master switch

The system SHALL provide a management home listing each day rule with edit and delete controls plus one master switch. The switch SHALL pause all fires while keeping every rule, and the periodic check SHALL read the switch first and skip all fires while it stays off.

#### Scenario: Pause keeps rules

- **WHEN** the user flips the master switch off for a holiday week
- **THEN** no window fires and all rules remain stored for resume

#### Scenario: Entries open the management home

- **WHEN** the user opens the setup start screen or the session complete screen
- **THEN** an entry control opens the same management home

### Requirement: Fires require live enforcement permissions

The system SHALL check accessibility plus overlay before starting a scheduled session. A fire with dead permissions SHALL skip activation, SHALL leave the occurrence unrecorded for retry, and SHALL route the user through the existing repair notice.

#### Scenario: Dead permissions skip the fire

- **WHEN** a window fires while accessibility is disabled
- **THEN** no session starts and the next check retries the same occurrence after repair

### Requirement: Rearm survives restarts and updates

The system SHALL rearm the periodic schedule check on every application start in addition to system boot, and SHALL listen to direct-boot plus quickboot plus package-replaced intents alongside normal boot. Rearm SHALL be enqueue-only with replace semantics, never an activation.

#### Scenario: Cold start arms without reboot

- **WHEN** the app starts fresh with no reboot
- **THEN** the periodic check is enqueued exactly once

#### Scenario: Update restarts rearm

- **WHEN** the package is replaced by an update
- **THEN** the periodic check is rearmed on the replace intent
