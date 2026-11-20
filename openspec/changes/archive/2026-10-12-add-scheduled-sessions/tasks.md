# Tasks

## 1. Schedule data layer

- [x] 1.1 Add schedule window entity, master-switch entity, DAO, and database migration, and verify the migration test passes on the existing database version
- [x] 1.2 Add schedule repository over the DAO exposing template read, window upsert and delete, and switch toggle, and verify repository unit tests pass for all CRUD paths
- [x] 1.3 Document the schedule tables and switch semantics in the data-model doc and verify the documented schema matches the entity fields

## 2. Schedule domain rules

- [x] 2.1 Implement week-wide overlap validation expanding windows to absolute weekly minutes, and verify unit tests pass for overnight spans, adjacent windows, and colliding saves
- [x] 2.2 Implement due-window resolution for a given clock time honoring start-day ownership and the master switch, and verify unit tests pass for overnight coverage, off days, and paused state
- [x] 2.3 Implement window duration computation from start and end minutes, and verify unit tests pass for same-day, overnight, and full-day preset windows

## 3. Automatic firing worker

- [x] 3.1 Implement the periodic schedule-check worker that reads the switch and template and invokes activation for due windows, and verify worker unit tests pass for fire, skip-on-live-session, and switch-off paths
- [x] 3.2 Post the scheduled-start notice through the existing notification channel naming the fired rule, and verify the notice test asserts the rule name in the posted text
- [x] 3.3 Re-arm the schedule-check worker from BootReceiver alongside existing timers, and verify the receiver test asserts scheduling after boot

## 4. Setup flow branch and editor UI

- [x] 4.1 Add Schedule and One-shot mode choice after keyword selection converging on confirmation, and verify navigation tests pass for both paths to the confirmation route
- [x] 4.2 Build the template editor with per-day window lists, time pickers bounded to 00:00 to 23:59, full-day preset, and overlap warning on refused saves, and verify UI tests pass for add, edit, preset fill, and refused overlap
- [x] 4.3 Follow existing theme colors, shapes, and typography in all new screens, and verify screenshot review shows no off-theme components
- [x] 4.4 Build the schedule confirmation screen showing the saved weekly summary with a Done control returning to a fresh setup start, and verify the route test covers the confirm route

## 5. Management home

- [x] 5.1 Build the management home listing day Rules with edit and delete plus the master switch, and verify UI tests pass for edit, delete, pause, and resume
- [x] 5.2 Wire setup-adjacent entries on the setup start screen and the session complete screen to the management home, and verify navigation tests reach the home from both entries

## 6. Integration checks

- [x] 6.1 Run the full scheduled-session journey on device from template save through automatic fire to identical completion, and verify lock, notice, expiry, and unlock all behave like manual sessions
- [x] 6.2 Run the full unit and UI test suites with lint and typecheck, and verify zero failures before handoff
