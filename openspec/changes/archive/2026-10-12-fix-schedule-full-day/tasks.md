# Tasks

## 1. Engine sentinel

- [x] 1.1 Widen validation to accept `endMinutes` of `1440` while keeping `startMinutes` in `0..1439` plus `start == end` rejected and verify `ScheduleEngineTest` covers accept `1440`, reject `1441`, and full-day duration of `1440`
- [x] 1.2 Confirm overlap expansion plus `dueWindow` plus `remainingMinutes` cover minute `1439` for `0..1440` and verify a full-day window collides with any same-week window and stays due at `23:59` with 1 minute remaining

## 2. Preset plus display

- [x] 2.1 Rewire the Full-day preset to save `0..1440` and render the end as `24:00` and verify tapping Full-day then saving stores the sentinel and shows the full-day row
- [x] 2.2 Keep hand-typed ends unchanged and verify `23:59` typed by hand still behaves as before with the preset as the only full-day path
