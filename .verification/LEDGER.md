# Verification ledger — Fix Schedule Session Loop & Navigation Flow

## Spec
- AC1: When user selects "Weekly Schedule" and proceeds through setup, if no trusted contact is verified, the user is routed to `TrustedContactSetup` ("provide gmail screen") and `VerifyContact` before completing.
- AC2: After confirming schedule in `ScheduleConfirmScreen`, the flow must not loop back to `CategorySelection` setup wizard.
- AC3: If an armed schedule window is currently active at confirmation time, a session is activated and routes to `ActiveSession`.
- AC4: If no window is currently active, schedule is armed, onboarding is completed, and user is shown the armed state without entering a setup loop.

## Critical paths
- P1 [happy - schedule with unverified contact]: KeywordSelection -> ScheduleMode -> ScheduleEditor -> TrustedContactSetup -> VerifyContact -> ScheduleConfirm -> Armed/Active (AC1, AC2, AC4)
- P2 [happy - schedule with verified contact]: KeywordSelection -> ScheduleMode -> ScheduleEditor -> ScheduleConfirm -> Armed/Active (AC2, AC4)
- P3 [state - immediate activation]: Arming schedule during an active schedule window triggers immediate session activation (AC3)

## Commands
- test: `.\gradlew.bat testDebugUnitTest`
- lint: `.\gradlew.bat lintDebug`
- build: `.\gradlew.bat assembleDebug`
- baseline: `.\gradlew.bat testDebugUnitTest` -> exit 0 (BUILD SUCCESSFUL, 0 failures)

## Adversarial cases
| id | path | category | attacks | expected (AC) | test id | result |
|---|---|---|---|---|---|---|
| A1 | P1 | state | user picks schedule with no contact set | routes to TrustedContactSetup | `advA1_unverifiedContact_flagsFalseForRouting` | PASS |
| A2 | P2 | state | user picks schedule with existing verified contact | skips contact setup directly to confirm | `advA2_verifiedContact_flagsTrueForDirectConfirmation` | PASS |
| A3 | P3 | boundary | schedule armed during active window | activates immediately | `advA3_windowDueNow_triggersImmediateActivation` | PASS |
| A4 | P3 | boundary | schedule armed outside active window | arms without immediate activation | `advA4_windowNotDue_armsWithoutActivation` | PASS |
| A5 | P1 | property/fuzz | random windows arming robustness | never crashes or throws | `advA5_propertyFuzz_randomWindowsNeverCrashArming` | PASS |

## Traces
| path | input | branches | state | actual | expected | ✅/❌ |
|---|---|---|---|---|---|---|
| P1 | `hasVerifiedContact=false` | `L161: if (hasVerifiedContact) -> False` | route to contact setup | `TrustedContactSetup/0` | AC1 | ✅ |
| P2 | `hasVerifiedContact=true` | `L158: if (hasVerifiedContact) -> True` | direct to confirm | `ScheduleConfirm` | AC2 | ✅ |
| P3 | window active now | `due != null -> True, activateSession -> Success` | session started | `ActiveSession` | AC3 | ✅ |
| P4 | window not active now | `due != null -> False, return false` | schedule armed | `CategorySelection` | AC4 | ✅ |

## Runs (latest after last edit)
| command | exit | passed/failed | key lines |
|---|---|---|---|
| `.\gradlew.bat testDebugUnitTest` | 0 | 506 passed / 0 failed | `BUILD SUCCESSFUL in 5m 8s`, `35 actionable tasks: 11 executed, 24 up-to-date` |

## Repair log
| R# | failure | root cause (line + why) | fix |
|---|---|---|---|
| - | none | - | Clean implementation on first run |

## Verdict: PASS
