package com.focuslock.domain.usecase

import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.worker.WorkScheduler
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReconcileScheduledSessionUseCaseTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val scheduleRepository = mockk<ScheduleRepository>(relaxed = true)
    private val workScheduler = mockk<WorkScheduler>(relaxed = true)
    private val scheduleEngine = ScheduleEngine()
    private val useCase = ReconcileScheduledSessionUseCase(
        sessionRepository,
        scheduleRepository,
        scheduleEngine,
        workScheduler
    )

    private val now = LocalDateTime.of(2026, 10, 12, 21, 0) // Monday 21:00
    private val mondayWindow = ScheduleWindowEntity(id = 3L, weekday = 1, startMinutes = 1200, endMinutes = 840)

    private fun nowEpoch(): Long = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun activeSession(id: Long, expiresAt: Long) = Session(
        id = id,
        status = SessionStatus.ACTIVE,
        startedAt = 0L,
        expiresAt = expiresAt,
        unlockedEarly = false,
        unlockRequestedAt = null,
        createdAt = 0L
    )

    @Test
    fun `nothing active is a no-op`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns null

        assertEquals(ReconcileScheduledSessionUseCase.Result.NothingActive, useCase(now))
        coVerify(exactly = 0) { sessionRepository.extendSession(any(), any()) }
        coVerify(exactly = 0) { workScheduler.scheduleSessionTimers(any()) }
    }

    @Test
    fun `an expired record is not a live session`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns activeSession(1L, nowEpoch() - 1L)

        assertEquals(ReconcileScheduledSessionUseCase.Result.NothingActive, useCase(now))
        coVerify(exactly = 0) { sessionRepository.extendSession(any(), any()) }
    }

    @Test
    fun `due window ending later extends the session and reschedules timers`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns
            activeSession(3L, nowEpoch() + 10L * 60L * 1000L)
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)
        val windowEnd = nowEpoch() + 17L * 60L * 60L * 1000L

        assertEquals(ReconcileScheduledSessionUseCase.Result.Extended(windowEnd), useCase(now))
        coVerify { sessionRepository.extendSession(3L, windowEnd) }
        coVerify { workScheduler.scheduleSessionTimers(windowEnd) }
    }

    @Test
    fun `due window ending earlier keeps the session`() = runTest {
        val expiresAt = nowEpoch() + 30L * 60L * 60L * 1000L
        coEvery { sessionRepository.getActiveSessionSync() } returns activeSession(3L, expiresAt)
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(mondayWindow)

        assertEquals(ReconcileScheduledSessionUseCase.Result.Unchanged(expiresAt), useCase(now))
        coVerify(exactly = 0) { sessionRepository.extendSession(any(), any()) }
    }

    @Test
    fun `no due window keeps the session`() = runTest {
        val expiresAt = nowEpoch() + 30L * 60L * 1000L
        coEvery { sessionRepository.getActiveSessionSync() } returns activeSession(3L, expiresAt)
        coEvery { scheduleRepository.getWindowsSync() } returns
            listOf(ScheduleWindowEntity(id = 4L, weekday = 2, startMinutes = 600, endMinutes = 660))

        assertEquals(ReconcileScheduledSessionUseCase.Result.Unchanged(expiresAt), useCase(now))
        coVerify(exactly = 0) { sessionRepository.extendSession(any(), any()) }
    }
}
