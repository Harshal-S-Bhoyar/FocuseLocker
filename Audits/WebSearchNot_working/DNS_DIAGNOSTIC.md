# DNS_PROBE_POSSIBLE — Systematic Debugging Diagnostic

## Phase 1: Root Cause Investigation (FACTS ONLY)

### Error
- **Symptom:** `DNS_PROBE_POSSIBLE` — browser shows "google.com's dns address could not be found"
- **Scope:** ALL domains fail, not just blocked ones
- **Reproducible:** 100% — every DNS query fails when VPN is active
- **Never worked:** User confirmed this VPN DNS pipeline has NEVER worked on device

---

## Root Cause #1: DNS Bootstrap Recursion (CRITICAL — causes DNS_PROBE_POSSIBLE)

### Evidence Chain

**Step 1 — VPN sets system DNS to its own virtual IP:**
```
File: FocusVpnService.kt:111-112
Code: builder.addDnsServer(dnsConfig.virtualIpv4)   // "10.0.0.2"
      builder.addDnsServer(dnsConfig.virtualIpv6)   // "fd00:1:fd00:1:fd00:1:fd00:1"
```
After `builder.establish()`, Android OS sets the system DNS resolver to `10.0.0.2`.
All DNS queries on the device now go to `10.0.0.2:53`.

**Step 2 — DNS queries routed into TUN:**
```
File: FocusVpnService.kt:108-109
Code: builder.addRoute(dnsConfig.virtualIpv4, 32)    // 10.0.0.2/32
      builder.addRoute(dnsConfig.virtualIpv6, 128)
```
Traffic destined for `10.0.0.2` enters the TUN interface.

**Step 3 — VpnPacketLoop reads DNS query, forwards non-blocked domains via DoH:**
```
File: VpnPacketLoop.kt:85-113
Code: val domain = DnsPacketParser.extractDomainName(packet, length)
      if (isDomainBlocked(domain)) {
          // NXDOMAIN response — works fine
      } else {
          forwardDnsViaDoH(...)  // ← THIS IS WHERE IT BREAKS
      }
```

**Step 4 — forwardDnsViaDoH creates HTTPS request to NextDNS:**
```
File: VpnPacketLoop.kt:150-156
Code: val request = Request.Builder()
          .url(dnsConfig.dohEndpoint)  // "https://dns.nextdns.io/71421a"
          .post(requestBody)
          .build()
      val response = okHttpClient.newCall(request).execute()
```

**Step 5 — OkHttpClient needs to resolve `dns.nextdns.io` hostname:**
```
File: NetworkModule.kt:46-57
Code: val dnsCache = object : Dns {
          override fun lookup(hostname: String): List<InetAddress> {
              val cached = domainCache[hostname]
              if (cached != null && now < cached.second) return cached.first
              val result = Dns.SYSTEM.lookup(hostname)  // ← FALLS BACK TO SYSTEM DNS
              domainCache[hostname] = Pair(result, now + 60_000L)
              return result
          }
      }
```
`Dns.SYSTEM.lookup("dns.nextdns.io")` calls `InetAddress.getAllByName("dns.nextdns.io")`.
The system DNS is now `10.0.0.2`. This sends a UDP DNS query to `10.0.0.2:53`.

**Step 6 — INFINITE RECURSION:**
```
Query for "dns.nextdns.io" → 10.0.0.2:53 → TUN → VpnPacketLoop
→ dns.nextdns.io not blocked → forwardDnsViaDoH()
→ OkHttpClient resolves "dns.nextdns.io" → Dns.SYSTEM → 10.0.0.2:53
→ TUN → VpnPacketLoop → forwardDnsViaDoH() → resolve → 10.0.0.2 → ...
∞ LOOP → timeout → DNS_PROBE_POSSIBLE
```

**Step 7 — No socket protection exists:**
```
Verification: grep "protect" across entire VPN service directory → ZERO results
File: VpnPacketLoop.kt — no call to vpnService.protect() anywhere
The OkHttpClient is a shared Hilt singleton (NetworkModule.kt:42) — no VPN-specific client exists
```

### Answer to Question 1 (investigated factually):
**YES — the OkHttpClient IS the same shared singleton.** 
- Provided at `NetworkModule.kt:42` as `@Singleton`
- Injected into `FocusVpnService` via `BlockingServiceEntryPoint.kt:23` → `entryPoint.okHttpClient()`
- Same client used by `MetaDescriptionFetcher`, `DoHQueryUseCase`, Retrofit API calls, AND VPN DoH forwarding
- Its custom DNS resolver (`NetworkModule.kt:46-57`) delegates to `Dns.SYSTEM.lookup()` which is poisoned by the VPN's own `addDnsServer()`

---

## Root Cause #2: DAO Query Includes TEMPORARY Auto-Blocked Entries

### Evidence Chain

**BlockedDomainEntity has 3 statuses:**
```
File: BlockedDomainEntity.kt:44
Code: val blockStatus: String = "PERMANENT"  // PERMANENT, TEMPORARY, CLEAN_CACHE
```

**DAO queries only exclude CLEAN_CACHE — not TEMPORARY:**
```
File: BlockedDomainDao.kt:28-42
Query: WHERE bc.is_active = 1 AND (bd.block_status IS NULL OR bd.block_status != 'CLEAN_CACHE')
```
This means TEMPORARY entries (from SmartScan auto-blocks) are included in `activeDomains`.

