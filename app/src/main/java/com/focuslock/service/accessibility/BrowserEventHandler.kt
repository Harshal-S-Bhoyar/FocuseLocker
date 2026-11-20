package com.focuslock.service.accessibility

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.usecase.CheckUrlForBlockedContentUseCase
import com.focuslock.service.overlay.BlockingOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class BrowserEventHandler(
    private val service: FocusAccessibilityService,
    private val checkUrlForBlockedContentUseCase: CheckUrlForBlockedContentUseCase,
    private val smartScanOrchestrator: SmartScanOrchestrator,
    private val getActiveKeywords: () -> List<String>,
    private val getActiveDomains: () -> List<String>,
    private val getActiveCategories: () -> List<String> = { emptyList() },
    private val getDomainRevision: () -> Long = { 0L }
) {
    private var lastProcessedUrl: String? = null
    private var lastProcessedUrlAtMillis = 0L
    private val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var checkJob: Job? = null
    // Published by the accessibility callback and consumed by the check coroutine's verdict step,
    // which is dispatched back to Main. Volatile so the value is safely published even if a caller
    // (a test, or a future non-accessibility source) publishes from another thread; the Main
    // dispatch is what makes the "is this still the latest URL?" read and the effects it guards a
    // single serialized step. checkJob stays main-thread-confined (assigned here and in dispose()).
    @Volatile
    private var latestUrl: String? = null
    
    companion object {
        private const val TAG = "SVC_ACCESSIBILITY"
        private const val URL_DEBOUNCE_MILLIS = 500L
    }

    fun handleBrowserEvent(event: AccessibilityEvent, packageName: String) {
        Timber.tag(TAG).v("handleBrowserEvent: Processing browser event for package %s", packageName)
        val source = event.source
        val root = service.rootInActiveWindow ?: source ?: return
        
        val urlNode = findUrlNode(root, packageName)
        val url = urlNode?.text?.toString()
        val isValidUrl = url != null && url.contains(".") && !url.contains(" ")
        if (isValidUrl) {
            if (isDuplicateUrlEvent(url)) return

            // Matching runs off Main; verdict handling posts back through service calls.
            // Single-flight: cancel the previous check and drop verdicts for
            // superseded URLs so only the latest page takes effect.
            latestUrl = url
            checkJob?.cancel()
            checkJob = handlerScope.launch {
                val result = checkUrlForBlockedContentUseCase(url, getActiveKeywords(), getActiveDomains(), getDomainRevision())
                // Apply the verdict on Main, the thread that publishes latestUrl. Reading the guard
                // and acting on it on the checking thread would be a read-then-act across threads:
                // a newer URL could land in between and a superseded verdict would still press BACK
                // on the page the user just opened. Confined to Main with no suspension between the
                // read and the effects, publication and application are serialized instead.
                withContext(Dispatchers.Main.immediate) {
                    if (url != latestUrl) return@withContext
                    if (result is BlockCheckResult.Blocked) {
                        service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                        service.launchOverlayService(result.matchedTerm, result.reason.name)
                    } else {
                        service.stopService(Intent(service, BlockingOverlayService::class.java))
                        smartScanOrchestrator.startScan(url, getActiveKeywords(), getActiveCategories())
                    }
                }
            }
        }
    }

    fun dispose() {
        checkJob?.cancel()
        handlerScope.cancel()
    }

    private fun isDuplicateUrlEvent(url: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isDuplicate = url == lastProcessedUrl && now - lastProcessedUrlAtMillis < URL_DEBOUNCE_MILLIS
        if (!isDuplicate) {
            lastProcessedUrl = url
            lastProcessedUrlAtMillis = now
        }
        return isDuplicate
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
        return firstNode
    }
}
