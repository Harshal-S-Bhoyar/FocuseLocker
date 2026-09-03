package com.focuslock.service.watchdog

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.data.repository.SessionRepository
import com.focuslock.service.overlay.PermissionEnforcementService
import com.focuslock.util.PermissionChecker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowService

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class PermissionWatchdogServiceTest {

    private lateinit var service: PermissionWatchdogService
    private lateinit var sessionRepository: SessionRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk(relaxed = true)
        
        // Mock permission checkers
        mockkObject(PermissionChecker)
        mockkObject(PermissionEnforcementService)
        
        every { PermissionEnforcementService.start(any()) } returns Unit

        val controller = Robolectric.buildService(PermissionWatchdogService::class.java)
        service = controller.get()
        controller.create() // Hilt injects real Dispatchers.IO here

        // Override injected fields with mocks/test dispatchers
        service.ioDispatcher = testDispatcher
        service.sessionRepository = sessionRepository

        // Replace the serviceScope created in onCreate with one using the testDispatcher
        val scopeField = PermissionWatchdogService::class.java.getDeclaredField("serviceScope")
        scopeField.isAccessible = true
        val oldScope = scopeField.get(service) as kotlinx.coroutines.CoroutineScope
        oldScope.cancel() // cancel the old one
        scopeField.set(service, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + testDispatcher))
    }

    @After
    fun teardown() {
        service.onDestroy()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `onStartCommand should return START_STICKY`() {
        val intent = Intent()
        val result = service.onStartCommand(intent, 0, 1)
        assertEquals(Service.START_STICKY, result)
    }

    @Test
    fun `polling with all permissions granted continues running`() = runTest(testDispatcher) {
        val intent = Intent()
        val mockSession = com.focuslock.domain.model.Session(
            id = 1L,
            status = com.focuslock.domain.model.SessionStatus.ACTIVE,
            startedAt = System.currentTimeMillis() - 10000,
            expiresAt = System.currentTimeMillis() + 100000,
            createdAt = System.currentTimeMillis(),
            unlockedEarly = false,
            unlockRequestedAt = null
        )
        coEvery { sessionRepository.getActiveSessionSync() } returns mockSession

        every { PermissionChecker.isAccessibilityServiceEnabled(any()) } returns true
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns true
        every { PermissionChecker.isDeviceAdminEnabled(any()) } returns true
        every { PermissionChecker.isVpnPermissionGranted(any()) } returns true

        service.onStartCommand(intent, 0, 1)
        runCurrent()
        advanceTimeBy(6000)
        runCurrent()

        verify(exactly = 0) { PermissionEnforcementService.start(any()) }
        service.onDestroy()
    }

    @Test
    fun `polling with accessibility revoked and overlay granted triggers EnforcementService`() = runTest(testDispatcher) {
        val intent = Intent()
        val mockSession = com.focuslock.domain.model.Session(
            id = 1L,
            status = com.focuslock.domain.model.SessionStatus.ACTIVE,
            startedAt = System.currentTimeMillis() - 10000,
            expiresAt = System.currentTimeMillis() + 100000,
            createdAt = System.currentTimeMillis(),
            unlockedEarly = false,
            unlockRequestedAt = null
        )
        coEvery { sessionRepository.getActiveSessionSync() } returns mockSession

        // Return false for all checks to bypass the retries in startPolling()
        every { PermissionChecker.isAccessibilityServiceEnabled(any()) } returns false
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns true
        every { PermissionChecker.isDeviceAdminEnabled(any()) } returns true
        every { PermissionChecker.isVpnPermissionGranted(any()) } returns true

        service.onStartCommand(intent, 0, 1)
        runCurrent()
        advanceTimeBy(6000)
        runCurrent()

        verify { PermissionEnforcementService.start(any()) }
        service.onDestroy()
    }

    @Test
    fun `polling with overlay revoked triggers Repair Notification`() = runTest(testDispatcher) {
        val intent = Intent()
        val mockSession = com.focuslock.domain.model.Session(
            id = 1L,
            status = com.focuslock.domain.model.SessionStatus.ACTIVE,
            startedAt = System.currentTimeMillis() - 10000,
            expiresAt = System.currentTimeMillis() + 100000,
            createdAt = System.currentTimeMillis(),
            unlockedEarly = false,
            unlockRequestedAt = null
        )
        coEvery { sessionRepository.getActiveSessionSync() } returns mockSession

        every { PermissionChecker.isAccessibilityServiceEnabled(any()) } returns true
        every { PermissionChecker.isOverlayPermissionGranted(any()) } returns false
        every { PermissionChecker.isDeviceAdminEnabled(any()) } returns true
        every { PermissionChecker.isVpnPermissionGranted(any()) } returns true

        service.onStartCommand(intent, 0, 1)
        runCurrent()
        advanceTimeBy(6000)
        runCurrent()

        // We can't easily verify the notification with mockk directly without Robolectric ShadowNotificationManager
        // but we can ensure EnforcementService is not started, meaning it went to the 'else' block for critical failure
        verify(exactly = 0) { PermissionEnforcementService.start(any()) }
        
        val notificationManager = ApplicationProvider.getApplicationContext<Context>().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowManager = shadowOf(notificationManager)
        val criticalNotification = shadowManager.getNotification(4003)
        
        org.junit.Assert.assertNotNull("Critical failure notification should be fired", criticalNotification)
        assertEquals("FocusLock is Compromised", criticalNotification.extras.getString("android.title"))
        
        service.onDestroy()
    }
}
