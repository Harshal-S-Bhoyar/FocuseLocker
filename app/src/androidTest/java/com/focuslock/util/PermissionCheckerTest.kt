package com.focuslock.util

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

// ponytail: Basic smoke tests to ensure no crash on Context access. 
// Ceiling: doesn't test actual true/false logic without mocking SystemServices. 
// Upgrade path: add Mockk and mock DevicePolicyManager/AccessibilityManager if precision is needed.

@RunWith(AndroidJUnit4::class)
class PermissionCheckerTest {

    @Test
    fun testPermissionCheckers_doNotCrash() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        PermissionChecker.isAccessibilityServiceEnabled(ctx)
        PermissionChecker.isDeviceAdminEnabled(ctx)
        PermissionChecker.isOverlayPermissionGranted(ctx)
        PermissionChecker.isBatteryOptimizationIgnored(ctx)
    }
}
