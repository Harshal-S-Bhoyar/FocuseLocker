# custom-keywords

## Purpose

Lets users maintain a personal list of custom keywords blocked during focus sessions, with a clear maximum size, an honest counter, and a correct refusal message at the limit.

## Requirements

### Requirement: Custom keyword list capped at 50

The system SHALL allow up to 50 active custom keywords and SHALL refuse additions beyond 50 with a message stating the current maximum.

#### Scenario: Adding below the limit succeeds
- **WHEN** the user adds a keyword while fewer than 50 are stored
- **THEN** the keyword is stored and becomes active for blocking

#### Scenario: Adding at the limit is refused
- **WHEN** the user adds a keyword while 50 are already stored
- **THEN** the system refuses the addition and reports that the maximum of 50 keywords has been reached

### Requirement: Keyword counter reflects the true limit

The system SHALL display a keyword counter of the form "N / 50" where 50 is the same limit the system enforces, SHALL disable adding at the limit, and SHALL highlight the counter once the limit is reached.

#### Scenario: Counter matches enforcement
- **WHEN** the user views the keyword selection screen
- **THEN** the displayed maximum equals the enforced maximum, the Add control is disabled when the count reaches it, and no add attempt can fail against a differently-numbered limit

### Requirement: Blank and duplicate keywords rejected

The system SHALL reject blank keywords and keywords that already exist, independent of the maximum limit.

#### Scenario: Blank keyword rejected
- **WHEN** the user adds an empty or whitespace-only keyword
- **THEN** the system refuses with a blank-keyword error and stores nothing

#### Scenario: Duplicate keyword rejected
- **WHEN** the user adds a keyword that already exists (case-insensitive, normalized)
- **THEN** the system refuses with a duplicate error and stores nothing
