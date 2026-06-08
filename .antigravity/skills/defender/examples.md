# Defender Examples

## Example 1: Full Automated Audit

**User Request:**
"Please run a security audit on the `src/` directory and give me patches for any issues found."

**Defender Workflow:**
1. Execute threat modeling: `/defender threat-model src/`
2. Scan focus areas: `/defender scan src/`
3. Triage the findings: `/defender triage VULN-FINDINGS.json --repo src/`
4. Generate patches: `/defender patch TRIAGE.json --repo src/`
5. Report generation: `/defender report TRIAGE.json --repo src/`

## Example 2: Analyzing a Specific File

**User Request:**
"Review `auth_handler.py` for vulnerabilities."

**Defender Workflow:**
1. The agent bypasses full threat modeling and runs a targeted scan:
   `/defender scan src/auth_handler.py`
2. Generates `VULN-FINDINGS.json`.
3. Agent analyzes the findings directly or runs triage:
   `/defender triage VULN-FINDINGS.json --repo src/`

## Example 3: Patching an Existing Vulnerability Report

**User Request:**
"Here is a penetration test report in `pentest.json`. Generate fixes for the issues."

**Defender Workflow:**
1. Treat `pentest.json` as a triage artifact.
2. Run patch generation:
   `/defender patch pentest.json --repo src/`
3. Provide the user with the generated unified diffs from the `PATCHES/` directory.
