package com.focuslock.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import timber.log.Timber
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focuslock.di.BlockingServiceEntryPoint
import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.usecase.CheckUrlForBlockedContentUseCase
import com.focuslock.domain.usecase.GetAllBlockedDomainsFlowUseCase
import com.focuslock.domain.usecase.GetActiveKeywordsUseCase
import com.focuslock.service.overlay.BlockingOverlayService
import androidx.core.content.ContextCompat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import java.util.concurrent.atomic.AtomicReference

/**
 * FocusAccessibilityService reads browser URL bars to detect and block keyword matches
 * across all Android browsers. This is a core technical mechanism of the FocusLock app
 * to ensure that distracting or harmful content remains blocked during an active session.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusAccessibility"
        
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
            "com.android.settings"
        )

        private const val URL_DEBOUNCE_MILLIS = 500L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isSessionActive = false
    private val activeKeywords = AtomicReference<List<String>>(emptyList())
    private val activeDomains = AtomicReference<List<String>>(emptyList())
    @Volatile private var activeSessionExpiresAt: Long? = null
    private var lastProcessedUrl: String? = null
    private var lastProcessedAtMillis = 0L
    private var pendingSmartScanJob: Job? = null

    private lateinit var checkUrlForBlockedContentUseCase: CheckUrlForBlockedContentUseCase
    private lateinit var getActiveKeywordsUseCase: GetActiveKeywordsUseCase
    private lateinit var getAllBlockedDomainsFlowUseCase: GetAllBlockedDomainsFlowUseCase
    private lateinit var sessionRepository: SessionRepository
    private lateinit var smartScanUseCase: com.focuslock.domain.usecase.SmartScanUseCase

    /**
     * Called when the accessibility service is connected and ready.
     * We can perform initialization or configure service info here.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.tag(TAG).d("Accessibility Service successfully connected.")

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BlockingServiceEntryPoint::class.java
        )

        checkUrlForBlockedContentUseCase = entryPoint.checkUrlForBlockedContentUseCase()
        getActiveKeywordsUseCase = entryPoint.getActiveKeywordsUseCase()
        getAllBlockedDomainsFlowUseCase = entryPoint.getAllBlockedDomainsFlowUseCase()
        sessionRepository = entryPoint.sessionRepository()
        smartScanUseCase = entryPoint.smartScanUseCase()

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
            }
        }
    }

    /**
     * Callback for AccessibilityEvents. This is where the service intercepts screen changes,
     * specifically targetting browser address bars to read the active URLs and verify
     * if they contain blocked keywords.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isSessionActive) return

        val packageName = event.packageName?.toString() ?: return
        val eventType = event.eventType

        // 1. Settings Protection — only on window state change (app/screen switch)
        if (SETTINGS_PACKAGES.contains(packageName)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                handleSettingsEvent(event)
            }
            return
        }

        // 2. Browser Filtering — accept BOTH event types:
        //    STATE_CHANGED  → tab switch, app open
        //    CONTENT_CHANGED → URL bar update after navigation
        //    Battery safety: isDuplicateUrlEvent() debounce + findUrlNode() specific ID filter
        if (SUPPORTED_BROWSERS.contains(packageName)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                handleBrowserEvent(event, packageName)
            }
            return
        }
    }

    private fun handleSettingsEvent(event: AccessibilityEvent) {
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
        
        if (!url.isNullOrBlank()) {
            if (isDuplicateUrlEvent(url)) return

            val result = checkUrlForBlockedContentUseCase(url, activeKeywords.get(), activeDomains.get())
            if (result is BlockCheckResult.Blocked) {
                // Perform BACK action on the browser
                performGlobalAction(GLOBAL_ACTION_BACK)
                launchOverlayService(result.matchedTerm, result.reason.name)
            } else {
                stopService(Intent(this, BlockingOverlayService::class.java))
                
                // Smart Scan for dynamic blocking (runs asynchronously)
                pendingSmartScanJob?.cancel()
                pendingSmartScanJob = serviceScope.launch {
                    val isAutoBlocked = smartScanUseCase(url, activeKeywords.get())
                    if (isAutoBlocked) {
                        withContext(Dispatchers.Main) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            launchOverlayService("Smart Scan Match", "AUTO_BLOCKED")
                        }
                    }
                }
            }
        }
    }

    private fun findUrlNode(root: AccessibilityNodeInfo, packageName: String): AccessibilityNodeInfo? {
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
        val now = SystemClock.elapsedRealtime()
        val isDuplicate = url == lastProcessedUrl && now - lastProcessedAtMillis < URL_DEBOUNCE_MILLIS
        lastProcessedUrl = url
        lastProcessedAtMillis = now
        return isDuplicate
    }

    /**
     * Called when the system wants to interrupt the accessibility feedback.
     */
    override fun onInterrupt() {
        Timber.tag(TAG).d("Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