**Both VPN and Accessibility use these queries:**
- `FocusVpnService.kt:76` → `getAllBlockedDomainsFlowUseCase().collectLatest`
- `FocusAccessibilityService.kt:104` → `getAllBlockedDomainsFlowUseCase()`

**Impact:** Domains auto-blocked by SmartScan (even those that failed due to timeout/empty responses — now returning FetchFailed instead of blocking) would remain in the active blocklist until they expire.

---

## Phase 2: Pattern Analysis

### Working vs Broken

| Aspect | Working Pattern (other VPN DNS apps) | FocusLocker (broken) |
|---|---|---|
| DoH client DNS | Hardcoded IP or bootstrap resolver | Uses `Dns.SYSTEM` (poisoned by VPN) |
| Socket protection | `VpnService.protect(socket)` | No protect() calls exist |
| Dedicated DoH client | Separate OkHttpClient with custom DNS | Shared singleton |
| DAO active list | Excludes temporary/transient entries | Only excludes CLEAN_CACHE |

---

## Phase 3: Proven Causes (NOT hypotheses — traced through code line-by-line)

### Cause 1: DNS Bootstrap Recursion
- **What:** VPN sets system DNS to 10.0.0.2 → DoH forwarder uses system DNS to resolve DoH hostname → infinite loop
- **Why:** No bootstrap DNS resolver exists for the DoH endpoint. No `protect()` on OkHttp sockets.
- **Proof:** Code trace through 7 files confirms the loop. Zero `protect` calls found via grep.

### Cause 2: TEMPORARY Entries in Active Blocklist
- **What:** DAO queries include TEMPORARY-status domains in the sync blocklist
- **Why:** WHERE clause only excludes `CLEAN_CACHE`, not `TEMPORARY`
- **Proof:** SQL query text at BlockedDomainDao.kt:32 and :40 — verified character-by-character

---

## Required Blocking Order (User Specification)

```
1. NextDNS (DNS-level)     → VPN TUN intercepts DNS → DoH to NextDNS for filtering
2. Local Blocklist          → blocked_domains.json + hosts.txt → NXDOMAIN in VpnPacketLoop  
3. Keyword Extraction       → SmartScan meta-fetch + Aho-Corasick keyword matching
```

### Current State vs Required Order

| Layer | Required | Current Status |
|---|---|---|
| NextDNS DoH | Forward non-blocked DNS to NextDNS | **BROKEN** — DNS bootstrap recursion prevents any DoH request |
| Local Blocklist | NXDOMAIN for domains in activeDomains | **PARTIALLY WORKING** — NXDOMAIN logic works but activeDomains has TEMPORARY pollution |
| Keyword Extraction | SmartScan + keyword match on URL | **WORKING** — SmartScan now has search engine safelist + fail-open |

---

## Fix Plan (4 changes, 3 files)

### Fix 1: Create VPN-Dedicated OkHttpClient with Bootstrap DNS (NetworkModule.kt)
**Problem:** Shared OkHttpClient uses `Dns.SYSTEM` which is poisoned by VPN
**Fix:** Add a second `@Named("vpnDoH")` OkHttpClient with a hardcoded bootstrap DNS resolver that resolves `dns.nextdns.io` using Google DNS (8.8.8.8) or Cloudflare (1.1.1.1) directly over a protected socket, bypassing the VPN TUN entirely.

### Fix 2: Protect OkHttp Sockets via VpnService.protect() (VpnPacketLoop.kt)
**Problem:** OkHttp sockets are not protected — they could be captured by the VPN
**Fix:** Use a `ProtectedSocketFactory` that calls `vpnService.protect(socket)` on every socket created by the DoH OkHttpClient. This ensures the HTTPS connection to NextDNS goes over the physical network, not through the TUN.

### Fix 3: Exclude TEMPORARY from DAO Active List (BlockedDomainDao.kt)
**Problem:** TEMPORARY entries pollute the VPN/Accessibility sync blocklist
**Fix:** Change the WHERE clause from:
```sql
bd.block_status IS NULL OR bd.block_status != 'CLEAN_CACHE'
```
To:
```sql
bd.block_status IS NULL OR bd.block_status = 'PERMANENT'
```

### Fix 4: Wire Dedicated Client into VPN Service (FocusVpnService.kt + BlockingServiceEntryPoint.kt)
**Problem:** VPN uses the shared OkHttpClient
**Fix:** Inject the `@Named("vpnDoH")` client into `FocusVpnService` and pass it to `VpnPacketLoop`

---

## Files to Modify

| File | Change |
|---|---|
| `di/NetworkModule.kt` | Add `@Named("vpnDoH")` OkHttpClient with bootstrap DNS + protected socket factory |
| `service/vpn/VpnPacketLoop.kt` | Accept VpnService reference for socket protection |
| `data/local/dao/BlockedDomainDao.kt` | Fix WHERE clause to exclude TEMPORARY |
| `service/vpn/FocusVpnService.kt` | Inject named DoH client |
| `di/BlockingServiceEntryPoint.kt` | Expose named DoH client |

---

## Verification Criteria

- [ ] `dns.nextdns.io` resolves via bootstrap DNS (8.8.8.8), not via VPN TUN
- [ ] OkHttp DoH sockets are protected via `VpnService.protect()`
- [ ] google.com resolves successfully when VPN is active
- [ ] NextDNS logs show queries appearing (currently 0)
- [ ] TEMPORARY domains do NOT appear in activeDomains HashSet
- [ ] Blocked domains from hosts.txt/blocked_domains.json still return NXDOMAIN
- [ ] Keyword extraction still works via SmartScan on non-blocked URLs
