# Defender Checklists

These checklists guide the scanning and validation phases of the Defender pipeline based on application type.

## 1. Web Applications & APIs
- [ ] **Injection:** Are SQL queries parameterized? Are ORMs used safely? Are OS commands constructed with attacker input?
- [ ] **Authentication:** Are session tokens secure (HttpOnly, Secure)? Is password hashing strong (Argon2, bcrypt)? Are password resets secure?
- [ ] **Authorization (IDOR):** Does the server check if the requesting user owns the requested resource before returning data or taking action?
- [ ] **XSS/CSRF:** Is output encoded? Are Anti-CSRF tokens present for state-changing requests?
- [ ] **SSRF:** Can the server be forced to make requests to internal resources (e.g., `169.254.169.254` or `localhost`)?

## 2. Python Backend Projects
- [ ] **Deserialization:** Is `pickle`, `yaml.load`, or `eval()` used on untrusted data?
- [ ] **Path Traversal:** Are file operations (`open()`, `os.path.join()`) safe against directory traversal (`../`)?
- [ ] **Secret Exposure:** Are API keys or passwords hardcoded or logged?
- [ ] **Dependency Risks:** Are vulnerable libraries used in exploitable ways?

## 3. AI & Agentic Systems
- [ ] **Prompt Injection:** Can user input alter the core instructions of the LLM? 
- [ ] **RAG Poisoning:** Can an attacker insert malicious content into the knowledge base that the LLM later retrieves and trusts?
- [ ] **Excessive Agency:** Does the agent have access to destructive tools (e.g., database drop, file delete) without human-in-the-loop approval?
- [ ] **Data Exfiltration:** Can prompt injection force the LLM to output sensitive context to an attacker-controlled external server?
- [ ] **Indirect Prompt Injection:** Does the LLM process untrusted external websites or emails that might contain hidden commands?

## 4. Authentication Systems
- [ ] **Brute Force:** Is there rate limiting on login attempts?
- [ ] **Token Validation:** Are JWT signatures validated? Is the `alg` header checked to prevent "none" algorithm attacks?
- [ ] **OAuth Flow:** Is the `state` parameter used and validated to prevent CSRF? Are redirect URIs strictly whitelisted?
