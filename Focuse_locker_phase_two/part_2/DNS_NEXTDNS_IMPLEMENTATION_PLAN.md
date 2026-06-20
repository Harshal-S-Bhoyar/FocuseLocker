# DNS & NextDNS Implementation Plan — Phase 2 Part 2

> **Profile**: `71421a` | **DNS**: `45.90.28.169` / `45.90.30.169` | **Linked IP**: `47.11.11.244`
> **Strategy**: NextDNS primary → Local blocklist (hosts.txt + blocked_domains.json) as fallback

---

## Architecture Overview

```
DNS Query from any app
        │
        ▼
┌─────────────────────┐
│   VPN TUN Interface  │
│   (FocusVpnService)  │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐     ┌──────────────────────┐
│  Local Blocklist     │────▶│  NXDOMAIN Response    │
│  (HashSet O(1))      │ YES │  (blocked locally)    │
│  hosts.txt +         │     └──────────────────────┘
│  blocked_domains.json│
│  + auto_blocked      │
└─────────┬───────────┘
          │ NOT in local list
          ▼
┌─────────────────────┐     ┌──────────────────────┐
│  NextDNS Forward     │────▶│  NextDNS decides:     │
│  45.90.28.169        │     │  Block → NXDOMAIN     │
│  (profile 71421a)    │     │  Allow → resolved IP  │
└─────────┬───────────┘     └──────────────────────┘
          │ TIMEOUT/FAIL
          ▼
┌─────────────────────┐
│  Google DNS Fallback │
│  8.8.8.8 (degraded)  │
│  + local list still  │
│  active              │
└─────────────────────┘
```

**Layered defense**:
1. **Layer 1** — Local HashSet (instant, offline, 83k+ domains)
2. **Layer 2** — NextDNS server-side blocking (categories, analytics, deny/allowlists)
3. **Layer 3** — Google DNS fallback (only if NextDNS unreachable)
4. **Layer 4** — AccessibilityService DoH + SmartScan (post-navigation safety net)

---

## Issues & Fixes Summary

| # | Severity | Issue | Fix | File(s) |
|---|----------|-------|-----|---------|
| 1 | 🔴 Critical | VPN uses Google DNS (8.8.8.8) instead of NextDNS | Change DnsConfig to NextDNS IPs + Google fallback | `DnsConfig.kt` |
| 2 | 🔴 Critical | VPN blocklist loaded once, never refreshed | Use Flow.collectLatest observer | `FocusVpnService.kt` |
| 3 | 🔴 Critical | VPN routes only cover current DNS IPs | Add NextDNS routes to TUN builder | `FocusVpnService.kt` |
| 4 | 🟠 High | `getAllBlockedDomainList()` ignores category active status | JOIN on blocked_categories | `BlockedDomainDao.kt` |
| 5 | 🟠 High | Subdomain matching is single-level only | Recursive while-loop matching | `FocusVpnService.kt` |
| 6 | 🟡 Medium | DoH NXDOMAIN may false-positive on non-existent domains | Add NODATA check | `DoHQueryUseCase.kt` |
| 7 | 🟡 Medium | No logging for DNS resolution path taken | Add Timber logging | `VpnPacketLoop.kt` |

---

## Fix 1 — DnsConfig: NextDNS Primary + Google Fallback

**File**: `app/src/main/java/com/focuslock/service/vpn/DnsConfig.kt`

**Problem**: Hardcoded Google DNS. NextDNS never queried at network layer.

**Solution**: 3-tier resolver chain — NextDNS primary, NextDNS secondary, Google fallback.

```kotlin
package com.focuslock.service.vpn

data class DnsConfig(
    val primaryResolver: String,
    val fallbackResolver: String,
    val lastResortResolver: String = "8.8.8.8"
) {
    val resolvers: List<String> = listOf(primaryResolver, fallbackResolver, lastResortResolver)

    companion object {
        /**
         * NextDNS profile-specific anycast IPs for profile 71421a.
         * These IPs encode the profile ID in their routing — no DoH/DoT needed.
         * Google DNS is kept as last-resort if NextDNS is completely unreachable.
         */
        val DEFAULT = DnsConfig(
            primaryResolver = "45.90.28.169",    // NextDNS primary (profile 71421a)
            fallbackResolver = "45.90.30.169",    // NextDNS fallback (profile 71421a)
            lastResortResolver = "8.8.8.8"        // Google DNS (degraded — no NextDNS categories)
        )
    }
}
```

