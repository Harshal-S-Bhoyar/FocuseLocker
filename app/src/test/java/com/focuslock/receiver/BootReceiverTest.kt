package com.focuslock.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.SessionWorkerScheduler
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.util.PermissionChecker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BootReceiverTest {

    private lateinit var context: Context
    private lateinit var bootReceiver: BootReceiver
    private lateinit var sessionRepository: SessionRepository
    private lateinit var sessionWorkerScheduler: SessionWorkerScheduler
    private lateinit var sessionRuntimeController: SessionRuntimeController

    @Before
    fun setup() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        Timber.plant(Timber.DebugTree())
        context = ApplicationProvider.getApplicationContext()
        sessionRepository = mockk(relaxed = true)
        sessionWorkerScheduler = mockk(relaxed = true)
        sessionRuntimeController = mockk(relaxed = true)

        bootReceiver = spyk(BootReceiver()).apply {
            this.sessionRepository = this@BootReceiverTest.sessionRepository
            this.sessionWorkerScheduler = this@BootReceiverTest.sessionWorkerScheduler
            this.sessionRuntimeController = this@BootReceiverTest.sessionRuntimeController
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
    fun `onReceive handles BOOT_COMPLETED and restarts session if active`() = runTest {
        bootReceiver.ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        
        val activeSession = Session(
            id = 1L,
            status = SessionStatus.ACTIVE,
            startedAt = System.currentTimeMillis() - 60000,
            expiresAt = System.currentTimeMillis() + 60000, // 1 min left
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = System.currentTimeMillis() - 60000
        )

        coEvery { sessionRepository.getActiveSessionSync() } returns activeSession
        every { PermissionChecker.isAccessibilityServiceEnabled(any<Context>()) } returns true

        bootReceiver.processBootIntent(context, intent)
        advanceUntilIdle()

        verify { sessionWorkerScheduler.scheduleSessionTimers(any()) }
        verify { sessionRuntimeController.startRuntime(any()) }
    }

    @Test
    fun `onReceive handles BOOT_COMPLETED and forces PermissionEnforcementService if accessibility is dead`() = runTest {
        bootReceiver.ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        
        val activeSession = Session(
            id = 1L,
            status = SessionStatus.ACTIVE,
            startedAt = System.currentTimeMillis() - 60000,
            expiresAt = System.currentTimeMillis() + 60000, // 1 min left
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = System.currentTimeMillis() - 60000
        )

        coEvery { sessionRepository.getActiveSessionSync() } returns activeSession
        // Simulate Accessibility being killed by OEM
        every { PermissionChecker.isAccessibilityServiceEnabled(any<Context>()) } returns false
        every { PermissionChecker.isOverlayPermissionGranted(any<Context>()) } returns true

        bootReceiver.processBootIntent(context, intent)
        advanceUntilIdle()
        
        // Verify that the permission enforcement service was started
        val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
        val nextIntent = shadowApp.peekNextStartedService()
        
        org.junit.Assert.assertNotNull(nextIntent)
        org.junit.Assert.assertEquals(
            "com.focuslock.service.overlay.PermissionEnforcementService",
            nextIntent?.component?.className
        )
    }
}
