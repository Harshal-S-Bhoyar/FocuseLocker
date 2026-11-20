package com.focuslock.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.focuslock.service.accessibility.FocusAccessibilityService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PermissionCheckerFallbackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `secure setting string grants accessibility when manager list lags`() {
        val flat = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.other/.Service:$flat"
        )

        assertTrue(PermissionChecker.isAccessibilityServiceEnabled(context))
    }

    @Test
    fun `unrelated services keep accessibility denied`() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.other/.Service:" + context.packageName + "/.service.accessibility.OtherService"
        )

        assertFalse(PermissionChecker.isAccessibilityServiceEnabled(context))
    }
}
