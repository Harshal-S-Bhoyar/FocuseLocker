# Tasks

## 1. Countdown plus first paint

- [x] 1.1 Hoist the timer text into a leaf subscriber and verify per-second ticks skip the surrounding session tree via composition counts
- [x] 1.2 Defer Lottie composition plus gradient blend past first paint with text already visible and verify first frame lands without GPU stall warnings

## 2. Lists plus matching

- [x] 2.1 Add stable keys plus content types to schedule plus category plus app lists and verify DB emissions rebind only changed rows with scroll preserved
- [x] 2.2 Move URL matching to a single-flight background worker with a revision-versioned cached domain set and verify verdicts equal uncached results under burst events

## 3. Measurement loop

- [x] 3.1 Run the device loop (Displayed plus init-to-content plus gfxinfo framestats) per change on release builds and verify p95 launch under 4s with zero missed vsync on countdown
- [x] 3.2 Keep unit suites green across the change and verify no behavior delta in blocking verdicts or schedule firing
