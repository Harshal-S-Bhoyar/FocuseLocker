# FocuseLocker — C4 Architecture Diagrams

> Source of truth: `graphify-out/GRAPH_REPORT.md` (2346 nodes · 2122 edges · 404 communities)
> *Last updated: 2026-06-25*

---

## Diagram 1 — Layer Architecture (Mermaid)

```mermaid
graph TD
    classDef uiLayer fill:#E2E8F0,stroke:#64748B,stroke-width:2px,color:#0F172A;
    classDef domainLayer fill:#DBEAFE,stroke:#3B82F6,stroke-width:2px,color:#0F172A;
    classDef dataLayer fill:#DCFCE7,stroke:#22C55E,stroke-width:2px,color:#0F172A;
    classDef serviceLayer fill:#FEF9C3,stroke:#EAB308,stroke-width:2px,color:#0F172A;
    classDef hubNode fill:#FEE2E2,stroke:#EF4444,stroke-width:3px,color:#991B1B,font-weight:bold;
    classDef bridgeNode fill:#F3E8FF,stroke:#A855F7,stroke-width:3px,stroke-dasharray: 5 5,color:#581C87;
    classDef newNode fill:#FFF7ED,stroke:#F97316,stroke-width:3px,color:#7C2D12;

    subgraph Presentation ["📱 Presentation Layer (Compose)"]
        Nav[AppNavigation]:::hubNode
        OnboardVM[OnboardingViewModel]:::hubNode
        ActiveSession[ActiveSessionScreen]:::hubNode
        UnlockVM[UnlockViewModel]:::uiLayer
        PermEnforce[PermissionEnforcementService]:::newNode

        Nav --> ActiveSession
        Nav --> UnlockVM
        OnboardVM --> Nav
        PermEnforce --> Nav
    end

    subgraph ServiceOS ["⚙️ Service & OS Integration Layer"]
        AccessSvc[FocusAccessibilityService]:::bridgeNode
        VpnSvc[FocusVpnService]:::hubNode
        PacketLoop[VpnPacketLoop\n(object-pooled buffers)]:::bridgeNode
        Overlay[BlockingOverlayService]:::serviceLayer
        RuntimeCtrl[AndroidSessionRuntimeController]:::newNode
        BootRx[BootReceiver + OemAutostartHelper]:::serviceLayer

        VpnSvc --> PacketLoop
        AccessSvc --> Overlay
        RuntimeCtrl --> VpnSvc
        BootRx --> RuntimeCtrl
    end

    subgraph Workers ["🔧 WorkManager Workers"]
        SessionExpiry[SessionExpiryWorker]:::serviceLayer
        Cooldown[CooldownTimerWorker]:::serviceLayer
        Emergency[EmergencyBypassWorker]:::serviceLayer
        ListSync[ListSyncWorker\n(fixed: silent cancel)]:::serviceLayer

        RuntimeCtrl --> SessionExpiry
    end

    subgraph Domain ["🧠 Domain Layer (Pure Kotlin)"]
        SmartScan[SmartScanUseCase]:::bridgeNode
        ActivateSess[ActivateSessionUseCase]:::domainLayer
        UnlockFlow[RequestUnlockUseCase\nVerifyUnlockOtpUseCase]:::domainLayer
        Aho[AhoCorasick Engine]:::domainLayer
        DoH[DoHQueryUseCase\n(with retry)]:::domainLayer
        InitApp[InitializeAppUseCase\n(DOMAINS_SEEDED gate)]:::domainLayer

        OnboardVM --> InitApp
        UnlockVM --> UnlockFlow
        ActivateSess --> RuntimeCtrl
        PacketLoop --> DoH
        AccessSvc --> SmartScan
        SmartScan --> Aho
        SmartScan --> DoH
    end

    subgraph Data ["💾 Data Layer (Room SQLite)"]
        BlockRepo[BlockingRepository\n19 edges — hub]:::hubNode
        DomainDao[BlockedDomainDao\n58k+ rows]:::dataLayer
        SessionDao[SessionDao]:::dataLayer
        AppSettingsRepo[AppSettingsRepository\nDOMAINS_SEEDED flag]:::dataLayer
        MetaFetch[MetaDescriptionFetcher\nSSRF-protected]:::bridgeNode

        SmartScan --> BlockRepo
        SmartScan --> MetaFetch
        BlockRepo --> DomainDao
        ActivateSess --> SessionDao
        InitApp --> AppSettingsRepo
    end

    subgraph Backend ["☁️ OTP Backend (Node.js + Brevo)"]
        OtpServer[Express OTP Server\nBrevo Email API]:::serviceLayer

        UnlockFlow --> OtpServer
    end
```

---

## Diagram 2 — C4 Container (Mermaid C4Container)

