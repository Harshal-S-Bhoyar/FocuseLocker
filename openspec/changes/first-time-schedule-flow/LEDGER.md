# Verification ledger — first-time-schedule-flow
## Spec
- AC1: Unverified first-time Keywords leads to contact+OTP then chooser; verified skips to chooser
- AC2: Editor Done guard routes unverified to contact then confirm
- AC3: Both branches reach the single confirm; lock-in arms with immediate start when live
- AC4: Category shows banner only, no manage entry or dead routes
- AC5 (regression): One-shot timer flow byte-identical
## Critical paths
- P1 [happy] verified keywords to chooser (AC1)
- P2 [happy] unverified keywords to contact to chooser (AC1)
- P3 [state] guard contact to confirm (AC2)
- P4 [boundary] -1/0/positive verify returns (AC1, AC2, AC5)
- P5 [regression] one-shot unchanged (AC5)
## Commands
- test: `.\gradlew.bat :app:testDebugUnitTest`
- lint: `.\gradlew.bat :app:lintDebug`
- build: `.\gradlew.bat :app:assembleDebug`
- baseline: 529 green pre-change (fix-battery-drain landing)
## Adversarial cases
| id | path | category | attacks | expected (AC) | test id | result |
| A1 | P1 | basic | verified next | ScheduleMode route | chooser verified | PASS |
| A2 | P2 | basic | unverified next | TrustedContactSetup/-1 | chooser unverified | PASS |
| A3 | P4 | boundary | -1 return | chooser + Keyword popUpTo | pre-chooser return | PASS |
| A4 | P4 | boundary | 0 return | confirm + Category popUpTo | guard return | PASS |
| A5 | P4 | boundary | positive return | confirmation/duration | one-shot return | PASS |
| A6 | P4 | property | durations incl MAX_VALUE | duration preserved, never blank | positive durations | PASS |
| A7 | P1/P2 | state | VM flag both states | false then true | keywords flags | PASS |
## Traces
| path | input | branches | state | actual | expected | match |
| P1 | true | chooserOrContactRoute true-branch | ScheduleMode | ScheduleMode | AC1 | ✅ |
| P2 | false | false-branch | TrustedContact/-1 | TrustedContact/-1 | AC1 | ✅ |
| P3 | guard Done unverified | existing editor branch | TrustedContact/0 | TrustedContact/0 | AC2 | ✅ |
| P4 | -1/0/+ | verifyNav 3-way | correct dest+popUpTo | correct | AC1/2/5 | ✅ |
| P5 | one-shot | untouched branch | identical | identical | AC5 | ✅ |
## Runs (latest after last edit)
| command | exit | passed/failed | key lines |
| targeted 4 suites | 0 | green | BUILD SUCCESSFUL in 4m 25s |
| full + lint + assemble | 0 | 542 passed / 0 failed | BUILD SUCCESSFUL in 6m 57s |
## Repair log
| R# | failure | root cause (line + why) | fix |
| - | none | - | clean run, no repairs |
## Verdict: NOT VERIFIED — 4.1 device walk pending (device offline)
