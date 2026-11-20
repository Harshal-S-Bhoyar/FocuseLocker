# app-startup

## Purpose

Launch stays fast and visibly alive on every start, so users never face a dead black screen while the app prepares itself.

## Requirements

### Requirement: Seed runs exactly once ever

The system SHALL record the domain-seeded flag atomically with the seed transaction, and SHALL skip all seed I/O on later launches when the flag is set.

#### Scenario: Second launch skips seeding

- **WHEN** the app launches after a completed seed
- **THEN** no asset parsing or bulk inserts run before first content

#### Scenario: Interrupted seed retries cleanly

- **WHEN** a previous seed died before recording the flag
- **THEN** the next launch reseeds fully instead of trusting partial rows

### Requirement: Bounded parallel initialization

The system SHALL run independent init branches concurrently and SHALL bound each branch with a timeout, degrading a stuck branch to a retryable error state instead of an endless loader.

#### Scenario: Wedged step degrades visibly

- **WHEN** any init branch exceeds its budget
- **THEN** the app leaves loading for an explicit error or cached state within a fixed ceiling

### Requirement: Loading UI paints from the first frame

The system SHALL render loading text plus progress immediately on launch without waiting on animation composition, and SHALL keep it until content or error replaces it.

#### Scenario: Slow init still communicates

- **WHEN** initialization takes longer than one second
- **THEN** the user sees loading text and progress, never a blank screen

### Requirement: Navigation events survive delivery

The system SHALL navigate on a routing event before marking it consumed, so a recomposition or recreation cannot drop the navigation.

#### Scenario: Event fires navigation exactly once

- **WHEN** the onboarding gate emits a routing event
- **THEN** the app navigates even if the screen recomposes mid-delivery
