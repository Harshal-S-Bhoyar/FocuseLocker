# Spec Delta

## ADDED Requirements

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
