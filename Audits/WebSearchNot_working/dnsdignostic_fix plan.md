# Plan: Fix DNS Bootstrap Recursion + Blocking Pipeline

## Reference
- Diagnostic: `.paul/DNS_DIAGNOSTIC.md`
- Skills applied: @systematic-debugging, @backend-architect, @kotlin-coroutines-expert

## Acceptance Criteria

- AC-1: When VPN is active, `google.com` resolves successfully (no DNS_PROBE_POSSIBLE)
- AC-2: NextDNS dashboard shows DNS queries appearing (currently 0)
- AC-3: `dns.nextdns.io` is resolved via bootstrap DNS (8.8.8.8/1.1.1.1), NOT via VPN TUN
- AC-4: OkHttp DoH sockets bypass TUN via `VpnService.protect()`
- AC-5: TEMPORARY domains excluded from VPN/Accessibility activeDomains
- AC-6: Blocked domains from hosts.txt/blocked_domains.json still return NXDOMAIN
- AC-7: Keyword extraction via SmartScan still works on allowed URLs
- AC-8: DoHQueryUseCase (Accessibility layer) also uses bootstrap DNS when VPN is active

---

## Architecture Overview

### Current (Broken)
```
Browser → DNS to 10.0.0.2 → TUN → VpnPacketLoop
    → isDomainBlocked? → YES → NXDOMAIN (works)
    → isDomainBlocked? → NO  → forwardDnsViaDoH()
        → OkHttpClient → Dns.SYSTEM → 10.0.0.2 → TUN → ∞ LOOP
```

### Target (Fixed)
```
Browser → DNS to 10.0.0.2 → TUN → VpnPacketLoop
    → isDomainBlocked? → YES → NXDOMAIN (works)
    → isDomainBlocked? → NO  → forwardDnsViaDoH()
        → VPN-dedicated OkHttpClient
            → BootstrapDns resolves dns.nextdns.io via 8.8.8.8 (physical network)
            → ProtectedSocketFactory calls vpnService.protect(socket)
            → HTTPS to dns.nextdns.io → NextDNS response → write to TUN
```

---

## Task Breakdown

### Task 1: Create BootstrapDns Class (NEW FILE)
**File:** `service/vpn/BootstrapDns.kt`
**What:** Custom `okhttp3.Dns` implementation that resolves hostnames via hardcoded DNS servers (8.8.8.8, 1.1.1.1) using raw UDP, completely bypassing the system DNS resolver.
**Why:** System DNS is poisoned by VPN's `addDnsServer(10.0.0.2)`. We need DNS resolution that never touches the VPN TUN.
**Verify:** Unit test — `BootstrapDns.lookup("dns.nextdns.io")` returns valid IPs without using system DNS

**Implementation Details:**
```kotlin
// BootstrapDns resolves hostnames by sending raw DNS queries
// to hardcoded public DNS servers over protected UDP sockets.
// This breaks the bootstrap recursion because it never calls
// InetAddress.getAllByName() (which uses system DNS = 10.0.0.2).

class BootstrapDns(
    private val vpnService: VpnService,
    private val fallbackServers: List<String> = listOf("8.8.8.8", "1.1.1.1")
) : Dns {
    // Cache resolved IPs for 5 minutes to avoid repeated lookups
    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()
    
    override fun lookup(hostname: String): List<InetAddress> {
        // 1. Check cache first
        val cached = cache[hostname]
        if (cached != null && System.currentTimeMillis() < cached.second) {
            return cached.first
        }
        
        // 2. Send raw DNS query to 8.8.8.8 via a PROTECTED UDP socket
        //    vpnService.protect(socket) ensures the packet goes over
        //    the physical network, not through TUN
        val socket = DatagramSocket()
        vpnService.protect(socket)  // ← CRITICAL: bypasses TUN
        
        // 3. Build DNS query packet, send to fallbackServers[0]:53
        // 4. Parse DNS response, extract A records
        // 5. Cache result, return InetAddress list
    }
}
```

