package com.focuslock.data.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.model.Session
import com.focuslock.util.PermissionChecker
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class AccessibilityHealthWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var sessionRepository: SessionRepository
    private lateinit var worker: AccessibilityHealthWorker

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        
        mockkObject(PermissionChecker)

        worker = AccessibilityHealthWorker(context, workerParams, sessionRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun doWork_withActiveSessionAndAccessibilityDead_returnsSuccess() = runBlocking {
        // Mock active session
        val mockSession = mockk<Session>()
        every { mockSession.expiresAt } returns System.currentTimeMillis() + 100000
        coEvery { sessionRepository.getActiveSessionSync() } returns mockSession

        // Mock accessibility disabled, others enabled
        every { PermissionChecker.isAccessibilityServiceEnabled(context) } returns false
        every { PermissionChecker.isOverlayPermissionGranted(context) } returns true
        every { PermissionChecker.isDeviceAdminEnabled(context) } returns true
        every { PermissionChecker.isVpnPermissionGranted(context) } returns true

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWork_withActiveSessionAndOverlayDead_returnsSuccess() = runBlocking {
        val mockSession = mockk<Session>()
        every { mockSession.expiresAt } returns System.currentTimeMillis() + 100000
        coEvery { sessionRepository.getActiveSessionSync() } returns mockSession

        every { PermissionChecker.isAccessibilityServiceEnabled(context) } returns true
        every { PermissionChecker.isOverlayPermissionGranted(context) } returns false
        every { PermissionChecker.isDeviceAdminEnabled(context) } returns true
        every { PermissionChecker.isVpnPermissionGranted(context) } returns true

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWork_withActiveSessionAndDeviceAdminDead_returnsSuccess() = runBlocking {
        val mockSession = mockk<Session>()
        every { mockSession.expiresAt } returns System.currentTimeMillis() + 100000
        coEvery { sessionRepository.getActiveSessionSync() } returns mockSession

        every { PermissionChecker.isAccessibilityServiceEnabled(context) } returns true
        every { PermissionChecker.isOverlayPermissionGranted(context) } returns true
        every { PermissionChecker.isDeviceAdminEnabled(context) } returns false
        every { PermissionChecker.isVpnPermissionGranted(context) } returns true

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWork_withActiveSessionAndVpnDead_returnsSuccess() = runBlocking {
        val mockSession = mockk<Session>()
        every { mockSession.expiresAt } returns System.currentTimeMillis() + 100000
        coEvery { sessionRepository.getActiveSessionSync() } returns mockSession

        every { PermissionChecker.isAccessibilityServiceEnabled(context) } returns true
        every { PermissionChecker.isOverlayPermissionGranted(context) } returns true
        every { PermissionChecker.isDeviceAdminEnabled(context) } returns true
        every { PermissionChecker.isVpnPermissionGranted(context) } returns false

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWork_withNoActiveSession_returnsSuccess() = runBlocking {
        coEvery { sessionRepository.getActiveSessionSync() } returns null
        every { PermissionChecker.isAccessibilityServiceEnabled(context) } returns true
        every { PermissionChecker.isOverlayPermissionGranted(context) } returns true
        every { PermissionChecker.isDeviceAdminEnabled(context) } returns true
        every { PermissionChecker.isVpnPermissionGranted(context) } returns true

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWork_withException_returnsRetry() = runBlocking {
        coEvery { sessionRepository.getActiveSessionSync() } throws RuntimeException("DB error")

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