```mermaid
C4Container
    title FocusLocker — Container View

    Person(user, "Focus User", "Self-disciplines via timer-locked sessions")
    Person(contact, "Trusted Contact", "Receives OTP email to authorize early unlock")

    System_Boundary(app, "FocusLocker Android App") {
        Container(ui, "UI Layer", "Jetpack Compose + ViewModels + Hilt", "12+ screens, state via StateFlow")
        Container(domain, "Domain Layer", "Pure Kotlin UseCases (19+)", "All business rules, zero Android imports")
        ContainerDb(room, "FocusDatabase", "Room SQLite", "7 tables, 58k+ domain rows, all state local")
        Container(access, "AccessibilityService", "FocusAccessibilityService", "10-browser URL monitoring, keyword scan + DoH race")
        Container(vpn, "VPN Service", "FocusVpnService + VpnPacketLoop", "Local TUN, DNS intercept, object-pooled buffers, DoH retry")
        Container(overlay, "Overlay Service", "BlockingOverlayService", "Full-screen non-dismissible overlay")
        Container(enforce, "Permission Enforcer", "PermissionEnforcementService", "Detects lost permissions, routes to repair. NEW Phase 7.")
        Container(runtime, "Session Runtime", "AndroidSessionRuntimeController", "Owns VPN + notification lifecycle. Concrete class (no interface).")
        Container(workers, "Background Workers", "WorkManager: SessionExpiry, Cooldown, Emergency, ListSync", "Survive process death + reboot")
        Container(boot, "Boot Recovery", "BootReceiver + OemAutostartHelper", "Restores session on reboot, OEM-specific autostart")
    }

    System_Ext(backend, "OTP Backend", "Node.js 20 + Express 5 + TypeScript + Brevo\nRailway.app hosted. No persistent storage.")

    Rel(user, ui, "Sets sessions, monitors timer", "Touch")
    Rel(ui, domain, "UseCases via coroutines", "Kotlin")
    Rel(domain, room, "DAOs + Flow", "Room")
    Rel(access, domain, "SmartScanUseCase", "Coroutine")
    Rel(vpn, domain, "CheckUrl + DoH", "Coroutine")
    Rel(access, overlay, "Intent on match", "Android")
    Rel(enforce, ui, "Routes to repair screen", "Intent/Nav")
    Rel(runtime, vpn, "startRuntime/stopRuntime", "Service API")
    Rel(runtime, workers, "Schedule expiry", "WorkManager")
    Rel(boot, runtime, "startRuntime() on BOOT_COMPLETED", "BroadcastReceiver")
    Rel(domain, backend, "POST /verify-contact\nPOST /request-unlock\nPOST /verify-unlock-otp", "HTTPS/Retrofit")
    Rel(backend, contact, "OTP context email via Brevo", "Email")
```

---

## Diagram 3 — Boot Recovery Sequence

```mermaid
sequenceDiagram
    participant OS as Android OS
    participant Boot as BootReceiver
    participant OEM as OemAutostartHelper
    participant Repo as SessionRepository
    participant Runtime as AndroidSessionRuntimeController
    participant Sched as SessionWorkerScheduler
    participant Perm as PermissionEnforcementService

    OS->>Boot: ACTION_BOOT_COMPLETED
    Boot->>OEM: sendOemAutostartIntents()
    Boot->>Repo: getActiveSession()
    alt Session found + not expired
        Repo-->>Boot: Session(id, expiresAt)
        Boot->>Runtime: startRuntime()
        Runtime->>Sched: rescheduleExpiryWorker(expiresAt)
    else Session found + expired
        Repo-->>Boot: Session(id, expiresAt=past)
        Boot->>Runtime: endSession(COMPLETED)
    else No session
        Boot-->>Boot: no-op
    end
    Boot->>Perm: checkPermissionHealth()
    Perm-->>Boot: [ACCESSIBILITY_LOST] → prompts user on next open
```

---

## Diagram 4 — Keyword Blocking Sequence

```mermaid
sequenceDiagram
    participant Browser as Browser App
    participant A11y as FocusAccessibilityService
    participant Aho as AhoCorasick
    participant Smart as SmartScanUseCase
    participant DoH as DoHQueryUseCase
    participant Overlay as BlockingOverlayService

    Browser->>A11y: TYPE_WINDOW_CONTENT_CHANGED (URL changed)
    A11y->>Aho: scan(url, activeKeywords)
    alt Instant keyword match
        Aho-->>A11y: MATCH
        A11y->>Browser: performGlobalAction(BACK)
        A11y->>Overlay: startService(reason, timeRemaining)
    else No instant match
        A11y->>Smart: invoke(url) [async select{}]
        par DoH check
            Smart->>DoH: queryDomain(domain)
        and Metadata scan
            Smart->>Smart: fetchAndScanMeta(url)
        end
        alt Blocked by either
            Smart-->>A11y: BLOCKED
            A11y->>Browser: performGlobalAction(BACK)
            A11y->>Overlay: startService()
        else Clear
            Smart-->>A11y: CLEAR (fail-open on network error)
        end
    end
```
