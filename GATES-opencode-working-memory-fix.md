# GATES — permanent fix: opencode-working-memory v1 incompatible with OpenCode v2

## G1 — Incompatible plugin reference removed — PASS
- CHECK: read `<user-config-dir>/opencode/opencode.jsonc:5`
- EVIDENCE: line 5 is `"plugin": ["C:\\ProgramData\\gsd-core\\.opencode\\plugins\\gsd-core.js"]`, no `opencode-working-memory` match

## G2 — Service running on current run — PASS
- CHECK: `opencode service status`
- EVIDENCE: returned `http://127.0.0.1:49374`, current run `a8867749` active in server log

## G3 — Plugin list shows no failed working-memory entry — PASS
- CHECK: `opencode plugin list`
- EVIDENCE: rows are `enforce-output-style`, `enforce-implementation`, `graphify` only; no `opencode-working-memory` row

## G4 — Fresh server log has no new working-memory load failure — PASS
- CHECK: grep `run=a8867749 message="failed to load plugin"` in `opencode.log`
- EVIDENCE: 2 matches, both `target=enforce-output-style.js`; zero matches for `target=opencode-working-memory` in run `a8867749`. Last real working-memory failure is `2026-10-11T09:50:30` run `f80ce1a5` (pre-fix).
