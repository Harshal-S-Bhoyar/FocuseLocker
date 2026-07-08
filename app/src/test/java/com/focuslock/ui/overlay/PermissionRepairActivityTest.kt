package com.focuslock.ui.overlay

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.focuslock.util.PermissionChecker
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class PermissionRepairActivityTest {

    @Before
    fun setup() {
        mockkObject(PermissionChecker)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `onResume finishes activity if all permissions are granted`() {
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns true
        every { PermissionChecker.isAccessibilityServiceEnabled(any()) } returns true
        every { PermissionChecker.isDeviceAdminEnabled(any()) } returns true
        every { PermissionChecker.isVpnPermissionGranted(any()) } returns true
        every { PermissionChecker.isBatteryOptimizationIgnored(any()) } returns true
        
        val controller = Robolectric.buildActivity(PermissionRepairActivity::class.java).setup()
        val activity = controller.get()
        
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `onResume keeps activity open if overlay is denied`() {
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns false
        every { PermissionChecker.isAccessibilityServiceEnabled(any()) } returns true
        every { PermissionChecker.isDeviceAdminEnabled(any()) } returns true
        every { PermissionChecker.isVpnPermissionGranted(any()) } returns true
        every { PermissionChecker.isBatteryOptimizationIgnored(any()) } returns true
        
        val controller = Robolectric.buildActivity(PermissionRepairActivity::class.java).setup()
        val activity = controller.get()
        
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `onResume keeps activity open if accessibility is denied`() {
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns true
        every { PermissionChecker.isAccessibilityServiceEnabled(any()) } returns false
        every { PermissionChecker.isDeviceAdminEnabled(any()) } returns true
        every { PermissionChecker.isVpnPermissionGranted(any()) } returns true
        every { PermissionChecker.isBatteryOptimizationIgnored(any()) } returns true
        
        val controller = Robolectric.buildActivity(PermissionRepairActivity::class.java).setup()
        val activity = controller.get()
        
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `createIntent returns correct flags`() {
        val intent = PermissionRepairActivity.createIntent(ApplicationProvider.getApplicationContext())
        assertEquals(PermissionRepairActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, intent.flags)
    }
}
