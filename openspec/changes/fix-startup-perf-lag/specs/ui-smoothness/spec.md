# Spec Delta

## Purpose

In-app surfaces stay smooth under load, so blocking, timers, and lists never stutter while sessions run.

## ADDED Requirements

### Requirement: Countdown recomposition stays scoped

The system SHALL confine per-second timer updates to the timer text composable, and SHALL NOT recompose the surrounding session tree on tick.

#### Scenario: Tick repaints text only

- **WHEN** the remaining-time flow emits a new second
- **THEN** only the timer text recomposes while status rows plus buttons skip

### Requirement: Matching runs off Main with a warm cache

The system SHALL evaluate URL and keyword matching off the Main thread, and SHALL reuse a cached normalized domain set across events instead of rebuilding per event.

#### Scenario: Burst browsing holds frames

- **WHEN** browser events arrive at the throttle ceiling
- **THEN** frames hold deadline and verdicts match the uncached result

### Requirement: Lists rebind by content change only

The system SHALL key lazy-list rows with stable content types, so database emissions rebind only changed rows instead of whole lists.

#### Scenario: Background write preserves scroll

- **WHEN** a DAO flow emits during an active session
- **THEN** visible rows keep position and unchanged rows skip recomposition
