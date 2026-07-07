package com.focuslock.integration

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.SessionWorkerScheduler
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.receiver.BootReceiver
import com.focuslock.util.PermissionChecker
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Functional / Integration test for the permission enforcement loop.
 * Simulates a device boot sequence where critical permissions are missing
 * and ensures the entire enforcement cascade triggers correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PermissionIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var bootReceiver: BootReceiver
    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private val sessionWorkerScheduler: SessionWorkerScheduler = mockk(relaxed = true)
    private val sessionRuntimeController: SessionRuntimeController = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        org.robolectric.shadows.ShadowLog.stream = System.out
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
        
        context = ApplicationProvider.getApplicationContext()
        mockkObject(PermissionChecker)
        
        bootReceiver = spyk(BootReceiver()).apply {
            this.sessionRepository = this@PermissionIntegrationTest.sessionRepository
            this.sessionWorkerScheduler = this@PermissionIntegrationTest.sessionWorkerScheduler
            this.sessionRuntimeController = this@PermissionIntegrationTest.sessionRuntimeController
            this.ioDispatcher = testDispatcher
        }
        
        val dummyPendingResult = mockk<android.content.BroadcastReceiver.PendingResult>(relaxed = true)
        every { bootReceiver.goAsync() } returns dummyPendingResult
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `Integration - Boot with active session and missing accessibility triggers enforcement overlay`() = runTest {
        // 1. Setup Environment State
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        val activeSession = Session(
            id = 1L,
            status = SessionStatus.ACTIVE,
            startedAt = System.currentTimeMillis() - 60000,
            expiresAt = System.currentTimeMillis() + 60000, // Active
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = System.currentTimeMillis() - 60000
        )

        // Mock database and system permissions
        coEvery { sessionRepository.getActiveSessionSync() } returns activeSession
        every { PermissionChecker.isAccessibilityServiceEnabled(any<Context>()) } returns false // Accessibility killed by OS
        every { PermissionChecker.isOverlayPermissionGranted(any<Context>()) } returns true     // Overlay still available

        // 2. Trigger Boot sequence
        bootReceiver.processBootIntent(context, bootIntent)
        advanceUntilIdle()

        // 3. Verify Functional Cascade
        // A. Verify session timers were rescheduled
        verify { sessionWorkerScheduler.scheduleSessionTimers(activeSession.expiresAt!!) }
        
        // B. Verify runtime started
        verify { sessionRuntimeController.startRuntime(any()) }

        // C. Verify Watchdog & Enforcement services were launched
        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        val startedServices = mutableListOf<Intent>()
        var s = shadowApp.nextStartedService
        while(s != null) {
            startedServices.add(s)
            s = shadowApp.nextStartedService
        }

        val watchdogIntent = startedServices.find { it.component?.className == "com.focuslock.service.watchdog.PermissionWatchdogService" }
        assertNotNull("System must launch PermissionWatchdogService on boot", watchdogIntent)
        
        val overlayIntent = startedServices.find { it.component?.className == "com.focuslock.service.overlay.PermissionEnforcementService" }
        assertNotNull("System must launch PermissionEnforcementService because accessibility is disabled", overlayIntent)
        assertEquals("com.focuslock.action.START_ENFORCEMENT", overlayIntent?.action)
    }
}