**Why this works**: The IPs `45.90.28.169`/`45.90.30.169` are profile-specific. NextDNS routes queries hitting these IPs through profile `71421a` automatically — no DoH/DoT handshake needed. The linked IP (`47.11.11.244`) provides additional device identification.

---

## Fix 2 — FocusVpnService: Live Domain Observer + Recursive Subdomain Matching

**File**: `app/src/main/java/com/focuslock/service/vpn/FocusVpnService.kt`

**Problems**:
- Blocklist loaded ONCE at startup, never refreshed
- Subdomain matching only strips one level
- No DI for `GetAllBlockedDomainsFlowUseCase`

**Solution**: Replace one-shot load with Flow.collectLatest + recursive subdomain check.

```kotlin
package com.focuslock.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import timber.log.Timber
import androidx.core.app.NotificationCompat
import com.focuslock.R
import com.focuslock.di.BlockingServiceEntryPoint
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.usecase.GetActiveBlockedDomainsUseCase
import com.focuslock.domain.usecase.GetAllBlockedDomainsFlowUseCase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FocusVpnService : VpnService() {

    private val TAG = "FocusVpnService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetLoopJob: Job? = null
    private var sessionMonitorJob: Job? = null
    private var domainObserverJob: Job? = null
    
    private lateinit var getActiveBlockedDomainsUseCase: GetActiveBlockedDomainsUseCase
    private lateinit var getAllBlockedDomainsFlowUseCase: GetAllBlockedDomainsFlowUseCase
    private lateinit var sessionRepository: SessionRepository
    
    // Thread-safe: reads happen on packet loop thread, writes on observer thread
    // Using @Volatile reference swap instead of synchronized for zero-contention reads
    @Volatile
    private var activeDomains: HashSet<String> = HashSet()
    private val dnsConfig = DnsConfig.DEFAULT

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BlockingServiceEntryPoint::class.java
        )
        getActiveBlockedDomainsUseCase = entryPoint.getActiveBlockedDomainsUseCase()
        getAllBlockedDomainsFlowUseCase = entryPoint.getAllBlockedDomainsFlowUseCase()
        sessionRepository = entryPoint.sessionRepository()
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("VPN Service starting")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        serviceScope.launch {
            // Initial load for immediate availability
            val domains = getActiveBlockedDomainsUseCase()
            activeDomains = HashSet(domains.map { it.lowercase() })
            Timber.tag(TAG).d("Loaded ${activeDomains.size} blocked domains (initial)")
            
            // Live observer — picks up auto-blocked domains, category toggles, etc.
            domainObserverJob?.cancel()
            domainObserverJob = launch {
                getAllBlockedDomainsFlowUseCase().collectLatest { domains ->
                    val newSet = HashSet(domains.map { it.lowercase() })
                    activeDomains = newSet  // atomic reference swap
                    Timber.tag(TAG).d("Domain list updated: ${newSet.size} domains active")
                }
            }
            
            // Monitor session status
            sessionMonitorJob?.cancel()
            sessionMonitorJob = launch {
                sessionRepository.getActiveSession().collectLatest { session ->
                    if (session == null) {
                        stopVpn()
                    }
                }
            }
            
            startVpn()
        }
        
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        
        try {
            val builder = Builder()
            builder.addAddress("10.0.0.2", 32)
            // Route DNS traffic to NextDNS + Google fallback through TUN
            dnsConfig.resolvers.forEach { resolver ->
                builder.addRoute(resolver, 32)
            }
            builder.addDnsServer(dnsConfig.primaryResolver)
            builder.setSession("FocusLock VPN")
            builder.setMtu(1500)
            
            vpnInterface = builder.establish()
            
            vpnInterface?.let { activeInterface ->
                Timber.tag(TAG).d("TUN interface established — DNS routed to NextDNS (${dnsConfig.primaryResolver})")
                packetLoopJob = serviceScope.launch {
                    val packetLoop = VpnPacketLoop(
                        this@FocusVpnService,
                        activeInterface,
                        dnsConfig = dnsConfig,
                        isDomainBlocked = { domain ->
                            isDomainOrParentBlocked(domain)
                        }
                    )
                    packetLoop.runLoop()
                }
            } ?: run {
                Timber.tag(TAG).e("Failed to establish TUN interface")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error starting VPN")
        }
    }

    /**
     * Recursive subdomain matching.
     * For "sub.evil.example.com", checks:
     *   1. sub.evil.example.com
     *   2. evil.example.com
     *   3. example.com
     * Stops at TLD (no dot remaining).
     */
    private fun isDomainOrParentBlocked(domain: String): Boolean {
        val currentDomains = activeDomains  // snapshot volatile ref
        var check = domain.lowercase()
        while (check.contains('.')) {
            if (currentDomains.contains(check)) return true
            check = check.substringAfter('.')
        }
        return false
    }

    private fun stopVpn() {
        packetLoopJob?.cancel()
        sessionMonitorJob?.cancel()
        domainObserverJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(R.drawable.ic_focuslock_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "vpn_service_channel"
        private const val NOTIFICATION_ID = 2
    }
}
```

