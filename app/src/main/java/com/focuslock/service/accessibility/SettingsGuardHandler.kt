package com.focuslock.service.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

class SettingsGuardHandler(
    private val service: FocusAccessibilityService
) {
    companion object {
        private const val TAG = "SVC_ACCESSIBILITY"
    }

    fun handleSettingsEvent(event: AccessibilityEvent) {
        Timber.tag(TAG).d("handleSettingsEvent: Processing settings event.")
        val source = event.source ?: return
        
        val sensitiveKeywords = listOf(
            "Device admin", "Device administrator", "VPN", "Accessibility",
            "Display over other apps", "Appear on top", "Draw over other apps",
            "Overlay", "permit drawing over", "Show on top", "Display pop-up windows"
        )
        
        val genericActionKeywords = listOf(
            "App info", "Force stop", "Uninstall", "Clear data", "Clear cache"
        )
        
        val allText = mutableListOf<String>()
        collectAllNodeText(source, allText)
        
        val isFocusLock = allText.any { it.contains("FocusLock", ignoreCase = true) } ||
                source.packageName?.toString()?.contains("focuslock", ignoreCase = true) == true
                
        val foundSensitive = allText.any { text ->
            sensitiveKeywords.any { keyword -> 
                text.contains(keyword, ignoreCase = true)
            }
        }
        
        val foundGeneric = allText.any { text ->
            genericActionKeywords.any { keyword -> 
                text.contains(keyword, ignoreCase = true)
            }
        }
        
        val found = foundSensitive || (isFocusLock && foundGeneric)
        
        if (found) {
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            
            // Add a small delay to ensure the BACK action is fully processed before launching the overlay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                service.launchOverlayService("Protected settings", "SETTINGS_PROTECTED")
            }, 300L) // 300ms delay gives enough time for the BACK action to clear the settings screen
        }
    }

    private fun collectAllNodeText(node: AccessibilityNodeInfo, textList: MutableList<String>) {
        node.text?.toString()?.let { textList.add(it) }
        node.contentDescription?.toString()?.let { textList.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllNodeText(child, textList)
            }
        }
    }
}
