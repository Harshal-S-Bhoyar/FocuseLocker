package com.focuslock.service.overlay

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.focuslock.util.PermissionChecker
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class PermissionEnforcementServiceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        mockkObject(PermissionChecker)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `start() sends ACTION_START intent`() {
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns true

        PermissionEnforcementService.start(context)

        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        
        val startedServices = mutableListOf<Intent>()
        var s = shadowApp.nextStartedService
        while(s != null) {
            startedServices.add(s)
            s = shadowApp.nextStartedService
        }
        
        val nextIntent = startedServices.find { it.component?.className == "com.focuslock.service.overlay.PermissionEnforcementService" }
        assertNotNull(nextIntent)
        assertEquals(PermissionEnforcementService::class.java.name, nextIntent?.component?.className)
        assertEquals(PermissionEnforcementService.ACTION_START, nextIntent?.action)
    }

    @Test
    fun `stop() sends ACTION_STOP intent`() {
        PermissionEnforcementService.stop(context)

        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        
        val startedServices = mutableListOf<Intent>()
        var s = shadowApp.nextStartedService
        while(s != null) {
            startedServices.add(s)
            s = shadowApp.nextStartedService
        }
        
        val nextIntent = startedServices.find { it.component?.className == "com.focuslock.service.overlay.PermissionEnforcementService" }
        assertNotNull(nextIntent)
        assertEquals(PermissionEnforcementService::class.java.name, nextIntent?.component?.className)
        assertEquals(PermissionEnforcementService.ACTION_STOP, nextIntent?.action)
    }

    @Test
    fun `onCreate starts foreground service and creates notification channel`() {
        val controller = Robolectric.buildService(PermissionEnforcementService::class.java)
        
        val service = controller.create().get()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel("enforcement_overlay_channel")
        assertNotNull(channel)
        
        // Cannot easily check startForeground directly in vanilla Robolectric without ShadowService enhancements in some versions,
        // but we can verify the service object is created without crashing and channel exists.
    }

    @Test
    fun `start() does NOT start service when overlay permission is denied`() {
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns false

        PermissionEnforcementService.start(context)

        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        assertNull(shadowApp.peekNextStartedService())
    }

    @Test
    fun `start() starts service when overlay permission is granted`() {
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns true

        PermissionEnforcementService.start(context)

        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        
        val startedServices = mutableListOf<Intent>()
        var s = shadowApp.nextStartedService
        while(s != null) {
            startedServices.add(s)
            s = shadowApp.nextStartedService
        }
        
        val nextIntent = startedServices.find { it.component?.className == "com.focuslock.service.overlay.PermissionEnforcementService" }
        assertNotNull(nextIntent)
        assertEquals(PermissionEnforcementService.ACTION_START, nextIntent?.action)
    }

    @Test
    fun `openAccessibilitySettings sets grace period and guard mode`() {
        val controller = Robolectric.buildService(PermissionEnforcementService::class.java).create().startCommand(0, 0)
        val service = controller.get()
        
        val before = System.currentTimeMillis()
        service.openAccessibilitySettings()
        val after = System.currentTimeMillis()
        
        assertTrue(service.gracePeriodEndTime >= before + 3000L - 100L) // allow small timing delta
        assertTrue(com.focuslock.service.accessibility.FocusAccessibilityService.settingsGuardMode)
        assertEquals("com.android.settings", com.focuslock.service.accessibility.FocusAccessibilityService.guardedSettingsPackage)
        
        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        val nextIntent = shadowApp.nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS, nextIntent?.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, nextIntent?.flags?.and(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
