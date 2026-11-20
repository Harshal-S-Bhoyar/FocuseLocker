package com.focuslock.domain.usecase

import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.WorkScheduler
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivateSessionUseCaseTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val workScheduler = mockk<WorkScheduler>(relaxed = true)
    private val runtimeController = mockk<SessionRuntimeController>(relaxed = true)
    private val cleanupStaleSessionUseCase = mockk<CleanupStaleSessionUseCase>(relaxed = true)
    private val blockingRepository = mockk<com.focuslock.data.repository.BlockingRepository>(relaxed = true)
    private val appBlockSelectionStore = com.focuslock.data.apps.AppBlockSelectionStore()
    private val useCase = ActivateSessionUseCase(sessionRepository, workScheduler, runtimeController, cleanupStaleSessionUseCase, blockingRepository, appBlockSelectionStore)

    private fun liveSession(id: Long, expiresAt: Long) = Session(
        id = id,
        status = SessionStatus.ACTIVE,
        startedAt = 0L,
        expiresAt = expiresAt,
        unlockedEarly = false,
        unlockRequestedAt = null,
        createdAt = 0L
    )

    @Test
    fun `invoke fails if duration is outside supported range`() = runTest {
        val result = useCase(0L)

        assertTrue(result is ActivationResult.Failed)
    }

    @Test
    fun `invoke returns AlreadyActive with the existing session and never starts a second one`() = runTest {
        // Arrange: a live session is running. It is an outcome, not an error, so callers hand off
        // instead of showing "could not start now" forever.
        val existing = liveSession(id = 5L, expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L)
        coEvery { sessionRepository.getActiveSessionSync() } returns existing

        // Act
        val result = useCase(1 * 60 * 1000L)

        // Assert
        assertTrue(result is ActivationResult.AlreadyActive)
        assertEquals(existing, (result as ActivationResult.AlreadyActive).session)
        coVerify(exactly = 0) { sessionRepository.startSessionIfNoneActive(any(), any(), any()) }
        coVerify(exactly = 0) { workScheduler.scheduleSessionTimers(any()) }
        coVerify(exactly = 0) { runtimeController.startRuntime(any()) }
    }

    @Test
    fun `invoke succeeds and schedules workers if no active session`() = runTest {
        // Arrange
        coEvery { sessionRepository.getActiveSessionSync() } returns null
        coEvery { sessionRepository.startSessionIfNoneActive(any(), any(), any()) } returns 1L

        val durationMillis = 1 * 60 * 1000L

        // Act
        val result = useCase(durationMillis)

        // Assert
        assertTrue(result is ActivationResult.Activated)
        assertEquals(1L, (result as ActivationResult.Activated).sessionId)
        coVerify { sessionRepository.startSessionIfNoneActive(any(), any(), any()) }
        coVerify { workScheduler.scheduleSessionTimers(any()) }
        coVerify { runtimeController.startRuntime(any()) }
    }

    @Test
    fun `invoke rolls back created session when runtime startup fails`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns null
        coEvery { sessionRepository.startSessionIfNoneActive(any(), any(), any()) } returns 9L
        every { runtimeController.startRuntime(any()) } throws IllegalStateException("startup failed")

        val result = useCase(1 * 60 * 1000L)

        assertTrue(result is ActivationResult.Failed)
        coVerify { workScheduler.cancelSessionTimers() }
        coVerify { runtimeController.stopRuntime() }
        coVerify { sessionRepository.endSession(9L, com.focuslock.domain.model.SessionStatus.CANCELLED) }
    }

    @Test
    fun `invoke permits activation after stale cleanup even when active record exists`() = runTest {
        // After the cleanup step the stale record is gone, so the re-read sees nothing live.
        coEvery { cleanupStaleSessionUseCase() } returns true
        coEvery { sessionRepository.getActiveSessionSync() } returns null
        coEvery { sessionRepository.startSessionIfNoneActive(any(), any(), any()) } returns 42L

        val result = useCase(168L * 60L * 60L * 1000L)

        assertTrue(result is ActivationResult.Activated)
        assertEquals(42L, (result as ActivationResult.Activated).sessionId)
        coVerify { sessionRepository.startSessionIfNoneActive(any(), any(), any()) }
    }

    @Test
    fun `losing the atomic insert to a concurrent activation returns the winner`() = runTest {
        // The race the atomic insert closes: this caller's check saw nothing live, the worker
        // inserted first, so this insert is rejected and must hand off instead of failing.
        val winner = liveSession(id = 11L, expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L)
        coEvery { sessionRepository.getActiveSessionSync() } returns null andThen winner
        coEvery { sessionRepository.startSessionIfNoneActive(any(), any(), any()) } returns null

        val result = useCase(1 * 60 * 1000L)

        assertTrue(result is ActivationResult.AlreadyActive)
        assertEquals(winner, (result as ActivationResult.AlreadyActive).session)
        coVerify(exactly = 0) { workScheduler.scheduleSessionTimers(any()) }
        coVerify(exactly = 0) { runtimeController.startRuntime(any()) }
    }

    @Test
    fun `a rejected insert without a live winner fails instead of reporting success`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns null
        coEvery { sessionRepository.startSessionIfNoneActive(any(), any(), any()) } returns null

        val result = useCase(1 * 60 * 1000L)

        assertTrue(result is ActivationResult.Failed)
        coVerify(exactly = 0) { workScheduler.scheduleSessionTimers(any()) }
    }

    @Test
    fun `invoke persists selected app keys and clears selection`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns null
        coEvery { sessionRepository.startSessionIfNoneActive(any(), any(), any()) } returns 7L
        appBlockSelectionStore.toggle("com.example.youtube", true)

        val result = useCase(1 * 60 * 1000L)

        assertTrue(result is ActivationResult.Activated)
        coVerify { sessionRepository.startSessionIfNoneActive(any(), any(), listOf("com.example.youtube")) }
        assertTrue(appBlockSelectionStore.selected.value.isEmpty())
    }
}