**Coroutines consideration (@kotlin-coroutines-expert):**
- `Dns.lookup()` is called synchronously by OkHttp — cannot be `suspend`
- The raw UDP query is blocking I/O but runs on `Dispatchers.IO` (the caller context in VpnPacketLoop)
- `DatagramSocket` timeout: 3 seconds per server, try both fallbacks

**Backend-architect consideration:**
- Resilience: try 8.8.8.8 first, fallback to 1.1.1.1 on timeout
- Cache TTL: 5 minutes — prevents redundant queries but refreshes regularly
- Connection pool isolation: this DNS is ONLY for VPN DoH, never shared with app HTTP

---

### Task 2: Create ProtectedSocketFactory (NEW FILE)
**File:** `service/vpn/ProtectedSocketFactory.kt`
**What:** Custom `javax.net.SocketFactory` that wraps each created socket with `vpnService.protect()` before returning it.
**Why:** Even though routes are narrow (only 10.0.0.2/32), `protect()` guarantees the TCP connection to NextDNS bypasses the TUN at the kernel level.
**Verify:** Log `vpnService.protect()` calls — each DoH HTTPS connection should trigger one

**Implementation Details:**
```kotlin
class ProtectedSocketFactory(
    private val vpnService: VpnService
) : SocketFactory() {
    private val delegate = SocketFactory.getDefault()
    
    override fun createSocket(): Socket {
        val socket = delegate.createSocket()
        vpnService.protect(socket)
        return socket
    }
    
    // Override all 4 createSocket variants — each must call protect()
    // after delegation
}
```

**Backend-architect consideration:**
- This is the industry-standard Android VPN pattern (PersonalDNSFilter, Intra, Blokada all use it)
- Must override ALL 4 `createSocket()` overloads or OkHttp may use an unprotected variant
- Thread-safe: `VpnService.protect()` is thread-safe per Android docs

---

### Task 3: Create VPN-Dedicated OkHttpClient Provider (MODIFY FILE)
**File:** `di/NetworkModule.kt`
**What:** Add a new `@Named("vpnDoH")` OkHttpClient provider that uses BootstrapDns + ProtectedSocketFactory. This client is ONLY used for VPN DoH forwarding.
**Why:** Shared OkHttpClient cannot be modified (MetaDescriptionFetcher, Retrofit, DoHQueryUseCase all depend on it). Need a separate, VPN-aware client.
**Verify:** DI graph compiles. `@Named("vpnDoH")` client has different DNS and socketFactory than default client.

**Implementation Details:**
```kotlin
// CANNOT provide BootstrapDns or ProtectedSocketFactory here because
// they need a VpnService reference, which doesn't exist at DI time.
// Solution: provide the base OkHttpClient, and let FocusVpnService
// create the final DoH client using .newBuilder() with VPN-specific config.
```

