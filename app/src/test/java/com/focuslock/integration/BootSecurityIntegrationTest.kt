package com.focuslock.integration

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.SessionWorkerScheduler
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.receiver.BootReceiver
import com.focuslock.ui.overlay.PermissionRepairActivity
import com.focuslock.util.PermissionChecker
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class BootSecurityIntegrationTest {

    private lateinit var context: Context
    private lateinit var bootReceiver: BootReceiver
    private lateinit var sessionRepository: SessionRepository
    private lateinit var sessionWorkerScheduler: SessionWorkerScheduler
    private lateinit var sessionRuntimeController: SessionRuntimeController

    @Before
    fun setup() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        
        context = ApplicationProvider.getApplicationContext()
        sessionRepository = mockk(relaxed = true)
        sessionWorkerScheduler = mockk(relaxed = true)
        sessionRuntimeController = mockk(relaxed = true)

        bootReceiver = spyk(BootReceiver()).apply {
            this.sessionRepository = this@BootSecurityIntegrationTest.sessionRepository
            this.sessionWorkerScheduler = this@BootSecurityIntegrationTest.sessionWorkerScheduler
            this.sessionRuntimeController = this@BootSecurityIntegrationTest.sessionRuntimeController
            this.ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        }

        val dummyPendingResult = mockk<android.content.BroadcastReceiver.PendingResult>(relaxed = true)
        every { bootReceiver.goAsync() } returns dummyPendingResult

        mockkObject(PermissionChecker)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `Integration Flow - Boot to Permission Repair if accessibility is disabled`() = runTest {
        bootReceiver.ioDispatcher = StandardTestDispatcher(testScheduler)
        
        // 1. Simulate an active session existing in the database
        val activeSession = Session(
            id = 100L,
            status = SessionStatus.ACTIVE,
            startedAt = System.currentTimeMillis() - 100000,
            expiresAt = System.currentTimeMillis() + 100000, 
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = System.currentTimeMillis() - 100000
        )
        coEvery { sessionRepository.getActiveSessionSync() } returns activeSession

        // 2. Simulate Accessibility Service being killed by the OS (permissions disabled)
        every { PermissionChecker.isAccessibilityServiceEnabled(any()) } returns false
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns true

        // 3. Trigger Boot process
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        bootReceiver.processBootIntent(context, intent)
        advanceUntilIdle()

        // 4. Verify that BootReceiver scheduled the recovery
        verify { sessionWorkerScheduler.scheduleSessionTimers(activeSession.expiresAt!!) }
        verify { sessionRuntimeController.startRuntime(any()) }

        // 5. Verify the integration link: BootReceiver launched PermissionEnforcementService
        val shadowApp = shadowOf(context as android.app.Application)
        
        // The first service started is PermissionWatchdogService
        val watchdogIntent = shadowApp.nextStartedService
        assertNotNull("System should launch watchdog service", watchdogIntent)
        assertEquals(com.focuslock.service.watchdog.PermissionWatchdogService::class.java.name, watchdogIntent?.component?.className)

        // The second service started should be PermissionEnforcementService
        val enforcementIntent = shadowApp.nextStartedService
        assertNotNull("System should launch an enforcement service when permissions are dead", enforcementIntent)
        assertEquals(com.focuslock.service.overlay.PermissionEnforcementService::class.java.name, enforcementIntent?.component?.className)
    }
}
