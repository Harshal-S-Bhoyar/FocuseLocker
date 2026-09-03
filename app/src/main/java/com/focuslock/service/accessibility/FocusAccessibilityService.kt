package com.focuslock.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import timber.log.Timber
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focuslock.di.BlockingServiceEntryPoint
import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.usecase.CheckUrlForBlockedContentUseCase
import com.focuslock.domain.usecase.GetAllBlockedDomainsFlowUseCase
import com.focuslock.domain.usecase.GetActiveKeywordsUseCase
import com.focuslock.service.overlay.BlockingOverlayService
import androidx.core.content.ContextCompat
import com.focuslock.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * FocusAccessibilityService reads browser URL bars to detect and block keyword matches
 * across all Android browsers. This is a core technical mechanism of the FocusLock app
 * to ensure that distracting or harmful content remains blocked during an active session.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SVC_ACCESSIBILITY"
        
        // List of supported browser package names
        private val SUPPORTED_BROWSERS = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.sec.android.app.sbrowser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.duckduckgo.mobile.android",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.kiwibrowser.browser",
            "com.UCMobile.intl"
        )
        
        // List of settings packages to protect
        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.app.settings",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.vivo.permissionmanager",
            "com.oneplus.security",
            "com.huawei.systemmanager",
            "com.letv.android.letvsafe",
            "com.samsung.android.lool",
            "com.asus.mobilemanager"
        )

        private const val URL_DEBOUNCE_MILLIS = 500L
        
        @Volatile var instance: FocusAccessibilityService? = null
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        @Volatile var settingsGuardMode = false
            set(value) {
                field = value
                instance?.updateServiceInfoForGuardMode(value)
            }
        @Volatile var guardedSettingsPackage: String? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isSessionActive = false
    private val activeKeywords = AtomicReference<List<String>>(emptyList())
    private val activeDomains = AtomicReference<List<String>>(emptyList())
    private val activeCategories = AtomicReference<List<String>>(emptyList())
    private val domainRevision = AtomicLong(0L)
    @Volatile private var activeSessionExpiresAt: Long? = null
    private var lastProcessedUrl: String? = null
    private var lastProcessedAtMillis = 0L
    private var lastSettingsProcessedAtMillis = 0L
    private var pendingSmartScanJob: Job? = null
    
    // Add watchdog broadcast receiver to enable guard mode? Not needed, we will use companion object variables

    private lateinit var checkUrlForBlockedContentUseCase: CheckUrlForBlockedContentUseCase
    private lateinit var getActiveKeywordsUseCase: GetActiveKeywordsUseCase
    private lateinit var blockingRepository: com.focuslock.data.repository.BlockingRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var smartScanUseCase: com.focuslock.domain.usecase.SmartScanUseCase
    private lateinit var dohQueryUseCase: com.focuslock.domain.usecase.DoHQueryUseCase
    private lateinit var keywordScanner: com.focuslock.domain.usecase.KeywordScanner

    private lateinit var smartScanOrchestrator: SmartScanOrchestrator
    private lateinit var browserEventHandler: BrowserEventHandler
    private lateinit var settingsGuardHandler: SettingsGuardHandler

    /**
     * Called when the accessibility service is connected and ready.
     * We can perform initialization or configure service info here.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        updateServiceInfoForGuardMode(settingsGuardMode)
        Timber.tag(TAG).d("Accessibility Service successfully connected.")
        updateForegroundState(true)

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BlockingServiceEntryPoint::class.java
        )

        checkUrlForBlockedContentUseCase = entryPoint.checkUrlForBlockedContentUseCase()
        getActiveKeywordsUseCase = entryPoint.getActiveKeywordsUseCase()
        blockingRepository = entryPoint.blockingRepository()
        sessionRepository = entryPoint.sessionRepository()
        smartScanUseCase = entryPoint.smartScanUseCase()
        dohQueryUseCase = entryPoint.dohQueryUseCase()
        keywordScanner = entryPoint.keywordScanner()

        smartScanOrchestrator = SmartScanOrchestrator(
            dohQueryUseCase,
            smartScanUseCase,
            serviceScope
        ) { matchedTerm, blockReason ->
            performGlobalAction(GLOBAL_ACTION_BACK)
            launchOverlayService(matchedTerm, blockReason)
        }

        browserEventHandler = BrowserEventHandler(
            this,
            checkUrlForBlockedContentUseCase,
            smartScanOrchestrator,
            { activeKeywords.get() },
            { activeDomains.get() },
            { activeCategories.get() },
            { domainRevision.get() }
        )

        settingsGuardHandler = SettingsGuardHandler(this)

        // Observe active session and domain rules for selected categories continuously
        serviceScope.launch {
            sessionRepository.getActiveSession().flatMapLatest { session ->
                isSessionActive = session != null
                activeSessionExpiresAt = session?.expiresAt

                if (session != null) {
                    val keys = session.selectedCategoryKeys.filter { it.isNotBlank() }
                    activeCategories.set(keys)
                    if (keys.isNotEmpty()) {
                        blockingRepository.getDomainsByCategoriesFlow(keys)
                    } else {
                        flowOf(emptyList())
                    }
                } else {
                    activeCategories.set(emptyList())
                    settingsGuardMode = false
                    flowOf(null)
                }
            }.collect { domains ->
                domainRevision.incrementAndGet()
                smartScanUseCase.clearCache()
                checkUrlForBlockedContentUseCase.clearCache()

                if (domains != null) {
                    activeDomains.set(domains)
                } else {
                    activeDomains.set(emptyList())
                }
            }
        }

        // Observe active keywords
        serviceScope.launch {
            getActiveKeywordsUseCase().collect { keywords ->
                checkUrlForBlockedContentUseCase.clearCache()
                activeKeywords.set(keywords)
                keywordScanner.rebuild(keywords)
            }
        }
    }

    private fun updateServiceInfoForGuardMode(isGuardActive: Boolean) {
        val runnable = Runnable {
            val info = serviceInfo ?: return@Runnable
            info.packageNames = if (isGuardActive) null
                else (SUPPORTED_BROWSERS + SETTINGS_PACKAGES).toTypedArray()
            serviceInfo = info
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) runnable.run()
        else mainHandler.post(runnable)
    }

    private fun updateForegroundState(isActive: Boolean) {
        Timber.tag(TAG).d("updateForegroundState: Setting foreground state to $isActive")
        if (isActive) {
            val channel = android.app.NotificationChannel(
                "accessibility_channel",
                getString(R.string.accessibility_notification_channel),
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            
            val noOpIntent = android.app.PendingIntent.getActivity(
                this, 0,
                Intent(this, com.focuslock.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val repairIntent = Intent(this, com.focuslock.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "permissions_repair")
            }
            val repairPendingIntent = android.app.PendingIntent.getActivity(
                this, 1, repairIntent, android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = androidx.core.app.NotificationCompat.Builder(this, "accessibility_channel")
                .setSmallIcon(R.drawable.ic_focuslock_secure)
                .setContentTitle(getString(R.string.accessibility_notification_title))
                .setContentText(getString(R.string.accessibility_notification_text))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(noOpIntent)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_SECRET)
                .addAction(0, "Repair Permissions", repairPendingIntent)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(1003, notification.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1003, notification.build())
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    /**
     * Callback for AccessibilityEvents. This is where the service intercepts screen changes,
     * specifically targetting browser address bars to read the active URLs and verify
     * if they contain blocked keywords.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isSessionActive) return
        
        // Log sparingly here to avoid extreme log spam, but note the event arrival
        // Timber.tag(TAG).v("onAccessibilityEvent: Received event type %s", event.eventType)

        val packageName = event.packageName?.toString() ?: return
        val eventType = event.eventType

        if (settingsGuardMode) {
            val currentPackage = packageName
            // User left Settings entirely (opened another app, went to home)
            if (currentPackage != "com.android.settings" &&
                !SETTINGS_PACKAGES.contains(currentPackage) &&
                currentPackage != this.packageName) {
                // Force back to overlay
                com.focuslock.service.overlay.PermissionEnforcementService.start(this)
                settingsGuardMode = false
            }
            return  // Don't process other events in guard mode
        }

        // 1. Settings Protection — handle both state and content changed with throttling
        if (SETTINGS_PACKAGES.contains(packageName)) {
            val now = SystemClock.elapsedRealtime()
            val isContentChanged = eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            val isStateChanged = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            if (isStateChanged || (isContentChanged && now - lastSettingsProcessedAtMillis > 500L)) {
                if (isContentChanged) lastSettingsProcessedAtMillis = now
                settingsGuardHandler.handleSettingsEvent(event)
            }
            return
        }

        // 2. Browser Filtering — accept BOTH event types:
        //    STATE_CHANGED  → tab switch, app open
        //    CONTENT_CHANGED → URL bar update after navigation
        //    Battery safety: Throttle CONTENT_CHANGED to max 2 times a second (500ms) to prevent excessive DOM traversal
        if (SUPPORTED_BROWSERS.contains(packageName)) {
            val now = SystemClock.elapsedRealtime()
            val isContentChanged = eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            val isStateChanged = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            if (isStateChanged || (isContentChanged && now - lastProcessedAtMillis > 500L)) {
                lastProcessedAtMillis = now
                browserEventHandler.handleBrowserEvent(event, packageName)
            }
            return
        }
    }

    internal fun launchOverlayService(matchedTerm: String, blockReason: String) {
        Timber.tag(TAG).d("launchOverlayService: Launching overlay for matched term '%s', reason: %s", matchedTerm, blockReason)
        val remainingMillis = (activeSessionExpiresAt ?: 0L) - System.currentTimeMillis()
        val intent = Intent(this, BlockingOverlayService::class.java).apply {
            action = BlockingOverlayService.ACTION_SHOW_OVERLAY
            putExtra(BlockingOverlayService.EXTRA_MATCHED_TERM, matchedTerm)
            putExtra(BlockingOverlayService.EXTRA_BLOCK_REASON, blockReason)
            putExtra(BlockingOverlayService.EXTRA_TIME_REMAINING, remainingMillis.coerceAtLeast(0L))
        }
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start overlay foreground service, trying normal start")
            try {
                startService(intent)
            } catch (e2: Exception) {
                Timber.tag(TAG).e(e2, "Failed to start overlay service normally")
                // Fallback: Launch MainActivity with a warning
                val fallbackIntent = Intent(this, com.focuslock.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "active_session")
                }
                try {
                    startActivity(fallbackIntent)
                } catch (e3: Exception) {
                    Timber.tag(TAG).e(e3, "Failed to launch fallback activity")
                }
            }
        }
    }

    /**
     * Called when the system wants to interrupt the accessibility feedback.
     */
    override fun onInterrupt() {
        Timber.tag(TAG).d("Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        Timber.tag(TAG).d("onDestroy: Accessibility Service destroyed.")
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
        serviceScope.cancel()
    }
}
