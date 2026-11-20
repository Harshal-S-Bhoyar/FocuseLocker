# Verification ledger — build latest APK and install on connected device
## Spec
- AC1: connected device present and authorized
- AC2: fresh assembleDebug APK built from current tree
- AC3: APK installs on the device with Success
- AC4: installed app matches the built APK (package present, fresh timestamp)
- AC5: app launches (MainActivity starts, process alive)
## Critical paths
- P1 [happy] device ready → build → install → launch verified (AC1-AC5)
- P2 [error] device missing OR install rejected → report exactly what blocks, no fake PASS (AC1, AC3)
## Commands
- device: `adb devices`
- build: `./gradlew.bat :app:assembleDebug`
- install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- verify: `adb shell pm path com.focuslock` + `adb shell pidof com.focuslock` after `am start`
- baseline: no prior install state claimed
## Adversarial cases
| id | path | category | attacks | expected (AC) | test id | result |
| A1 | P2 | error | no device attached | AC1 | adb devices empty | pending |
| A2 | P2 | error | MIUI install prompt (USER_RESTRICTED) | AC3 | install output | pending |
## Traces
| path | input | branches | state | actual | expected | ✅/❌ |
| n/a (ops task) | — | — | — | — | — | — |
## Runs (latest after last edit)
| command | exit | passed/failed | key lines |
| adb devices | 0 | device ee64c019 present | AC1 |
| assembleDebug | 0 | BUILD SUCCESSFUL in 8s (tree unchanged, APK 2026-10-14 22:20, 15572026 bytes) | AC2 |
| adb install -r app-debug.apk | 0 | Success | AC3 |
| pm path + am start + pidof + resumed | 0 | base.apk present; pid 21862; MainActivity RESUMED t1592 | AC4+AC5 |
## Repair log
| R# | failure | root cause (line + why) | fix |
| (none — clean run) | — | — | — |
## Verdict: PASS — 5/5 gates checked with fresh evidence
