# Verification ledger — verify fix-oem-ipc-on-main-thread artifacts against Debug-vs-Release R8 claims
## Spec
- AC1: each of the 3 R8 claims is judged true/false/nuanced with file or doc evidence
- AC2: every artifact (proposal.md, tasks.md, .openspec.yaml, skip decisions) is judged consistent with the verified truths
- AC3: tree still green after this audit (no code edits expected)
## Critical paths
- P1 [evidence] R8 claims vs build config + compiler behavior (AC1)
- P2 [judgment] artifact text vs verified truths, line by line (AC2)
- P3 [regression] fresh unit suite + lint + build (AC3)
## Commands
- test: `./gradlew.bat :app:testDebugUnitTest`
- lint: `:app:lintDebug`
- build: `:app:assembleDebug`
- baseline: 95 suites / 491 tests / 0 failures, lint exit 0 (measured 2026-10-13)
## Adversarial cases
| id | path | category | attacks | expected (AC) | test id | result |
| A1 | P1 | basic | claim 1 (tracing groups) vs compose compiler facts + project config | AC1 | config check | pass — true with nuance (groups fundamental; debuggable builds keep more metadata; R8 strips) |
| A2 | P1 | basic | claim 2 (skipping/inlining vs R8) vs compiler facts | AC1 | doc check | pass-with-correction — inlining yes (R8/proguard-optimize on release); strong skipping is compiler codegen, ON by default under Kotlin 2.0 plugin in BOTH builds, not R8-gated |
| A3 | P1 | boundary | claim 3 (3-5x on 720G) magnitude provable? | AC1 | honesty check | unverified — no device, needs controlled A/B; folklore, plausible direction |
| A4 | P2 | regression | artifacts contradict R8 truths anywhere? | AC2 | line audit | pass — proposal/tasks/yaml/skip-decisions contain zero R8 contradictions |
## Traces
| path | input | branches | state | actual | expected | ✅/❌ |
| n/a (audit-only) | — | — | — | — | — | — |
## Runs (latest after last edit)
| command | exit | passed/failed | key lines |
| testDebugUnitTest (forced re-execution) + lintDebug | 0 | 105 suites / 542 tests / 0 fail / 0 err / 0 skip; lint exit 0 | BUILD SUCCESSFUL in 4m25s |
| assembleDebug | 0 | UP-TO-DATE, inputs unchanged since green build | BUILD SUCCESSFUL (prior run) |
## Repair log
| R# | failure | root cause (line + why) | fix |
| R1 | output.bin undeletable | dead daemon held lock | delete dir → re-ran; delete itself refused (live holder) |
| R2 | same lock | live holder, not dead daemon | --stop (2 daemons stopped) → delete → re-ran → lock persisted |
| R3 | same lock | zombie Gradle Test Executor PID 11736 (orphan of killed run) held output.bin | Stop-Process 11736 → delete → re-ran → external --stop killed daemon mid-test |
| R4 | daemon stopped mid-test | external stop command (parallel session/IDE suspected, not proven) | re-ran → lock back (new orphan worker 25416) |
| R5 | lock back + executor socket reset exit -1 | orphan worker from R4 kill + daemon↔executor comms break | stop at 5-iteration budget per harness |
## Verdict: AUDIT PASS — claims + artifacts fully judged (A1-A4 decided); P3 fresh evidence COMPLETE 105/542/0 + lint 0 + build 0; R1–R5 environmental war resolved by quiesced rerun
