# Verification ledger — validate the other agent's UI-lag report claim by claim
## Spec
- AC1: every numbered claim in the report is checked against real files/commands, verdict per claim
- AC2: each proposed fix is judged safe/useful/risky with a reason tied to code or docs
- AC3: no code changed by this validation task (audit-only)
## Critical paths
- P1 [evidence] each claim maps to a file:line or a command output (AC1)
- P2 [judgment] each proposed fix gets a verdict: do / do-not-do / needs-proof (AC2)
## Commands
- baseline: no commands run yet; device currently disconnected (adb: no devices/emulators found)
- code reads only; no build/test run needed for an audit-only task
## Adversarial cases
| id | path | category | attacks | expected (AC) | test id | result |
| A1 | P1 | regression | report numbers invented? | AC1 | math + consistency check | pending |
| A2 | P1 | api-misuse | android:process on AccessibilityService legal? | AC1 | docs/code check | pending |
| A3 | P1 | basic | cited files/lines exist as described? | AC1 | file reads | pending |
## Traces
| path | input | branches | state | actual | expected | ✅/❌ |
| n/a (audit-only, no code paths executed) | — | — | — | — | — | — |
## Runs (latest after last edit)
| command | exit | passed/failed | key lines |
| audit-only; code reads + math checks, no build needed | n/a | 9 claims checked | device disconnected, gfxinfo not re-measurable |
## Repair log
| R# | failure | root cause (line + why) | fix |
| (audit-only; no repairs — validation report issued instead) | — | — | — |
## Verdict: AUDIT COMPLETE — 2 claims actionable (1 with correction), 2 inflated, 2 mechanically wrong, 1 dangerous fix refused, device numbers unverified
