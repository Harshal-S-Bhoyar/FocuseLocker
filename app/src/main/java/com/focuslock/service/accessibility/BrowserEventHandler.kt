package com.focuslock.service.accessibility

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.usecase.CheckUrlForBlockedContentUseCase
import com.focuslock.service.overlay.BlockingOverlayService
import timber.log.Timber

class BrowserEventHandler(
    private val service: FocusAccessibilityService,
    private val checkUrlForBlockedContentUseCase: CheckUrlForBlockedContentUseCase,
    private val smartScanOrchestrator: SmartScanOrchestrator,
    private val getActiveKeywords: () -> List<String>,
    private val getActiveDomains: () -> List<String>
) {
    private var lastProcessedUrl: String? = null
    
    companion object {
        private const val TAG = "SVC_ACCESSIBILITY"
    }

    fun handleBrowserEvent(event: AccessibilityEvent, packageName: String) {
        Timber.tag(TAG).v("handleBrowserEvent: Processing browser event for package %s", packageName)
        val source = event.source
        val root = service.rootInActiveWindow ?: source ?: return
        
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

            val result = checkUrlForBlockedContentUseCase(url, getActiveKeywords(), getActiveDomains())
            if (result is BlockCheckResult.Blocked) {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                service.launchOverlayService(result.matchedTerm, result.reason.name)
            } else {
                service.stopService(Intent(service, BlockingOverlayService::class.java))
                smartScanOrchestrator.startScan(url, getActiveKeywords())
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

    private fun isDuplicateUrlEvent(url: String): Boolean {
        Timber.tag(TAG).v("isDuplicateUrlEvent: Checking if URL is duplicate.")
        val isDuplicate = url == lastProcessedUrl
        if (!isDuplicate) {
            lastProcessedUrl = url
        }
        return isDuplicate
    }
}
