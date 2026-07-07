package com.focuslock.integration

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.focuslock.service.accessibility.FocusAccessibilityService
import com.focuslock.service.overlay.PermissionEnforcementService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class SettingsGuardIntegrationTest {

    private lateinit var context: Context
    private lateinit var accessibilityService: FocusAccessibilityService

    @Before
    fun setup() {
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        
        context = ApplicationProvider.getApplicationContext()
        mockkObject(com.focuslock.util.PermissionChecker)
        every { com.focuslock.util.PermissionChecker.isOverlayPermissionGranted(any()) } returns true
        
        // Initialize FocusAccessibilityService
        accessibilityService = Robolectric.buildService(FocusAccessibilityService::class.java).create().get()
    }

    @After
    fun teardown() {
        FocusAccessibilityService.settingsGuardMode = false
        unmockkAll()
    }

    @Test
    fun `Integration Flow - Settings Guard triggers enforcement overlay on package change`() = runTest {
        // 1. Enable settings guard mode and simulate active session
        FocusAccessibilityService.settingsGuardMode = true
        
        val sessionActiveField = FocusAccessibilityService::class.java.getDeclaredField("isSessionActive")
        sessionActiveField.isAccessible = true
        sessionActiveField.set(accessibilityService, true)

        // 2. Simulate user leaving the settings app (e.g. going to home screen)
        val event = android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = "com.google.android.apps.nexuslauncher" // some other package
        
        accessibilityService.onAccessibilityEvent(event)

        // 3. Verify that the enforcement service was started
        val shadowApp = shadowOf(context as android.app.Application)
        
        // The service started should be PermissionEnforcementService
        var enforcementIntent: Intent? = null
        var s = shadowApp.nextStartedService
        val startedList = mutableListOf<String>()
        while(s != null) {
            startedList.add(s.component?.className ?: "null")
            if (s.component?.className == PermissionEnforcementService::class.java.name) {
                enforcementIntent = s
                break
            }
            s = shadowApp.nextStartedService
        }

        if (enforcementIntent == null) {
            throw RuntimeException("Enforcement not found! Started services: $startedList. isSessionActive=${com.focuslock.service.accessibility.FocusAccessibilityService::class.java.getDeclaredField("isSessionActive").apply{isAccessible=true}.getBoolean(accessibilityService)}")
        }
        
        assertEquals("com.focuslock.action.START_ENFORCEMENT", enforcementIntent.action)
        
        // Settings guard mode should be reset
        assertFalse("Settings guard mode should be disabled after enforcement triggered", FocusAccessibilityService.settingsGuardMode)
    }
}
