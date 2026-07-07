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
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.async
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
        
        @Volatile var settingsGuardMode = false
        @Volatile var guardedSettingsPackage: String? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isSessionActive = false
    private val activeKeywords = AtomicReference<List<String>>(emptyList())
    private val activeDomains = AtomicReference<List<String>>(emptyList())
    @Volatile private var activeSessionExpiresAt: Long? = null
    private var lastProcessedUrl: String? = null
    private var lastProcessedAtMillis = 0L
    private var lastSettingsProcessedAtMillis = 0L
    private var pendingSmartScanJob: Job? = null
    
    // Add watchdog broadcast receiver to enable guard mode? Not needed, we will use companion object variables

    private lateinit var checkUrlForBlockedContentUseCase: CheckUrlForBlockedContentUseCase
    private lateinit var getActiveKeywordsUseCase: GetActiveKeywordsUseCase
    private lateinit var getAllBlockedDomainsFlowUseCase: GetAllBlockedDomainsFlowUseCase
    private lateinit var sessionRepository: SessionRepository
    private lateinit var smartScanUseCase: com.focuslock.domain.usecase.SmartScanUseCase
    private lateinit var dohQueryUseCase: com.focuslock.domain.usecase.DoHQueryUseCase
    private lateinit var keywordScanner: com.focuslock.domain.usecase.KeywordScanner

    /**
     * Called when the accessibility service is connected and ready.
     * We can perform initialization or configure service info here.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.tag(TAG).d("Accessibility Service successfully connected.")
        updateForegroundState(true)

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BlockingServiceEntryPoint::class.java
        )

        checkUrlForBlockedContentUseCase = entryPoint.checkUrlForBlockedContentUseCase()
        getActiveKeywordsUseCase = entryPoint.getActiveKeywordsUseCase()
        getAllBlockedDomainsFlowUseCase = entryPoint.getAllBlockedDomainsFlowUseCase()
        sessionRepository = entryPoint.sessionRepository()
        smartScanUseCase = entryPoint.smartScanUseCase()
        dohQueryUseCase = entryPoint.dohQueryUseCase()
        keywordScanner = entryPoint.keywordScanner()

        // Observe active session
        serviceScope.launch {
            combine(
                sessionRepository.getActiveSession(),
                getAllBlockedDomainsFlowUseCase()
            ) { session, domains ->
                Pair(session, domains)
            }.collect { (session, domains) ->
                isSessionActive = session != null
                activeSessionExpiresAt = session?.expiresAt
                if (isSessionActive) {
                    activeDomains.set(domains)
                } else {
                    activeDomains.set(emptyList())
                }
            }
        }

        // Observe active keywords
        serviceScope.launch {
            getActiveKeywordsUseCase().collect { keywords ->
                activeKeywords.set(keywords)
                keywordScanner.rebuild(keywords)
            }
        }
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
            
            val notification = androidx.core.app.NotificationCompat.Builder(this, "accessibility_channel")
                .setSmallIcon(R.drawable.ic_focuslock_secure)
                .setContentTitle(getString(R.string.accessibility_notification_title))
                .setContentText(getString(R.string.accessibility_notification_text))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
                
            startForeground(1003, notification)
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
                handleSettingsEvent(event)
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
                handleBrowserEvent(event, packageName)
            }
            return
        }
    }

    private fun handleSettingsEvent(event: AccessibilityEvent) {
        Timber.tag(TAG).d("handleSettingsEvent: Processing settings event.")
        val source = event.source ?: return
        
        val sensitiveKeywords = listOf("FocusLock", "Device admin", "Device administrator", "VPN", "Accessibility")
        
        val allText = mutableListOf<String>()
        collectAllNodeText(source, allText)
        source.recycle()
        
        val found = allText.any { text ->
            sensitiveKeywords.any { keyword -> 
                text.contains(keyword, ignoreCase = true)
            }
        }
        
        if (found) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            launchOverlayService("Protected settings", "SETTINGS_PROTECTED")
        }
    }

    private fun collectAllNodeText(node: AccessibilityNodeInfo, textList: MutableList<String>) {
        // Omitting entry log due to high recursion frequency
        node.text?.toString()?.let { textList.add(it) }
        node.contentDescription?.toString()?.let { textList.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllNodeText(child, textList)
                child.recycle()
            }
        }
    }

    private fun handleBrowserEvent(event: AccessibilityEvent, packageName: String) {
        Timber.tag(TAG).d("handleBrowserEvent: Processing browser event for package %s", packageName)
        // Find the URL node. Note: ID varies by browser.
        val source = event.source
        val root = rootInActiveWindow ?: source ?: return
        
        val urlNode = findUrlNode(root, packageName)
        val url = urlNode?.text?.toString()
        urlNode?.recycle()
        if (root != source) {
            root.recycle()
        }
        source?.recycle()
        
        val isValidUrl = url != null && url.contains(".") && !url.contains(" ")
        if (isValidUrl) {
            if (isDuplicateUrlEvent(url)) return

            val result = checkUrlForBlockedContentUseCase(url, activeKeywords.get(), activeDomains.get())
            if (result is BlockCheckResult.Blocked) {
                // Perform BACK action on the browser
                performGlobalAction(GLOBAL_ACTION_BACK)
                launchOverlayService(result.matchedTerm, result.reason.name)
            } else {
                stopService(Intent(this, BlockingOverlayService::class.java))
                
                // Smart Scan and DoH for dynamic blocking (runs asynchronously)
                pendingSmartScanJob?.cancel()
                pendingSmartScanJob = serviceScope.launch {
                    val dohJob = async { dohQueryUseCase.checkAndCacheDoH(url) }
                    val metaJob = async { smartScanUseCase(url, activeKeywords.get()) }

                    val blockedResult = select<Pair<String, String>?> {
                        dohJob.onAwait { dohResult ->
                            if (dohResult is com.focuslock.domain.model.DoHResult.Blocked) {
                                metaJob.cancel()
                                Pair("category_block", "NEXTDNS_DOH")
                            } else {
                                null
                            }
                        }
                        metaJob.onAwait { result ->
                            if (result is com.focuslock.domain.model.SmartScanResult.Blocked) {
                                dohJob.cancel()
                                Pair(result.keyword, "AUTO_BLOCKED: ${result.signal}")
                            } else {
                                null
                            }
                        }
                    }

                    if (blockedResult != null) {
                        withContext(Dispatchers.Main) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            launchOverlayService(blockedResult.first, blockedResult.second)
                        }
                        return@launch
                    }

                    // If the first one finished cleanly, we need to wait for the other one
                    if (dohJob.isActive) {
                        val dohResult = dohJob.await()
                        if (dohResult is com.focuslock.domain.model.DoHResult.Blocked) {
                            withContext(Dispatchers.Main) {
                                performGlobalAction(GLOBAL_ACTION_BACK)
                                launchOverlayService("category_block", "NEXTDNS_DOH")
                            }
                        }
                    } else if (metaJob.isActive) {
                        val result = metaJob.await()
                        if (result is com.focuslock.domain.model.SmartScanResult.Blocked) {
                            withContext(Dispatchers.Main) {
                                performGlobalAction(GLOBAL_ACTION_BACK)
                                launchOverlayService(result.keyword, "AUTO_BLOCKED: ${result.signal}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findUrlNode(root: AccessibilityNodeInfo, packageName: String): AccessibilityNodeInfo? {
        Timber.tag(TAG).v("findUrlNode: Searching for URL node in %s", packageName)
        val urlBarId = when (packageName) {
            "com.android.chrome" -> "com.android.chrome:id/url_bar"
            "org.mozilla.firefox" -> "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
            "com.sec.android.app.sbrowser" -> "com.sec.android.app.sbrowser:id/location_bar_edit_text"
            "com.opera.browser" -> "com.opera.browser:id/url_field"
            "com.opera.mini.native" -> "com.opera.mini.native:id/url_field"
            "com.duckduckgo.mobile.android" -> "com.duckduckgo.mobile.android:id/omnibarTextInput"
            "com.microsoft.emmx" -> "com.microsoft.emmx:id/url_bar"
            "com.brave.browser" -> "com.brave.browser:id/url_bar"
            "com.kiwibrowser.browser" -> "com.kiwibrowser.browser:id/url_bar"
            "com.UCMobile.intl" -> "com.UCMobile.intl:id/title_bar_input"
            else -> return null
        }
        
        val nodes = root.findAccessibilityNodeInfosByViewId(urlBarId)
        val firstNode = nodes.firstOrNull()
        nodes.drop(1).forEach { it.recycle() }
        return firstNode
    }

    private fun launchOverlayService(matchedTerm: String, blockReason: String) {
        Timber.tag(TAG).d("launchOverlayService: Launching overlay for matched term '%s', reason: %s", matchedTerm, blockReason)
        val remainingMillis = (activeSessionExpiresAt ?: 0L) - System.currentTimeMillis()
        val intent = Intent(this, BlockingOverlayService::class.java).apply {
            action = BlockingOverlayService.ACTION_SHOW_OVERLAY
            putExtra(BlockingOverlayService.EXTRA_MATCHED_TERM, matchedTerm)
            putExtra(BlockingOverlayService.EXTRA_BLOCK_REASON, blockReason)
            putExtra(BlockingOverlayService.EXTRA_TIME_REMAINING, remainingMillis.coerceAtLeast(0L))
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun isDuplicateUrlEvent(url: String): Boolean {
        Timber.tag(TAG).v("isDuplicateUrlEvent: Checking if URL is duplicate.")
        // Since we already throttle CONTENT_CHANGED events globally, 
        // this method just ensures we don't start duplicate scans for the exact same URL
        val isDuplicate = url == lastProcessedUrl
        if (!isDuplicate) {
            lastProcessedUrl = url
        }
        return isDuplicate
    }

    /**
     * Called when the system wants to interrupt the accessibility feedback.
     */
    override fun onInterrupt() {
        Timber.tag(TAG).d("Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        Timber.tag(TAG).d("onDestroy: Accessibility Service destroyed.")
        super.onDestroy()
        serviceScope.cancel()
    }
}