**Design Decision (IMPORTANT):**
After investigation, `BootstrapDns` and `ProtectedSocketFactory` both require a `VpnService` reference. `VpnService` is NOT available at Hilt DI time (it's a lifecycle-managed Android component). 

**Revised approach:** Do NOT create a separate Hilt-provided client. Instead:
- FocusVpnService creates the DoH client at runtime using `okHttpClient.newBuilder()`
- This is the correct pattern because the VPN service has the `VpnService` reference needed for `protect()`
- The base client provides connection pool, cache, and logging — `newBuilder()` shares those

```kotlin
// In FocusVpnService.startVpn():
val dohClient = okHttpClient.newBuilder()
    .dns(BootstrapDns(this))                      // "this" = VpnService
    .socketFactory(ProtectedSocketFactory(this))   // "this" = VpnService
    .connectTimeout(5, TimeUnit.SECONDS)           // DoH can be slower
    .readTimeout(5, TimeUnit.SECONDS)
    .cache(null)                                   // No cache for raw DNS
    .build()
```

This means:
- ❌ No changes to `NetworkModule.kt` (no `@Named` needed)
- ❌ No changes to `BlockingServiceEntryPoint.kt`
- ✅ VPN-specific client created in `FocusVpnService` with runtime `VpnService` reference

---

### Task 4: Wire DoH Client into VpnPacketLoop (MODIFY FILE)
**File:** `service/vpn/VpnPacketLoop.kt`
**What:** The `okHttpClient` parameter already exists. No interface change needed — FocusVpnService will pass the DoH-specific client instead of the shared one.
**Verify:** Log the DNS resolver class name at startup — should show `BootstrapDns`, not anonymous `Dns` object

**Current code (FocusVpnService.kt:121-125):**
```kotlin
val packetLoop = VpnPacketLoop(
    vpnService = this@FocusVpnService,
    vpnInterface = activeInterface,
    dnsConfig = dnsConfig,
    okHttpClient = okHttpClient,  // ← shared singleton (BROKEN)
    isDomainBlocked = { domain -> isDomainOrParentBlocked(domain) }
)
```

**Fixed code:**
```kotlin
val packetLoop = VpnPacketLoop(
    vpnService = this@FocusVpnService,
    vpnInterface = activeInterface,
    dnsConfig = dnsConfig,
    okHttpClient = dohClient,  // ← VPN-specific with BootstrapDns + protect()
    isDomainBlocked = { domain -> isDomainOrParentBlocked(domain) }
)
```

---

### Task 5: Fix DAO Queries — Exclude TEMPORARY (MODIFY FILE)
**File:** `data/local/dao/BlockedDomainDao.kt`
**What:** Change the WHERE clause in `getAllBlockedDomainList()` (line 32) and `getAllBlockedDomainListFlow()` (line 40) to exclude TEMPORARY entries.
**Why:** TEMPORARY entries were created by SmartScan's old fail-closed behavior. Even after the fail-open fix, existing TEMPORARY entries remain in DB and pollute activeDomains.
**Verify:** Query the DB — TEMPORARY entries should NOT appear in `getActiveBlockedDomainList` or `getAllBlockedDomainListFlow` results

**Current (line 32 and 40):**
```sql
WHERE bc.is_active = 1 AND (bd.block_status IS NULL OR bd.block_status != 'CLEAN_CACHE')
```

**Fixed:**
```sql
WHERE bc.is_active = 1 AND (bd.block_status IS NULL OR bd.block_status = 'PERMANENT')
```

**Impact trace (@backend-architect):**
- `getAllBlockedDomainList()` → called by `BlockingRepositoryImpl.getAllBlockedDomains()` → called by `GetActiveBlockedDomainsUseCase` → called by `FocusVpnService.onStartCommand()` for initial load
- `getAllBlockedDomainListFlow()` → called by `BlockingRepositoryImpl.getAllBlockedDomainsFlow()` → called by `GetAllBlockedDomainsFlowUseCase` → called by both `FocusVpnService` (live sync) and `FocusAccessibilityService` (live sync)
- Both paths correctly updated by changing the DAO — no repository or use case changes needed

---

## File Change Summary

| # | File | Action | Lines Changed |
|---|---|---|---|
| 1 | `service/vpn/BootstrapDns.kt` | **CREATE** | ~90 lines |
| 2 | `service/vpn/ProtectedSocketFactory.kt` | **CREATE** | ~45 lines |
| 3 | `service/vpn/FocusVpnService.kt` | **MODIFY** | ~10 lines (create dohClient, pass to PacketLoop) |
| 4 | `data/local/dao/BlockedDomainDao.kt` | **MODIFY** | 2 lines (WHERE clause fix) |

**Files NOT modified (originally planned but unnecessary):**
- ❌ `di/NetworkModule.kt` — not needed, VPN client created at runtime
- ❌ `di/BlockingServiceEntryPoint.kt` — not needed, no new DI binding
- ❌ `service/vpn/VpnPacketLoop.kt` — interface unchanged, receives dohClient via constructor

---

## Execution Order

```
Step 1: Create BootstrapDns.kt (no dependencies)
Step 2: Create ProtectedSocketFactory.kt (no dependencies)  
Step 3: Modify FocusVpnService.kt (depends on steps 1+2)
Step 4: Modify BlockedDomainDao.kt (independent — can run parallel)
Step 5: Build + verify compilation
Step 6: Test on device — verify DNS resolution with VPN active
```

---

## Data Flow After Fix

### Layer 1: VPN DNS Interception
```
Browser asks for google.com
  → System DNS = 10.0.0.2 → UDP to TUN
  → VpnPacketLoop extracts "google.com"
  → isDomainOrParentBlocked("google.com") → false (not in activeDomains)
  → forwardDnsViaDoH(dnsPayload)
  → dohClient (with BootstrapDns) resolves dns.nextdns.io via 8.8.8.8
  → ProtectedSocketFactory.protect(socket) bypasses TUN
  → HTTPS POST to dns.nextdns.io/71421a
  → NextDNS returns A record for google.com
  → buildTunDnsResponse() → write to TUN → browser gets IP
```

### Layer 2: Local Blocklist (VPN)
```
Browser asks for blocked-site.com
  → System DNS = 10.0.0.2 → UDP to TUN
  → VpnPacketLoop extracts "blocked-site.com"
  → isDomainOrParentBlocked("blocked-site.com") → true (in activeDomains from DAO)
  → DnsResponseBuilder.buildNxDomainResponse() → write to TUN
  → Browser gets NXDOMAIN → "site can't be reached"
```

### Layer 3: Keyword Extraction (Accessibility)
```
Browser navigates to allowed-site.com/bad-keyword-page
  → URL bar text captured by FocusAccessibilityService
  → checkUrlForBlockedContentUseCase(url, keywords, domains)
    → keyword match on URL path? → block
    → domain in activeDomains? → block
  → If allowed → async SmartScan + DoH:
    → dohQueryUseCase.checkAndCacheDoH(url) — checks NextDNS via JSON API
    → smartScanUseCase(url, keywords) — fetches meta tags, scans for keywords
    → First blocked result triggers overlay
```

---

## Risk Assessment

| Risk | Mitigation |
|---|---|
| BootstrapDns raw UDP parsing is complex | Use well-tested DNS wire format (RFC 1035). Fall back to cached IPs on parse error. |
| Protected socket not covering all OkHttp calls | Override ALL 4 `createSocket()` variants. Log each protect() call for verification. |
| 8.8.8.8/1.1.1.1 blocked on some networks | Try both. If both fail, log error and let query timeout (fail-open for DNS). |
| TEMPORARY cleanup leaves stale entries | DAO fix excludes them from queries. Existing entries still have `expires_at` and get cleaned by `deleteExpiredEntries()`. |
| DoHQueryUseCase in Accessibility also hits NextDNS | This uses Hilt OkHttpClient (NOT VPN client). When VPN is active, system DNS goes through TUN → bootstrapped by VPN. When VPN is off, Dns.SYSTEM works normally. No change needed. |

---

## Coroutines Considerations (@kotlin-coroutines-expert)

1. **BootstrapDns.lookup() is blocking** — called by OkHttp from its dispatcher thread. This is correct behavior per OkHttp's design. The `DatagramSocket` read has a 3-second timeout.

2. **VpnPacketLoop.forwardDnsViaDoH()** already runs on `Dispatchers.IO` via `loopScope.launch` — the blocking OkHttp call inside is appropriate.

3. **FocusVpnService.serviceScope** uses `SupervisorJob() + Dispatchers.IO` — if one DNS forward fails, others continue. This is correct.

4. **No GlobalScope usage** — all coroutines are scoped to `serviceScope` (VPN) or `serviceScope` (Accessibility).

5. **CancellationException** is properly rethrown in VpnPacketLoop:34-127 — structured concurrency preserved.
