# FocusLock Industry Standards Checklist (MVP)

## 1. Architecture

### Android

* MVVM + Clean Architecture
* Single Responsibility Principle
* Dependency Injection (Hilt)
* Repository Pattern
* UseCase per business action
* Immutable UI State
* StateFlow over LiveData
* No business logic in UI
* No Android imports in Domain layer

### Backend

* Layered Architecture
* Routes → Services → Repository/Store
* DTO-based contracts
* Centralized error handling
* Configuration isolation

---

## 2. Security

### Mobile

* HTTPS only
* No hardcoded secrets
* Proguard/R8 enabled
* Certificate pinning (recommended)
* Secure local storage
* No sensitive logs
* Input validation everywhere

### Backend

* OWASP API Top 10 compliant
* Rate limiting
* Request validation (Zod)
* Security headers (Helmet)
* CORS restrictions
* Environment-based secrets
* OTP hashing
* OTP expiration
* Brute-force protection

---

## 3. API Standards

* Consistent response format
* Proper HTTP status codes
* DTO versioning
* Request validation
* Response validation
* Timeout handling
* Retry strategy
* Idempotent verification endpoints
* Structured error messages

Example:

{
"success": true,
"data": {},
"message": "OTP sent"
}

{
"success": false,
"error": {
"code": "OTP_SEND_FAILED",
"message": "Unable to send OTP"
}
}

---

## 4. Android Performance

* No memory leaks
* No Context leaks
* No GlobalScope
* Structured concurrency
* Lifecycle-aware collection
* Lazy loading
* Database indexing
* WorkManager for background jobs
* Foreground services only when required

---

## 5. Compose Standards

* Stateless composables
* State hoisting
* Stable models
* Avoid unnecessary recompositions
* rememberSaveable where needed
* Navigation arguments typed
* Material 3 compliance

---

## 6. Database Standards

### Room

* Indexed frequently queried fields
* Transactions where required
* Migrations tested
* Flow-based reads
* No main-thread queries

### Data Integrity

* Foreign keys
* Constraints
* Unique indexes
* Soft delete where appropriate

---

## 7. OTP System Standards

* Secure random generation
* OTP hash storage
* 5–10 min expiry
* Single-use OTP
* Attempt limits
* Rate limiting
* Replay protection
* Audit logging
* Delivery retry handling

---

## 8. Email (Brevo) Standards

* Verified sender domain
* SPF configured
* DKIM configured
* DMARC configured
* Bounce monitoring
* Delivery monitoring
* API failure handling
* Retry strategy
* Template-based emails

---

## 9. Accessibility Service Standards

* Minimal permissions
* Battery optimization handling
* Graceful service recovery
* Accessibility node null safety
* Browser compatibility testing
* No ANR risk

---

## 10. VPN Service Standards

* DNS leak prevention
* Service restart recovery
* Battery optimization handling
* Network change handling
* Low memory handling

---

## 11. Device Admin Standards

* Clear user disclosure
* Proper activation flow
* Safe deactivation flow
* Reboot persistence
* Session integrity checks

---

## 12. Error Handling

* No silent failures
* No generic catch blocks
* User-friendly messages
* Developer logs separated
* Crash reporting
* Retry mechanisms

---

## 13. Logging & Monitoring

### Development

* Timber
* Structured logs
* Network logging interceptor

### Production

* Crashlytics
* ANR monitoring
* Backend monitoring
* Request tracing

---

## 14. Testing Standards

### Android

* 80%+ domain coverage
* ViewModel tests
* Repository tests
* Integration tests
* UI tests

### Backend

* Route tests
* Service tests
* OTP tests
* Validation tests
* Integration tests

---

## 15. CI/CD

* Lint checks
* Unit tests
* Static analysis
* Dependency scanning
* Build verification
* Release signing validation

---

## 16. Production Readiness

* Offline handling
* Network recovery
* App restart recovery
* Reboot recovery
* Data migration strategy
* Monitoring dashboards
* Alerting setup
* Backup strategy

---

## 17. Code Quality

* SOLID
* DRY
* KISS
* Clean Code
* No dead code
* No duplicate logic
* Consistent naming
* Documentation coverage

---

## 18. Release Gate (Must Pass)

□ No Critical Bugs
□ No High Security Findings
□ No Crash Loops
□ No Memory Leaks
□ No API Contract Mismatches
□ No Secret Exposure
□ OTP Flow Verified
□ Email Flow Verified
□ Accessibility Tested
□ VPN Tested
□ Device Admin Tested
□ Recovery Tested
□ Release Build Tested
□ Monitoring Enabled
□ CI/CD Passing
