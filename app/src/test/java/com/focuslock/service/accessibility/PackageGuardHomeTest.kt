package com.focuslock.service.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class PackageGuardHomeTest {

    private fun serviceWith(sessionActive: Boolean, blocked: Set<String>): FocusAccessibilityService {
        val service = FocusAccessibilityService()
        val activeField = FocusAccessibilityService::class.java.getDeclaredField("isSessionActive")
        activeField.isAccessible = true
        activeField.set(service, sessionActive)
        val blockedField = FocusAccessibilityService::class.java.getDeclaredField("blockedAppPackages")
        blockedField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (blockedField.get(service) as AtomicReference<Set<String>>).set(blocked)
        return service
    }

    @Test
    fun `guard mode watches the launcher so Home exits guard`() {
        val pkgs = serviceWith(true, emptySet())
            .computeWatchedPackages("com.focuslock", true, "com.example.launcher")
            .toSet()
        assertTrue(pkgs.contains("com.example.launcher"))
    }

    @Test
    fun `inactive guard keeps the filter narrow`() {
        val pkgs = serviceWith(false, emptySet())
            .computeWatchedPackages("com.focuslock", false, "com.example.launcher")
            .toSet()
        assertFalse(pkgs.contains("com.example.launcher"))
        assertTrue(pkgs.contains("com.android.settings"))
    }

    @Test
    fun `null launcher keeps guard explicit without crashing`() {
        val pkgs = serviceWith(true, emptySet())
            .computeWatchedPackages("com.focuslock", true, null)
            .toSet()
        assertTrue(pkgs.contains("com.android.settings"))
    }
}
