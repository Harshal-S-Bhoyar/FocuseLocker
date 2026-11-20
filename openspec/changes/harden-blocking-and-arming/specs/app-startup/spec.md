# Spec Delta

## MODIFIED Requirements

### Requirement: Startup permission probe tolerates a slow binder

The startup sequence SHALL NOT report permissions as missing solely because a single permission
probe exceeded its timeout. On a probe timeout the system SHALL re-probe once before degrading to
the not-granted result, and SHALL bound every wait so startup cannot hang indefinitely.

#### Scenario: First probe times out, second succeeds

- **WHEN** the first permission probe exceeds its timeout
- **AND** the second probe returns the real permission state
- **THEN** routing uses the real permission state and the permission-repair flow is not presented

#### Scenario: Both probes time out

- **WHEN** both the first and second permission probes exceed their timeout
- **THEN** permissions are treated as not granted so the repair flow, not a hang, owns the outcome

#### Scenario: Startup step times out

- **WHEN** a startup initialization step exceeds its timeout
- **THEN** loading ends and the user is offered a retry instead of being left on a blank screen
