# battery-efficiency

## Purpose

Enforcement stays cheap. Blocking, scanning, scheduling, and logging cost the minimum CPU, radio, and disk needed for correct verdicts, so the phone stays cool and the battery lasts whether or not a session is active.

## ADDED Requirements

### Requirement: DNS verdicts are cached within TTL bounds

The system SHALL cache DNS-over-HTTPS verdicts keyed by domain plus query type, SHALL honor server TTL clamped to a 30s minimum and 300s maximum, and SHALL serve cache hits with zero network traffic while producing byte-identical verdicts to a fresh lookup.

#### Scenario: Repeated lookup skips the network

- **WHEN** the same domain plus query type resolves again inside its TTL
- **THEN** no HTTPS request leaves the device and the verdict matches a fresh lookup

#### Scenario: Expired verdict revalidates

- **WHEN** a cached verdict passes its clamped TTL
- **THEN** the next lookup performs a fresh lookup and refreshes the entry

#### Scenario: Blocked domains never reach the network

- **WHEN** a domain is locally blocklisted
- **THEN** the system returns NXDOMAIN without any cache or network interaction

### Requirement: No enforcement services without work to do

The system SHALL NOT run foreground enforcement services when no active session exists, SHALL start the permission watchdog on boot only when a persisted active session is present, and SHALL stop enforcement services once the session ends or expires.

#### Scenario: Boot without a session stays silent

- **WHEN** the device boots with no persisted active session
- **THEN** no watchdog, VPN, or enforcement service starts, while periodic schedule checks still rearm

#### Scenario: Session end releases services

- **WHEN** the active session completes, expires, or is cancelled
- **THEN** enforcement services stop and no polling loop keeps running

### Requirement: Log I/O is batched and crash-safe

The system SHALL buffer file log writes and flush them on a fixed interval, SHALL keep logcat output immediate, and SHALL flush pending logs on fatal crash before termination.

#### Scenario: Steady logging costs one flush per interval

- **WHEN** the app logs continuously during enforcement
- **THEN** disk writes happen in periodic batches, not once per log line

#### Scenario: Crash preserves logs

- **WHEN** an uncaught fatal exception occurs
- **THEN** buffered logs reach disk before the process dies

### Requirement: Event delivery is scoped to watched packages

The system SHALL scope accessibility event delivery to an explicit watched-package set during enforcement, and that set SHALL always include supported browsers, settings packages, explicitly blocked apps, and the app itself, and SHALL never silently drop a package class the active verdict paths need.

#### Scenario: Unwatched app generates no events

- **WHEN** a session with blocked apps is active and an unwatched third-party app changes windows
- **THEN** the service receives no event for it and all block paths still fire for watched packages

#### Scenario: Blocked app launch still intercepted

- **WHEN** a blocked app comes to the foreground during an active session
- **THEN** the launch is intercepted and the blocking overlay appears exactly as before

### Requirement: Periodic background work shares wakeups

The system SHALL align periodic enforcement workers within shared scheduler windows so schedule checks, session timers, and health checks batch into the minimum number of device wakeups at unchanged check frequency.

#### Scenario: Steady state wakes once per cycle

- **WHEN** all periodic workers are scheduled at the same interval
- **THEN** the scheduler may batch them into a single wakeup window without dropping any check
