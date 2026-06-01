package com.focuslock.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focuslock.di.BlockingServiceEntryPoint
import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.usecase.CheckUrlForBlockedContentUseCase
import com.focuslock.domain.usecase.GetActiveBlockedDomainsUseCase
import com.focuslock.domain.usecase.GetActiveKeywordsUseCase
import com.focuslock.service.overlay.BlockingOverlayService
import androidx.core.content.ContextCompat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
            "com.sec.android.app.sbrowser", // Samsung Internet
            "com.opera.browser",
            "com.duckduckgo.mobile.android",
            "com.microsoft.emmx" // Edge
        )
        
        // List of settings packages to protect
        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings"
        )

        private const val URL_DEBOUNCE_MILLIS = 500L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isSessionActive = false
    @Volatile private var activeKeywords: List<String> = emptyList()
    @Volatile private var activeDomains: List<String> = emptyList()
    @Volatile private var activeSessionExpiresAt: Long? = null
    private var lastProcessedUrl: String? = null
    private var lastProcessedAtMillis = 0L

    private lateinit var checkUrlForBlockedContentUseCase: CheckUrlForBlockedContentUseCase
    private lateinit var getActiveKeywordsUseCase: GetActiveKeywordsUseCase
    private lateinit var getActiveBlockedDomainsUseCase: GetActiveBlockedDomainsUseCase
    private lateinit var sessionRepository: SessionRepository

    /**
     * Called when the accessibility service is connected and ready.
     * We can perform initialization or configure service info here.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service successfully connected.")

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BlockingServiceEntryPoint::class.java
        )

        checkUrlForBlockedContentUseCase = entryPoint.checkUrlForBlockedContentUseCase()
        getActiveKeywordsUseCase = entryPoint.getActiveKeywordsUseCase()
        getActiveBlockedDomainsUseCase = entryPoint.getActiveBlockedDomainsUseCase()
        sessionRepository = entryPoint.sessionRepository()

        // Observe active session
        serviceScope.launch {
            sessionRepository.getActiveSession().collect { session ->
                isSessionActive = session != null
                activeSessionExpiresAt = session?.expiresAt
                if (isSessionActive) {
                    // Fetch blocked domains when session becomes active
                    activeDomains = getActiveBlockedDomainsUseCase()
                } else {
                    activeDomains = emptyList()
                }
            }
        }

        // Observe active keywords
        serviceScope.launch {
            getActiveKeywordsUseCase().collect { keywords ->
                activeKeywords = keywords
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
        
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // 1. Settings Protection
        if (SETTINGS_PACKAGES.contains(packageName)) {
            handleSettingsEvent(event)
            return
        }

        // 2. Browser Filtering
        if (SUPPORTED_BROWSERS.contains(packageName)) {
            handleBrowserEvent(event, packageName)
            return
        }
    }

    private fun handleSettingsEvent(event: AccessibilityEvent) {
        val source = event.source ?: return
        
        val sensitiveKeywords = listOf("FocusLock", "Device admin", "Device administrator", "VPN", "Accessibility")
        
        for (keyword in sensitiveKeywords) {
            val nodes = source.findAccessibilityNodeInfosByText(keyword)
            if (nodes.isNotEmpty()) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                launchOverlayService("Protected settings", "SETTINGS_PROTECTED")
                
                return
            }
        }
    }

    private fun handleBrowserEvent(event: AccessibilityEvent, packageName: String) {
        // Find the URL node. Note: ID varies by browser.
        val source = event.source ?: return
        
        val urlNode = findUrlNode(source, packageName)
        val url = urlNode?.text?.toString()
        
        if (!url.isNullOrBlank()) {
            if (isDuplicateUrlEvent(url)) return

            val result = checkUrlForBlockedContentUseCase(url, activeKeywords, activeDomains)
            if (result is BlockCheckResult.Blocked) {
                // Perform BACK action on the browser
                performGlobalAction(GLOBAL_ACTION_BACK)
                launchOverlayService(result.matchedTerm, result.reason.name)
            } else {
                stopService(Intent(this, BlockingOverlayService::class.java))
            }
        }
    }

    private fun findUrlNode(root: AccessibilityNodeInfo, packageName: String): AccessibilityNodeInfo? {
        val urlBarId = when (packageName) {
            "com.android.chrome" -> "com.android.chrome:id/url_bar"
            "org.mozilla.firefox" -> "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
            "com.sec.android.app.sbrowser" -> "com.sec.android.app.sbrowser:id/location_bar_edit_text"
            "com.opera.browser" -> "com.opera.browser:id/url_field"
            "com.duckduckgo.mobile.android" -> "com.duckduckgo.mobile.android:id/omnibarTextInput"
            "com.microsoft.emmx" -> "com.microsoft.emmx:id/url_bar"
            else -> return null
        }
        
        val nodes = root.findAccessibilityNodeInfosByViewId(urlBarId)
        return nodes.firstOrNull()
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
        Log.d(TAG, "Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
