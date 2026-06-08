# Defender Workflows

This document details the step-by-step logic for each phase of the Defender security pipeline. 

## 1. Threat Modeling (`threat-model`)
**Goal:** Map trust boundaries, identify assets, and define threats.
**Output:** `THREAT_MODEL.md`

1. **Reconnaissance:** Enumerate entry points (e.g., API routes, CLI arguments, file uploads, network sockets).
2. **Identify Assets:** List high-value data (e.g., PII, API keys, database credentials).
3. **Determine Trust Boundaries:** Where does untrusted data cross into a trusted context? (e.g., Internet -> App Server, User -> Database).
4. **Threat Generation:** Apply STRIDE (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege) to generate threats.
5. **Output Generation:** Write `THREAT_MODEL.md` defining Assets, Entry Points, and Threats.

## 2. Vulnerability Scanning (`scan`)
**Goal:** Discover security vulnerabilities via static analysis.
**Output:** `VULN-FINDINGS.json` and `VULN-FINDINGS.md`

1. **Scope Definition:** Read `THREAT_MODEL.md` if available to define focus areas. Otherwise, do a quick recon of entry points.
2. **Targeted Review:** Analyze code paths in focus areas, searching for:
   - Memory Safety issues (Buffer overflows, UAF)
   - Injection (SQLi, Command Injection, XSS)
   - AI/Agent Specifics (Prompt injection, RAG poisoning, excessive agent autonomy)
   - Business Logic Flaws (Auth bypass, IDOR)
3. **Trace Paths:** For each potential sink, trace backward to ensure an attacker-controlled entry point can reach it.
4. **Scoring:** Assign severity (High, Medium, Low) and confidence scores. Output to `VULN-FINDINGS.json`.

## 3. Triage & Deduplication (`triage`)
**Goal:** Verify, rank, and deduplicate raw scan findings.
**Output:** `TRIAGE.json` and `TRIAGE.md`

1. **Verification:** Rigorously re-evaluate each finding in `VULN-FINDINGS.json`. Reject false positives and purely theoretical issues with no reachable path.
2. **Deduplication:** Group findings that share the same root cause (e.g., multiple SQLi findings tracing back to the same unparameterized database wrapper).
3. **Re-Ranking:** Adjust severity based on preconditions (e.g., an XSS requiring administrative privileges is downgraded compared to unauthenticated XSS).
4. **Output:** Generate `TRIAGE.json` with only `true_positive` findings.

## 4. Exploit Validation (`validate`)
**Goal:** Determine exact preconditions and exploit paths.
*Note: In the static pipeline, this is handled during Triage and Patching phases through adversarial self-checks.*

1. **Precondition Analysis:** What state must the system be in? What permissions must the attacker hold?
2. **Attack Path Construction:** Outline the exact steps, payloads, and API calls an attacker would use.

## 5. Patch Generation (`patch`)
**Goal:** Provide secure, minimal, unified diffs.
**Output:** `PATCHES/bug_NN/patch.diff` and `PATCHES.md`

1. **Root Cause Analysis:** Identify the exact file and line where validation/sanitization should occur.
2. **Variant Hunt:** Use `grep_search` to find all other instances of the vulnerable pattern.
3. **Generate Diff:** Write a minimal, unified diff. Do not include unrelated cleanups.
4. **Adversarial Self-Check:** Assume the attacker knows about the patch. Can they bypass it with different encoding, path traversal sequences (`....//`), or alternative inputs? If so, revise the patch.
5. **Output:** Write the diff to `PATCHES/bug_NN/patch.diff`.

## 6. Security Reporting (`report`)
**Goal:** Produce an actionable summary for engineering teams.
**Output:** `SECURITY_REPORT.md`

1. **Executive Summary:** High-level overview of the audit scope, critical risks, and overall security posture.
2. **Detailed Findings:** For each verified finding in `TRIAGE.json`:
   - Description and Impact
   - Exploit Scenario
   - Root Cause
   - Remediation (incorporating generated patches)
3. **Strategic Recommendations:** Broader architectural changes needed (e.g., "Implement a centralized input validation middleware").
