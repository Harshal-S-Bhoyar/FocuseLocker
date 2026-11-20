# Spec Delta

## MODIFIED Requirements

### Requirement: Seed runs exactly once ever

The system SHALL record the domain-seeded flag atomically with the seed transaction, SHALL skip all seed I/O on later launches when the flag is set, and SHALL bound the total time an init waits to seed — including time queued behind another in-flight seed — within the seed budget.

#### Scenario: Second launch skips seeding

- **WHEN** the app launches after a completed seed
- **THEN** no asset parsing or bulk inserts run before first content

#### Scenario: Interrupted seed retries cleanly

- **WHEN** a previous seed died before recording the flag
- **THEN** the next launch reseeds fully instead of trusting partial rows

#### Scenario: Contended seed stays bounded

- **WHEN** two init sequences race for the seed lock
- **THEN** the waiter either proceeds within the seed budget or degrades to a retryable error instead of waiting unboundedly

### Requirement: Bounded parallel initialization

The system SHALL run independent init branches concurrently and SHALL bound each branch with a timeout, degrading a stuck branch to a retryable error state instead of an endless loader. The system SHALL run at most one initialization sequence at a time: starting a new sequence SHALL cancel the prior one so concurrent runs cannot overwrite each other's UI state.

#### Scenario: Wedged step degrades visibly

- **WHEN** any init branch exceeds its budget
- **THEN** the app leaves loading for an explicit error or cached state within a fixed ceiling

#### Scenario: Retry supersedes the prior run

- **WHEN** the user retries while a previous initialization is still running
- **THEN** the previous run is cancelled and only the newest run may update loading, error, or navigation state
