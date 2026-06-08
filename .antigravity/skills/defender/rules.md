# Defender Rules & Constraints

## 1. Safety & Execution Constraints
- **No Execution:** This skill performs static analysis only. It reads source code, git history, and vulnerability reports, but it MUST NOT build, execute, fuzz, or dynamically probe the target.
- **No Network Requests:** Do not make network requests against the target's infrastructure.
- **Read-Only Codebase:** Never edit target source files directly. Generate candidate fixes as unified diffs written to a `PATCHES/` directory.
- **Containment:** Stay within `<target-dir>`. Do not traverse path boundaries (e.g., `../`).

## 2. Methodology Rules

### Threat Modeling
- Focus on *threats* (what could go wrong, who would do it), not just known vulnerabilities.
- Identify trust boundaries, assets, and entry points before searching for bugs.
- Always output `THREAT_MODEL.md` as the source of truth for the scan phase.

### Vulnerability Discovery
- Do not fabricate line numbers. Every `file:line` cited MUST be verified via Glob, Read, or Grep tools.
- Do not report low-value noise as high severity (e.g., volumetric DoS, missing hardening without an exploit path, outdated dependencies without usage).
- Focus on high-value issues: memory safety, injection, RCE, logic bypass, SSRF, prompt injection, RAG poisoning.

### Triage & Deduplication
- **Duplicate Rule:** Two findings are duplicates if fixing one fixes the other (e.g., same root cause).
- **Severity Rule:** Rank severity based on preconditions. An authenticated flaw with multiple preconditions is Medium/Low. Unauthenticated remote access is High.

### Exploit Validation
- Treat findings as "guilty until proven innocent" during triage. Ask "Can an attacker actually reach this sink with malicious data?"
- Identify exact attack preconditions (e.g., "Requires user to hold Admin role").

### Patch Generation
- **Root Cause First:** Trace backward from the sink to the source of the untrusted data. Patch the root cause, not just the symptom.
- **Variant Hunt:** Always grep for sibling call sites with the same pattern to ensure complete coverage.
- **Minimal Diff:** Provide the smallest change that fixes the root cause. No refactoring, formatting changes, or drive-by cleanups.
- **Adversarial Self-Check:** Actively try to bypass your own proposed patch before emitting it. Think like an attacker facing your fix.

## 3. Allowed Tools
Defender uses Antigravity-native tools:
- `view_file` / `read_file` to inspect code.
- `grep_search` to find patterns, sinks, and variant hunting.
- `list_dir` to enumerate project structure.
- `write_to_file` to output JSON findings, `THREAT_MODEL.md`, `TRIAGE.md`, and patches.
- No direct `bash` execution of target applications. Use `run_command` only for source control queries (e.g. `git log`) if absolutely necessary.
