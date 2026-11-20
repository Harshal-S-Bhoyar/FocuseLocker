# Spec Delta

## ADDED Requirements

### Requirement: Only the latest URL verdict is applied

The system SHALL evaluate browser URL events one at a time in arrival order and SHALL apply only the verdict for the most recently observed URL. A check that finishes after a newer URL arrived SHALL have its verdict discarded instead of navigating, overlaying, or scanning for the superseded page.

#### Scenario: Fast navigation supersedes a slow check

- **WHEN** the user moves from page A to page B while A's check is still running
- **THEN** A's verdict is discarded when it arrives and only B's verdict takes effect

#### Scenario: Latest blocked page still enforced

- **WHEN** the most recently observed URL is blocked
- **THEN** the system navigates away and shows the blocking overlay for that URL
