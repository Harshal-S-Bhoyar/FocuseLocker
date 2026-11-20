# Spec Delta

## ADDED Requirements

### Requirement: Launch-to-content budget

The system SHALL reach first content within 4 seconds worst case on the reference device, measured from process start through window draw plus init plus first paint.

#### Scenario: Slow cold start stays inside budget

- **WHEN** a cold launch runs on the reference device
- **THEN** content or explicit loading state is visible inside 4 seconds
