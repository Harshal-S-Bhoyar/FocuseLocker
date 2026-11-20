package com.focuslock.service.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class PackageFilterTest {

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
    fun `idle filter is explicit and scoped`() {
        val pkgs = serviceWith(false, emptySet()).computeWatchedPackages("com.focuslock", false).toSet()
        assertNotNull(pkgs)
        assertTrue(pkgs.contains("com.android.chrome"))
        assertTrue(pkgs.contains("com.android.settings"))
        assertTrue(pkgs.contains("com.focuslock"))
        assertFalse(pkgs.contains("com.example.game"))
    }

    @Test
    fun `enforcement filter includes blocked apps and never null`() {
        val pkgs = serviceWith(true, setOf("com.example.game"))
            .computeWatchedPackages("com.focuslock", false).toSet()
        assertNotNull(pkgs)
        assertTrue(pkgs.contains("com.example.game"))
        assertTrue(pkgs.contains("com.android.chrome"))
        assertTrue(pkgs.contains("com.android.settings"))
        assertTrue(pkgs.contains("com.focuslock"))
    }

    @Test
    fun `guard mode without blocked apps still explicit`() {
        val pkgs = serviceWith(true, emptySet())
            .computeWatchedPackages("com.focuslock", true).toSet()
        assertNotNull(pkgs)
        assertTrue(pkgs.contains("com.android.chrome"))
        assertTrue(pkgs.contains("com.android.settings"))
    }

    @Test
    fun `inactive session with blocked set stays narrow`() {
        val pkgs = serviceWith(false, setOf("com.example.game"))
            .computeWatchedPackages("com.focuslock", false).toSet()
        assertFalse(pkgs.contains("com.example.game"))
        assertTrue(pkgs.contains("com.android.chrome"))
    }
}