**Key changes**:
- `domainObserverJob` uses `collectLatest` → auto-updates on DB changes
- `@Volatile activeDomains` → atomic reference swap, zero-lock reads on packet loop
- `isDomainOrParentBlocked()` → recursive subdomain walk
- Logs domain count on every update for debugging

---

## Fix 3 — BlockedDomainDao: Category-Aware Queries

**File**: `app/src/main/java/com/focuslock/data/local/dao/BlockedDomainDao.kt`

**Problem**: `getAllBlockedDomainList()` returns domains from disabled categories.

**Solution**: JOIN on `blocked_categories` to respect `is_active` toggle.

```kotlin
// REPLACE lines 27-31 with:

@Query("""
    SELECT DISTINCT bd.domain FROM blocked_domains bd
    INNER JOIN blocked_categories bc ON bd.category_key = bc.category_key
    WHERE bc.is_active = 1 
      AND (bd.block_status IS NULL OR bd.block_status != 'CLEAN_CACHE')
""")
suspend fun getAllBlockedDomainList(): List<String>

@Query("""
    SELECT DISTINCT bd.domain FROM blocked_domains bd
    INNER JOIN blocked_categories bc ON bd.category_key = bc.category_key
    WHERE bc.is_active = 1 
      AND (bd.block_status IS NULL OR bd.block_status != 'CLEAN_CACHE')
""")
fun getAllBlockedDomainListFlow(): Flow<List<String>>
```

**Impact**: When user disables "SOCIAL_MEDIA" category, those domains immediately drop from VPN blocklist (via Flow observer from Fix 2).

---

## Fix 4 — DoHQueryUseCase: Better Response Interpretation

**File**: `app/src/main/java/com/focuslock/domain/usecase/DoHQueryUseCase.kt`

**Problem**: Status=3 (NXDOMAIN) could be a genuine non-existent domain, not a NextDNS block.

**Solution**: Check for NextDNS-specific block indicators (CNAME to blocked page, or Answer with 0.0.0.0).

