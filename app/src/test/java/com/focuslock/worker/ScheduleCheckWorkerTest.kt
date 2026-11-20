package com.focuslock.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.worker.ScheduleAlarmScheduler
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.ActivationResult
import com.focuslock.domain.usecase.ScheduleEngine
import com.focuslock.notification.SessionNotificationManager
import com.focuslock.util.PermissionChecker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScheduleCheckWorkerTest {

    private lateinit var context: Context
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var activateSessionUseCase: ActivateSessionUseCase
    private lateinit var notificationManager: SessionNotificationManager
    private lateinit var scheduleAlarmScheduler: ScheduleAlarmScheduler
    private lateinit var worker: ScheduleCheckWorker

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        scheduleRepository = mockk(relaxed = true)
        activateSessionUseCase = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        scheduleAlarmScheduler = mockk(relaxed = true)
        ScheduleCheckWorker.resetBackoffForTest()
        worker = ScheduleCheckWorker(
            context,
            mockk<WorkerParameters>(relaxed = true),
            scheduleRepository,
            ScheduleEngine(),
            activateSessionUseCase,
            notificationManager,
            scheduleAlarmScheduler
        )
        mockkObject(PermissionChecker)
        every { PermissionChecker.isAccessibilityServiceEnabled(any<Context>()) } returns true
        every { PermissionChecker.isOverlayPermissionGranted(any<Context>()) } returns true
    }

    @After
    fun teardown() {
        ScheduleCheckWorker.resetBackoffForTest()
        unmockkAll()
    }

    private val mondayWindow = ScheduleWindowEntity(weekday = 1, startMinutes = 1200, endMinutes = 840)
    private val monday2100 = LocalDateTime.of(2026, 10, 12, 21, 0)

    private fun liveSession() = Session(
        id = 2L,
        status = SessionStatus.ACTIVE,
        startedAt = 0L,
        expiresAt = Long.MAX_VALUE,
        unlockedEarly = false,
        unlockRequestedAt = null,
        createdAt = 0L
    )

    @Test
    fun `doWork retries when the check throws`() = runBlocking {
        coEvery { scheduleRepository.isScheduleEnabledSync() } throws IllegalStateException("storage locked")

        assertEquals(androidx.work.ListenableWorker.Result.retry(), worker.doWork())
    }

    @Test
    fun `every check re-arms the next window alarm`() = runBlocking {
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)

        worker.runCheck(monday2100)

        io.mockk.verify { scheduleAlarmScheduler.scheduleNext(any(), monday2100) }
    }

    @Test
    fun `due window fires activation with exact remaining millis`() = runBlocking {
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)
        coEvery { activateSessionUseCase.invoke(any()) } returns ActivationResult.Activated(7L, 0L)

        worker.runCheck(monday2100)

        coVerify { activateSessionUseCase.invoke(1020 * 60 * 1000L) }
        coVerify { scheduleRepository.setLastFiredSchedule(any()) }
        coVerify { notificationManager.notifyScheduleFired(match { "Monday" in it }) }
    }

    @Test
    fun `remaining millis follow the exact window end not rounded minutes`() = runBlocking {
        // Regression for the midnight spillover: the old minute math granted a full minute where
        // 106 seconds remained, pushing the session past the window boundary.
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns
            listOf(ScheduleWindowEntity(weekday = 6, startMinutes = 781, endMinutes = 1440))
        coEvery { activateSessionUseCase.invoke(any()) } returns ActivationResult.Activated(8L, 0L)

        worker.runCheck(LocalDateTime.of(2026, 10, 17, 23, 58, 14))

        coVerify { activateSessionUseCase.invoke(106_000L) }
    }

    @Test
    fun `paused schedule never activates`() = runBlocking {
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns false

        worker.runCheck(monday2100)

        coVerify(exactly = 0) { scheduleRepository.getWindowsSync() }
        coVerify(exactly = 0) { activateSessionUseCase.invoke(any()) }
    }

    @Test
    fun `no due window means no activation and no notice`() = runBlocking {
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)

        worker.runCheck(LocalDateTime.of(2026, 10, 12, 15, 0))

        coVerify(exactly = 0) { activateSessionUseCase.invoke(any()) }
        coVerify(exactly = 0) { notificationManager.notifyScheduleFired(any()) }
    }

    @Test
    fun `live session wins and notice stays silent`() = runBlocking {
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)
        coEvery { activateSessionUseCase.invoke(any()) } returns ActivationResult.AlreadyActive(liveSession())

        worker.runCheck(monday2100)

        coVerify { activateSessionUseCase.invoke(any()) }
        coVerify(exactly = 0) { scheduleRepository.setLastFiredSchedule(any()) }
        coVerify(exactly = 0) { notificationManager.notifyScheduleFired(any()) }
    }

    @Test
    fun `real activation failure does not suppress the next tick`() = runBlocking {
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)
        coEvery { activateSessionUseCase.invoke(any()) } returns
            ActivationResult.Failed(IllegalStateException("runtime unavailable"))
        var fakeNow = 1_000_000L
        ScheduleCheckWorker.clockMillis = { fakeNow }

        worker.runCheck(monday2100)
        worker.runCheck(monday2100)
        fakeNow += 1L

        // Only AlreadyActive earns the live-wins backoff; a real failure is retried next tick.
        coVerify(exactly = 2) { activateSessionUseCase.invoke(any()) }
        coVerify(exactly = 0) { notificationManager.notifyScheduleFired(any()) }
    }

    @Test
    fun `morning part of overnight window fires with exact remaining millis`() = runBlocking {
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)
        coEvery { activateSessionUseCase.invoke(any()) } returns ActivationResult.Activated(9L, 0L)

        worker.runCheck(LocalDateTime.of(2026, 10, 13, 8, 20))

        coVerify { activateSessionUseCase.invoke(340 * 60 * 1000L) }
        assertTrue(true)
    }

    @Test
    fun `dead accessibility skips activation records nothing and posts repair notice`() = runBlocking {
        every { PermissionChecker.isAccessibilityServiceEnabled(any<Context>()) } returns false
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)
        coEvery { activateSessionUseCase.invoke(any()) } returns ActivationResult.Activated(7L, 0L)

        worker.runCheck(monday2100)

        coVerify(exactly = 0) { activateSessionUseCase.invoke(any()) }
        coVerify(exactly = 0) { scheduleRepository.setLastFiredSchedule(any()) }
        coVerify(exactly = 0) { notificationManager.notifyScheduleFired(any()) }
        val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
        val started = mutableListOf<android.content.Intent>()
        var next = shadowApp.nextStartedService
        while (next != null) {
            started.add(next)
            next = shadowApp.nextStartedService
        }
        assertTrue(started.any { it.component?.className == "com.focuslock.service.overlay.PermissionEnforcementService" })
    }

    @Test
    fun `retry fires after permissions repaired`() = runBlocking {
        every { PermissionChecker.isAccessibilityServiceEnabled(any<Context>()) } returns false
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)
        coEvery { activateSessionUseCase.invoke(any()) } returns ActivationResult.Activated(7L, 0L)

        worker.runCheck(monday2100)

        coVerify(exactly = 0) { activateSessionUseCase.invoke(any()) }
        coVerify(exactly = 0) { scheduleRepository.setLastFiredSchedule(any()) }

        every { PermissionChecker.isAccessibilityServiceEnabled(any<Context>()) } returns true

        worker.runCheck(monday2100)

        coVerify(exactly = 1) { activateSessionUseCase.invoke(1020 * 60 * 1000L) }
        coVerify(exactly = 1) { scheduleRepository.setLastFiredSchedule(any()) }
    }

    @Test
    fun `live retry suppressed within window then fires after`() = runBlocking {
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)
        coEvery { scheduleRepository.getLastFiredSchedule() } returns null
        coEvery { activateSessionUseCase.invoke(any()) } returns ActivationResult.AlreadyActive(liveSession())
        var fakeNow = 1_000_000L
        ScheduleCheckWorker.clockMillis = { fakeNow }

        worker.runCheck(monday2100)
        worker.runCheck(monday2100)

        coVerify(exactly = 1) { activateSessionUseCase.invoke(any()) }
        coVerify(exactly = 0) { scheduleRepository.setLastFiredSchedule(any()) }

        // The live session ends and the suppression window has passed: the next tick retries
        // and this time starts the scheduled session.
        coEvery { activateSessionUseCase.invoke(any()) } returns ActivationResult.Activated(6L, 0L)
        fakeNow += ScheduleCheckWorker.LIVE_RETRY_SUPPRESS_MILLIS + 1L

        worker.runCheck(monday2100)

        coVerify(exactly = 2) { activateSessionUseCase.invoke(any()) }
        coVerify(exactly = 1) { scheduleRepository.setLastFiredSchedule(any()) }
        coVerify(exactly = 1) { notificationManager.notifyScheduleFired(any()) }
    }

    @Test
    fun `enforcement started once per occurrence`() = runBlocking {
        every { PermissionChecker.isAccessibilityServiceEnabled(any<Context>()) } returns false
        coEvery { scheduleRepository.isScheduleEnabledSync() } returns true
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)

        val dayOne = LocalDateTime.of(2026, 10, 12, 21, 0)
        val dayTwo = LocalDateTime.of(2026, 10, 19, 21, 0)
        drainStartedServices()

        worker.runCheck(dayOne)
        worker.runCheck(dayOne)

        org.junit.Assert.assertEquals(
            1, drainStartedServices().count { it.component?.className == "com.focuslock.service.overlay.PermissionEnforcementService" }
        )
        coVerify(exactly = 0) { activateSessionUseCase.invoke(any()) }
        coVerify(exactly = 0) { scheduleRepository.setLastFiredSchedule(any()) }

        worker.runCheck(dayTwo)

        org.junit.Assert.assertEquals(
            1, drainStartedServices().count { it.component?.className == "com.focuslock.service.overlay.PermissionEnforcementService" }
        )
    }

    private fun drainStartedServices(): List<android.content.Intent> {
        val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
        val started = mutableListOf<android.content.Intent>()
        var next = shadowApp.nextStartedService
        while (next != null) {
            started.add(next)
            next = shadowApp.nextStartedService
        }
        return started
    }
}
