# Data Model & Schema
## FocusLock — Android App
**Version:** 1.0 (MVP)

---

## Overview

All data is stored locally on the device using Room (SQLite). No user data is stored on the backend server. The backend only holds OTP hashes temporarily in memory (max 10 minutes).

---

## 1. Entities

### 1.1 Session
Stores the current and past focus sessions.

```
Table: sessions

id                  INTEGER     PRIMARY KEY AUTOINCREMENT
status              TEXT        NOT NULL  -- ACTIVE | COMPLETED | CANCELLED
started_at          INTEGER     NOT NULL  -- Unix timestamp (milliseconds)
expires_at          INTEGER     NOT NULL  -- Unix timestamp (milliseconds)
unlocked_early      INTEGER     NOT NULL DEFAULT 0  -- 0 = false, 1 = true
unlock_requested_at INTEGER     NULLABLE  -- Unix timestamp if unlock was requested
created_at          INTEGER     NOT NULL  -- Unix timestamp
```

**Rules:**
- Only one session can have `status = ACTIVE` at any time
- `expires_at` is the single source of truth for when blocking ends
- On reboot, app queries for `status = ACTIVE AND expires_at > currentTime`

---

### 1.2 BlockedKeyword
Stores custom keywords added by the user.

```
Table: blocked_keywords

id          INTEGER     PRIMARY KEY AUTOINCREMENT
keyword     TEXT        NOT NULL UNIQUE
is_active   INTEGER     NOT NULL DEFAULT 1  -- 0 = disabled, 1 = enabled
created_at  INTEGER     NOT NULL
```

**Rules:**
- Keywords are stored in lowercase — matching is case-insensitive
- Maximum 50 rows for MVP
- Deleted keywords are hard-deleted (no soft delete for MVP)

---

### 1.3 BlockedCategory
Stores which preset content categories are enabled.

```
Table: blocked_categories

id            INTEGER   PRIMARY KEY AUTOINCREMENT
category_key  TEXT      NOT NULL UNIQUE
-- Values: SOCIAL_MEDIA | ADULT | GAMBLING | NEWS | GAMING | SHOPPING
is_active     INTEGER   NOT NULL DEFAULT 0
```

**Rules:**
- Pre-populated on first install with all 6 categories set to `is_active = 0`
- App reads this table to determine which domain blocklist to load into VPN

---

### 1.4 BlockedDomain
Stores the domain blocklist used by the VPN service.

```
Table: blocked_domains

id            INTEGER   PRIMARY KEY AUTOINCREMENT
domain        TEXT      NOT NULL UNIQUE
category_key  TEXT      NOT NULL  -- Foreign key to blocked_categories.category_key
```

**Rules:**
- Pre-populated from a bundled JSON file at first install
- Read-only at runtime — user cannot add/remove domains directly
- VPN service loads only domains where the matching `category_key` has `is_active = 1`
- This table can have 5,000–20,000 rows — index on `domain` is required

---

### 1.5 TrustedContact
Stores the verified trusted contact phone number.

```
Table: trusted_contacts

id              INTEGER   PRIMARY KEY AUTOINCREMENT
phone_number    TEXT      NOT NULL
is_verified     INTEGER   NOT NULL DEFAULT 0  -- 0 = unverified, 1 = verified
verified_at     INTEGER   NULLABLE  -- Unix timestamp
created_at      INTEGER   NOT NULL
```

**Rules:**
- Maximum 1 row for MVP (single trusted contact)
- Phone number stored as plain text with country code (e.g. +919876543210)
- `is_verified = 1` required before a session can be activated
- Can only be updated when no session is `ACTIVE`

---

### 1.6 UnlockRequest
Stores early unlock attempts for audit trail.

```
Table: unlock_requests

id              INTEGER   PRIMARY KEY AUTOINCREMENT
session_id      INTEGER   NOT NULL  -- Foreign key to sessions.id
requested_at    INTEGER   NOT NULL  -- Unix timestamp
status          TEXT      NOT NULL  -- PENDING | OTP_SENT | OTP_VERIFIED | COOLDOWN | COMPLETED | FAILED | EMERGENCY
otp_attempts    INTEGER   NOT NULL DEFAULT 0
cooldown_ends_at INTEGER  NULLABLE  -- Unix timestamp when 10-min cooldown ends
```

**Rules:**
- One row per unlock attempt per session
- `otp_attempts` max value is 3 — after 3 failures, status goes to FAILED and new request required
- Emergency bypass sets `status = EMERGENCY` and `cooldown_ends_at = now + 24 hours`

---

### 1.7 AppSettings
Stores app-level configuration as key-value pairs.

```
Table: app_settings

key     TEXT    PRIMARY KEY
value   TEXT    NOT NULL
```

**Default rows:**

| key | value | description |
|---|---|---|
| onboarding_complete | false | Set to true after first onboarding |
| device_admin_active | false | Mirrors actual Device Admin state |
| accessibility_active | false | Mirrors actual Accessibility Service state |
| vpn_active | false | Mirrors actual VPN state |
| first_install_date | null | Unix timestamp |

---

## 2. Indexes

```sql
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_expires_at ON sessions(expires_at);
CREATE INDEX idx_blocked_domains_domain ON blocked_domains(domain);
CREATE INDEX idx_blocked_domains_category ON blocked_domains(category_key);
CREATE INDEX idx_unlock_requests_session ON unlock_requests(session_id);
```

---

## 3. Entity Relationships

```
sessions (1) ──────────── (many) unlock_requests
                                    │
blocked_categories (1) ─── (many) blocked_domains
                                    
trusted_contacts (1) ──── referenced by unlock_requests.phone_number
                           (not a formal FK — stored separately)
```

---

## 4. Backend (Temporary In-Memory Only)

The backend does not use a persistent database. OTPs are stored in server memory with a TTL.

```javascript
// In-memory store structure (Node.js)
otpStore = {
  "+919876543210": {
    otpHash: "bcrypt hash of OTP",
    expiresAt: timestamp,
    type: "CONTACT_VERIFY" | "SESSION_UNLOCK",
    attempts: 0
  }
}
```

**TTL:** 10 minutes
**Max attempts:** 3
**After expiry or max attempts:** Entry deleted from memory
**Nothing is written to disk on the server**
