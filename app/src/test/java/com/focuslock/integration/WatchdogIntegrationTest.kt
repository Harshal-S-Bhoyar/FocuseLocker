package com.focuslock.integration

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.service.overlay.PermissionEnforcementService
import com.focuslock.service.watchdog.PermissionWatchdogService
import com.focuslock.util.PermissionChecker
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
class WatchdogIntegrationTest {

    private lateinit var context: Context
    private lateinit var watchdogService: PermissionWatchdogService
    private lateinit var sessionRepository: SessionRepository

    @Before
    fun setup() {
        TestLogTree.clearLogs()
        Timber.uprootAll()
        Timber.plant(TestLogTree())
        
        context = ApplicationProvider.getApplicationContext()
        sessionRepository = mockk(relaxed = true)

        mockkObject(PermissionChecker)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `Integration Flow - Watchdog polling triggers enforcement when permissions revoked`() = runTest {
        watchdogService = Robolectric.buildService(PermissionWatchdogService::class.java).get()
        
        // Start service
        watchdogService.onCreate()

        // Mock private/injected properties AFTER onCreate so Hilt doesn't overwrite them
        val repoField = PermissionWatchdogService::class.java.getDeclaredField("sessionRepository")
        repoField.isAccessible = true
        repoField.set(watchdogService, sessionRepository)

        val scopeField = PermissionWatchdogService::class.java.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        scopeField.set(watchdogService, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)))

        // Simulate active session
        val activeSession = Session(
            id = 200L,
            status = SessionStatus.ACTIVE,
            startedAt = System.currentTimeMillis() - 10000,
            expiresAt = System.currentTimeMillis() + 86400000,
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = System.currentTimeMillis() - 10000
        )
        coEvery { sessionRepository.getActiveSessionSync() } returns activeSession

        // Initially permissions are granted
        every { PermissionChecker.isAccessibilityServiceEnabled(any()) } returns true
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns true

        watchdogService.onStartCommand(Intent(), 0, 1)
        runCurrent()

        // Advance time by 6 seconds (polling interval is 5000ms)
        advanceTimeBy(6000)
        runCurrent()

        // Verify enforcement NOT started since permissions are granted
        val shadowApp = shadowOf(context as android.app.Application)
        var enforcementIntent = shadowApp.nextStartedService
        assertNull("Enforcement should not start when permissions are granted", enforcementIntent)

        // Now revoke accessibility permission
        every { PermissionChecker.isAccessibilityServiceEnabled(any()) } returns false

        // Advance time again by another polling interval
        advanceTimeBy(6000)
        runCurrent()

        // Verify enforcement STARTED
        var foundEnforcement = false
        var s = shadowApp.nextStartedService
        val startedList = mutableListOf<String>()
        while(s != null) {
            startedList.add(s.component?.className ?: "null")
            if (s.component?.className == PermissionEnforcementService::class.java.name) {
                foundEnforcement = true
                assertEquals("com.focuslock.action.START_ENFORCEMENT", s.action)
                break
            }
            s = shadowApp.nextStartedService
        }

        if (!foundEnforcement) {
            val logs = com.focuslock.integration.TestLogTree.getLogs()
            throw RuntimeException("Enforcement not found! Started services: $startedList. isAccessibilityEnabled=${PermissionChecker.isAccessibilityServiceEnabled(context)}, isOverlayGranted=${PermissionChecker.isOverlayPermissionGranted(context)}. Logs:\n${logs.joinToString("\n")}")
        }
        
        watchdogService.onDestroy()
    }
}
