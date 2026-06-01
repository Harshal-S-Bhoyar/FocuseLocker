package com.focuslock.domain.usecase

import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.session.SessionRuntimeController
import com.focuslock.domain.worker.SessionWorkerScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class DeactivateSessionUseCaseTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val workerScheduler = mockk<SessionWorkerScheduler>(relaxed = true)
    private val runtimeController = mockk<SessionRuntimeController>(relaxed = true)
    private val useCase = DeactivateSessionUseCase(sessionRepository, workerScheduler, runtimeController)

    @Test
    fun `invoke ends session and cancels workers`() = runTest {
        // Arrange
        coEvery { sessionRepository.endSession(1L, SessionStatus.COMPLETED) } returns Unit

        // Act
        val result = useCase(1L, SessionStatus.COMPLETED)

        // Assert
        assertTrue(result.isSuccess)
        coVerify { sessionRepository.endSession(1L, SessionStatus.COMPLETED) }
        coVerify { workerScheduler.cancelSessionTimers() }
        coVerify { runtimeController.stopRuntime() }
    }
}
