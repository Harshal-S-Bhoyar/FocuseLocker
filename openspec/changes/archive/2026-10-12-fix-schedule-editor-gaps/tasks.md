# Tasks

## 1. Row-tap edit

- [x] 1.1 Add row-tap handler that loads the window id plus day plus start plus end into dialog state and verify tapping a saved row opens the dialog prefilled with that window's values
- [x] 1.2 Route dialog save through `saveWindow` with the loaded id instead of null and verify editing a window updates the row without creating a duplicate

## 2. Error hygiene

- [x] 2.1 Clear the ViewModel error on dialog cancel plus dialog dismiss plus successful save and verify no stale overlap warning remains on the list after cancel
- [x] 2.2 Keep validation failures visible in the dialog and on the list and verify a rejected overlap still shows its warning until the next open or successful save
