# Spec Delta

## ADDED Requirements

### Requirement: Contact precedes session-type choice for first-time setup

The system SHALL present trusted contact setup plus OTP verification before the session-type chooser during first-time setup, SHALL skip it when a verified contact already exists, and SHALL NOT present it again in later sessions.

#### Scenario: First-time user verifies before choosing

- **WHEN** a first-time user without a verified contact finishes keywords
- **THEN** trusted contact setup plus OTP comes next, and the chooser follows verification

#### Scenario: Verified user skips contact

- **WHEN** a verified contact already exists
- **THEN** the flow goes directly from keywords to the session-type chooser

### Requirement: Single confirm terminal for both session types

The system SHALL route both the weekly schedule path and the one-shot timer path to the same session confirmation screen, SHALL arm on lock-in in both cases, and SHALL apply constraints immediately when the active window covers the current time.

#### Scenario: Schedule confirm locks in

- **WHEN** the user completes the schedule editor and confirms
- **THEN** the schedule arms, and a live window starts the session at once

#### Scenario: One-shot path unchanged

- **WHEN** the user chooses the one-shot timer
- **THEN** set-time plus the existing contact check plus confirm behave exactly as before

### Requirement: Category screen carries no schedule entry

The system SHALL NOT show a manage-schedule entry on the category selection screen, SHALL keep the armed-schedule banner, and SHALL contain no dead schedule-routing code on that screen.

#### Scenario: Category shows banner only

- **WHEN** a schedule is armed and the user views category selection
- **THEN** the armed banner shows and no manage entry or dead route exists

### Requirement: Contact guard stays on the schedule path

The system SHALL check contact verification when leaving the schedule editor, SHALL route unverified users to trusted contact setup plus OTP first, and SHALL continue to confirmation after verification.

#### Scenario: Unverified user at schedule Done

- **WHEN** an unverified user taps Done in the schedule editor
- **THEN** trusted contact setup plus OTP precedes confirmation