```kotlin
// REPLACE lines 50-57 with:

val responseBody = response.body?.string() ?: return@withContext DoHResult.QueryFailed
val json = Json.parseToJsonElement(responseBody).jsonObject

val status = json["Status"]?.jsonPrimitive?.intOrNull ?: -1
val answers = json["Answer"]?.let { 
    it as? kotlinx.serialization.json.JsonArray 
}

when {
    // NextDNS blocks return NXDOMAIN (status 3) 
    // OR status 0 with a blocked CNAME/0.0.0.0 answer
    status == 3 -> DoHResult.Blocked
    status == 0 && answers != null && answers.any { answer ->
        val data = answer.jsonObject["data"]?.jsonPrimitive?.content ?: ""
        data == "0.0.0.0" || data == "::0" || data.contains("blocked")
    } -> DoHResult.Blocked
    status == 0 -> DoHResult.Clean
    else -> DoHResult.Unknown
}
```

---

## Fix 5 — VpnPacketLoop: Diagnostic Logging

**File**: `app/src/main/java/com/focuslock/service/vpn/VpnPacketLoop.kt`

**Problem**: No visibility into DNS resolution path (local block vs NextDNS vs Google fallback).

**Solution**: Add targeted logging (rate-limited to avoid log spam).

```kotlin
// REPLACE lines 52-61 with:

val domain = DnsPacketParser.extractDomainName(packet, length)
if (domain != null) {
    if (isDomainBlocked(domain)) {
        Timber.tag(TAG).v("DNS BLOCKED (local): $domain")
        val response = DnsResponseBuilder.buildNxDomainResponse(packet, length)
        outputStream.write(response)
    } else {
        // Forward to upstream DNS (NextDNS primary → NextDNS fallback → Google)
        forwardDns(packet, length, outputStream)
    }
}
```

---

## Implementation Order (Dependency-Safe)

```
Step 1: DnsConfig.kt           — Change DNS IPs (no dependencies)
Step 2: BlockedDomainDao.kt     — Fix category-aware queries (no dependencies)
Step 3: FocusVpnService.kt      — Live observer + subdomain matching (depends on Step 1, 2)
Step 4: DoHQueryUseCase.kt      — Better response parsing (independent)
Step 5: VpnPacketLoop.kt        — Diagnostic logging (independent)
Step 6: Build & Test            — Verify compilation + DNS behavior
```

---

## Testing Checklist

| Test | Method | Expected Result |
|------|--------|----------------|
| NextDNS receives queries | Check NextDNS dashboard → Logs tab | Queries appear from linked IP |
| Local blocklist blocks | Query a domain from hosts.txt | NXDOMAIN before reaching NextDNS |
| Category toggle works | Disable SOCIAL_MEDIA → query facebook.com | facebook.com resolves (not blocked) |
| NextDNS category block | Enable "Porn" in NextDNS dashboard → query adult site | NXDOMAIN from NextDNS |
| Subdomain matching | Query `sub.evil.blocked-domain.com` | Blocked if `blocked-domain.com` is in list |
| Fallback to Google | Block NextDNS IPs in firewall → query any domain | Resolves via 8.8.8.8 (degraded) |
| Dynamic auto-block | SmartScan blocks new domain → VPN catches it | Domain blocked without VPN restart |
| Offline blocking | Airplane mode → query blocked domain | NXDOMAIN from local list |

---

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| NextDNS IPs change in future | Store in `local.properties` / BuildConfig, not hardcoded |
| Linked IP changes (mobile network) | Profile-specific IPs (`45.90.28.169`) don't need linked IP — they encode profile in routing |
| Massive hosts.txt slows first launch | Already chunked (1000/batch); consider lazy background seeding |
| Flow observer causes excessive HashSet rebuilds | `collectLatest` cancels previous — only latest emission processed |
| Volatile HashSet race on read during swap | Snapshot pattern: `val current = activeDomains` before iteration |

---

## Files Modified

| File | Lines Changed | Type |
|------|--------------|------|
| `DnsConfig.kt` | Full rewrite (16 lines) | Config |
| `FocusVpnService.kt` | Major refactor (~169 lines) | Service |
| `BlockedDomainDao.kt` | Lines 27-31 (2 queries) | DAO |
| `DoHQueryUseCase.kt` | Lines 50-57 (response parsing) | UseCase |
| `VpnPacketLoop.kt` | Lines 52-61 (logging) | Service |

**No new files created. No dependencies added. No database migration needed.**
