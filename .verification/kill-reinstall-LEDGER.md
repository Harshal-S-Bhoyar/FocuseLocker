# Verification ledger — kill running app on device, then install latest build
## Spec
- AC1: running com.focuslock process on device is dead (pidof empty after force-stop)
- AC2: fresh assembleDebug APK built from current tree
- AC3: APK installs with Success
- AC4: installed package matches the built APK (pm path present)
## Critical paths
- P1 [happy] confirm running → force-stop → confirm dead → build → install → verify (AC1-AC4)
- P2 [error] install rejected (USER_RESTRICTED) → report, no fake PASS (AC3)
## Commands
- kill: `adb shell am force-stop com.focuslock` + `adb shell pidof com.focuslock`
- build: `./gradlew.bat :app:assembleDebug`
- install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- verify: `adb shell pm path com.focuslock`
- baseline: app was running (pid 21862, MainActivity resumed) at end of prior task
## Adversarial cases
| id | path | category | attacks | expected (AC) | test id | result |
| A1 | P1 | state | process actually dead, not just backgrounded | AC1 | pidof empty | pending |
| A2 | P2 | error | MIUI install prompt blocks reinstall | AC3 | install output | pending |
## Traces
| path | input | branches | state | actual | expected | ✅/❌ |
| n/a (ops task) | — | — | — | — | — | — |
## Runs (latest after last edit)
| command | exit | passed/failed | key lines |
| pidof → force-stop → pidof | 0 | 21862 → empty, BUT service rebound (pid 6723, PROC_STATE_PERSISTENT) | kill did not hold |
| settings get secure enabled_accessibility_services | 0 | only com.focuslock/...FocusAccessibilityService enabled | — |
| settings delete + force-stop + 10s + pidof | 0 | Deleted 1 row; get=null; pidof empty (stays dead) | session truly dead |
| assembleDebug | 0 | BUILD SUCCESSFUL, APK 22:20 latest | — |
| adb install -r | 0 | Success; base.apk present at fresh path | — |
## Repair log
| R# | failure | root cause (line + why) | fix |
| R1 | force-stop didn't hold (user: still live) | enabled a11y service bound PROC_STATE_PERSISTENT; OS rebinds instantly | user-approved: disabled service via settings delete, re-killed, verified dead+stays |
## Verdict: PASS — session dead and staying dead; latest build installed
