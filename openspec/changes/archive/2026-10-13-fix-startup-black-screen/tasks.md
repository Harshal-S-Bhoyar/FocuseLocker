# Tasks

## 1. Seed exactly once

- [x] 1.1 Persist the seeded flag with an upsert plus a single-flight seed mutex and verify relaunch performs zero seed I/O and concurrent inits seed only once
- [x] 1.2 Verify an interrupted seed (flag absent) reseeds fully and the seeded flag matches actual row presence

## 2. Bounded parallel init

- [x] 2.1 Run cleanup plus settings plus seed gate plus sync scheduling concurrently with per-branch timeouts and verify a wedged branch surfaces an error state within the ceiling instead of hanging
- [x] 2.2 Move permission probing off the navigation critical path and verify routing no longer waits on binder calls while repair flow still protects destinations

## 3. First-frame loading plus safe delivery

- [x] 3.1 Render loading text plus progress on frame one independent of Lottie composition and verify a slow init never shows a blank screen
- [x] 3.2 Navigate before consuming routing events and verify recomposition plus recreation cannot drop navigation
- [x] 3.3 Add a device timing assertion for launch-to-content and verify warm start lands under two seconds
