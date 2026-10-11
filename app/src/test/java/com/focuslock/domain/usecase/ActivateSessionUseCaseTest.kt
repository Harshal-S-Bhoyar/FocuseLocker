package com.focuslock.domain.usecase

import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.SessionWorkerScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivateSessionUseCaseTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val workerScheduler = mockk<SessionWorkerScheduler>(relaxed = true)
    private val runtimeController = mockk<SessionRuntimeController>(relaxed = true)
    private val cleanupStaleSessionUseCase = mockk<CleanupStaleSessionUseCase>(relaxed = true)
    private val blockingRepository = mockk<com.focuslock.data.repository.BlockingRepository>(relaxed = true)
    private val appBlockSelectionStore = com.focuslock.data.apps.AppBlockSelectionStore()
    private val useCase = ActivateSessionUseCase(sessionRepository, workerScheduler, runtimeController, cleanupStaleSessionUseCase, blockingRepository, appBlockSelectionStore)

    @Test
    fun `invoke fails if duration is outside supported range`() = runTest {
        val result = useCase(0L)

        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke fails if active session already exists`() = runTest {
        // Arrange
        coEvery { sessionRepository.hasActiveSession() } returns true

        // Act
        val result = useCase(1 * 60 * 1000L)

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already exists") == true)
    }

    @Test
    fun `invoke succeeds and schedules workers if no active session`() = runTest {
        // Arrange
        coEvery { sessionRepository.hasActiveSession() } returns false
        coEvery { sessionRepository.startSession(any(), any(), any()) } returns 1L

        val durationMillis = 1 * 60 * 1000L

        // Act
        val result = useCase(durationMillis)

        // Assert
        assertTrue(result.isSuccess)
        coVerify { sessionRepository.startSession(any(), any(), any()) }
        coVerify { workerScheduler.scheduleSessionTimers(any()) }
        coVerify { runtimeController.startRuntime(any()) }
    }

    @Test
    fun `invoke rolls back created session when runtime startup fails`() = runTest {
        coEvery { sessionRepository.hasActiveSession() } returns false
        coEvery { sessionRepository.startSession(any(), any(), any()) } returns 9L
        every { runtimeController.startRuntime(any()) } throws IllegalStateException("startup failed")

        val result = useCase(1 * 60 * 1000L)

        assertTrue(result.isFailure)
        coVerify { workerScheduler.cancelSessionTimers() }
        coVerify { runtimeController.stopRuntime() }
        coVerify { sessionRepository.endSession(9L, com.focuslock.domain.model.SessionStatus.CANCELLED) }
    }

    @Test
    fun `invoke permits activation after stale cleanup even when active record exists`() = runTest {
        coEvery { cleanupStaleSessionUseCase() } returns true
        coEvery { sessionRepository.hasActiveSession() } returns true
        coEvery { sessionRepository.startSession(any(), any(), any()) } returns 42L

        val result = useCase(168L * 60L * 60L * 1000L)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == 42L)
        coVerify { sessionRepository.startSession(any(), any(), any()) }
    }

    @Test
    fun `invoke persists selected app keys and clears selection`() = runTest {
        coEvery { sessionRepository.hasActiveSession() } returns false
        coEvery { sessionRepository.startSession(any(), any(), any()) } returns 7L
        appBlockSelectionStore.toggle("com.example.youtube", true)

        val result = useCase(1 * 60 * 1000L)

        assertTrue(result.isSuccess)
        coVerify { sessionRepository.startSession(any(), any(), listOf("com.example.youtube")) }
        assertTrue(appBlockSelectionStore.selected.value.isEmpty())
    }
}
