package com.focuslock.domain.usecase

import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.session.SessionRuntimeController
import com.focuslock.domain.worker.SessionWorkerScheduler
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
    private val useCase = ActivateSessionUseCase(sessionRepository, workerScheduler, runtimeController, cleanupStaleSessionUseCase)

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
        val result = useCase(15 * 60 * 1000L)

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already exists") == true)
    }

    @Test
    fun `invoke succeeds and schedules workers if no active session`() = runTest {
        // Arrange
        coEvery { sessionRepository.hasActiveSession() } returns false
        coEvery { sessionRepository.startSession(any()) } returns 1L

        val durationMillis = 15 * 60 * 1000L

        // Act
        val result = useCase(durationMillis)

        // Assert
        assertTrue(result.isSuccess)
        coVerify { sessionRepository.startSession(any()) }
        coVerify { workerScheduler.scheduleSessionTimers(any()) }
        coVerify { runtimeController.startRuntime(any()) }
    }

    @Test
    fun `invoke rolls back created session when runtime startup fails`() = runTest {
        coEvery { sessionRepository.hasActiveSession() } returns false
        coEvery { sessionRepository.startSession(any()) } returns 9L
        every { runtimeController.startRuntime(any()) } throws IllegalStateException("startup failed")

        val result = useCase(15 * 60 * 1000L)

        assertTrue(result.isFailure)
        coVerify { workerScheduler.cancelSessionTimers() }
        coVerify { runtimeController.stopRuntime() }
        coVerify { sessionRepository.endSession(9L, com.focuslock.domain.model.SessionStatus.CANCELLED) }
    }
}
