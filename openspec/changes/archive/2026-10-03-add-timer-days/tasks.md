# Tasks

## 1. Timer picker with days + rollover

- [x] 1.1 Add a `days` flow (0-7) to `TimerSetupViewModel` with `updateDays`, cap hours at 23 in `updateHours`, and implement rollover (23h +1 → 0h/days+1) with symmetric borrow (0h −1 with days>0 → 23h/days−1), refusing any change past 7 days, verify unit tests for rollover, borrow, 7-day edge, and 0-minute invalid pass
- [x] 1.2 Add the Days `NumberPicker` to `TimerSetupScreen` reusing the existing component and derive millis as days*1440 + hours*60 + minutes bounded to 1..10080, verify manual check: 23h +1 shows 0h/1d, 2d 3h yields the correct millis, sub-24h entry behaves exactly as before
- [x] 1.3 Update `TimerSetupViewModelTest` for the days flow, rollover/borrow cases, and duration math, verify `./gradlew :app:testDebugUnitTest --tests "com.focuslock.ui.setup.TimerSetupViewModelTest"` green

## 2. Confirmation days display + verification

- [x] 2.1 Extend `SessionConfirmationScreen` to render days ("2 days, 3 hours", omitting zero units) via a new `session_duration_days` plural following the existing H/M pattern, verify manual check on a 360dp-wide device that the summary reads naturally for 0d, 1d, and 7d sessions
- [x] 2.2 Run full `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug`, verify 0 failures/errors and no new lint findings in touched files
