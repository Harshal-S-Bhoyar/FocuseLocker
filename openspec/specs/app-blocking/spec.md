# app-blocking

## Purpose

Lets users select distracting launchable apps per focus session and hard-blocks those apps until the session ends.

## Requirements

### Requirement: App block screen follows category selection

The system SHALL present an app-selection step immediately after Category selection in the session setup flow, listing only user-launchable installed apps each with a toggle control.

#### Scenario: User reaches app block step
- **WHEN** user completes Category selection and continues
- **THEN** system shows the App Block screen with search/list of launchable apps, each row showing app name, icon, and toggle defaulting to OFF for a new setup

#### Scenario: User continues without selecting
- **WHEN** user leaves all toggles OFF and continues
- **THEN** system proceeds to Keyword selection with an empty app block list for this session

### Requirement: Per-session fresh app selection with toggle semantics

The system SHALL treat toggle ON as blocked for the current session only, starting fresh (all OFF) for each new session setup, and SHALL persist the selection as part of that session.

#### Scenario: Toggle ON marks app blocked
- **WHEN** user turns a toggle ON for an app
- **THEN** system includes that app package in the current session block list and reflects ON state in the UI

#### Scenario: Fresh selection each session
- **WHEN** user starts a new session setup
- **THEN** system shows all toggles OFF regardless of prior session selections

#### Scenario: Selection survives setup navigation
- **WHEN** user navigates forward to Keyword/Timer/Confirmation or back to App Block within the same setup
- **THEN** system retains the toggle states until the session is started or setup is abandoned

### Requirement: Only user-launchable apps are listed, critical apps excluded

The system SHALL list only apps the user can launch from the launcher, and SHALL exclude FocusLocker itself, the current launcher, and critical system settings/phone components required to avoid self-lockout.

#### Scenario: Launchable filter
- **WHEN** the App Block screen loads inventory
- **THEN** system shows apps with launcher intents and hides non-launchable services, widgets-only packages, and excluded critical packages

#### Scenario: Excluded apps never blocked
- **WHEN** a session with app blocking is active
- **THEN** system never blocks FocusLocker, the active launcher, or the exclusion list even if previously selected due to stale data

### Requirement: Hard block of selected apps during active session

The system SHALL, while a session with a non-empty app block list is active, intercept foreground launch of any blocked package, return the user away from that app, and present the existing non-dismissible blocking overlay with remaining session time. The user SHALL NOT be able to use the blocked app until the session ends via normal completion, trusted-contact unlock, or emergency bypass where applicable.

#### Scenario: Blocked app launch intercepted
- **WHEN** a blocked app comes to the foreground during an active session
- **THEN** system navigates away from the app within 1 second and shows the blocking overlay indicating the app is blocked and time remaining

#### Scenario: Unblocked app unaffected
- **WHEN** a non-blocked app comes to the foreground during an active session
- **THEN** system allows normal use with no overlay or navigation interference

#### Scenario: No session means no blocking
- **WHEN** no session is active
- **THEN** system allows all apps to launch normally regardless of prior selections

### Requirement: Block list released on session end

The system SHALL clear enforcement immediately when the session ends (completed, cancelled, unlocked, or expired) and SHALL NOT persist the per-session app list as a global block list.

#### Scenario: Session expiry releases apps
- **WHEN** the active session reaches expiry or is ended
- **THEN** system stops intercepting blocked packages within 2 seconds and hides any app-block overlay

#### Scenario: No carryover
- **WHEN** a session ends
- **THEN** the next session setup starts with an empty selection

### Requirement: App inventory performance and offline behavior

The system SHALL load the launchable app list off the main thread with cached icons, show loading and empty states, and work fully offline without network access.

#### Scenario: Fast list display
- **WHEN** user opens the App Block screen on a device with 200+ apps
- **THEN** system shows the list without freezing, displays placeholders while icons load, and supports search/filter

#### Scenario: Offline inventory
- **WHEN** device is offline
- **THEN** system still lists installed launchable apps and allows toggle selection
