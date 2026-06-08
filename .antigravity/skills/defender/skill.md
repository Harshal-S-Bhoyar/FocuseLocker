---
name: defender
description: >-
  Comprehensive security analysis and defense skill covering Threat Modeling, Vulnerability Discovery, Security Review, Triage, Exploit Validation, Patch Generation, and Security Reporting. 
argument-hint: "<target-dir> [threat-model|scan|triage|validate|patch|report] [flags]"
---

# Defender Skill

The Defender skill is a comprehensive, Antigravity-native security analysis workflow adapted from the Anthropic Defending Code methodology. It provides a complete end-to-end security pipeline for identifying, validating, and patching vulnerabilities in software systems.

## Core Capabilities

The Defender agent orchestrates the following security lifecycle:
1. **Threat Modeling:** Map the attack surface and identify trust boundaries.
2. **Vulnerability Scanning:** Perform static analysis for security flaws based on focus areas.
3. **Vulnerability Triage:** Deduplicate, verify, and rank findings by severity.
4. **Exploit Validation:** Assess preconditions, attack paths, and impact.
5. **Patch Generation:** Provide root-cause analysis, generate minimal unified diffs, and ensure no regressions.
6. **Reporting:** Generate executive summaries and technical remediation details.

## How to Activate

Call Defender with the target directory and the specific phase you want to execute:
- `/defender threat-model <target-dir>`
- `/defender scan <target-dir>`
- `/defender triage <findings.json> --repo <target-dir>`
- `/defender patch <triage.json> --repo <target-dir>`
- `/defender report <triage.json> --repo <target-dir>`

Alternatively, trigger the full automated pipeline via the defender agent:
`@defender-agent audit <target-dir>`

## Reference Documentation

- [Rules & Constraints](rules.md) - Security constraints, static-only rules, and methodologies.
- [Workflows](workflows.md) - Detailed step-by-step logic for each phase of the pipeline.
- [Checklists](checklists.md) - Specific threat checklists for Web, API, Python, AI, RAG, and Auth systems.
- [Examples](examples.md) - Example usage scenarios and integrations.

## Safety Preamble

**STATIC ANALYSIS ONLY.** Defender performs static reasoning and analysis over source code. It does NOT build, execute, fuzz, or modify the target codebase directly without explicit authorization. Defender writes output reports and inert `.diff` files. See `rules.md` for full constraints.
