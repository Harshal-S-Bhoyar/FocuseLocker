package com.focuslock.domain.usecase

import com.focuslock.domain.model.SessionStatus
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.SessionWorkerScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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

    @Test
    fun `repository failure returns failure and skips cleanup`() = runTest {
        val failure = IllegalStateException("repository unavailable")
        coEvery { sessionRepository.endSession(2L, SessionStatus.CANCELLED) } throws failure

        val result = useCase(2L, SessionStatus.CANCELLED)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
        coVerify(exactly = 0) { workerScheduler.cancelSessionTimers() }
        coVerify(exactly = 0) { runtimeController.stopRuntime() }
    }

    @Test
    fun `worker cancellation failure returns failure and does not stop runtime`() = runTest {
        val failure = IllegalStateException("scheduler unavailable")
        coEvery { workerScheduler.cancelSessionTimers() } throws failure

        val result = useCase(3L, SessionStatus.COMPLETED)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
        coVerify { sessionRepository.endSession(3L, SessionStatus.COMPLETED) }
        coVerify(exactly = 0) { runtimeController.stopRuntime() }
    }

    @Test
    fun `runtime stop failure returns failure after persistence cleanup`() = runTest {
        val failure = IllegalStateException("runtime unavailable")
        coEvery { runtimeController.stopRuntime() } throws failure

        val result = useCase(4L, SessionStatus.CANCELLED)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
        coVerify { sessionRepository.endSession(4L, SessionStatus.CANCELLED) }
        coVerify { workerScheduler.cancelSessionTimers() }
    }

    @Test
    fun `cancellation exception is rethrown`() = runTest {
        coEvery { sessionRepository.endSession(5L, SessionStatus.COMPLETED) } throws CancellationException("cancelled")

        try {
            useCase(5L, SessionStatus.COMPLETED)
            throw AssertionError("Expected cancellation")
        } catch (error: CancellationException) {
            assertTrue(error.message == "cancelled")
        }
        coVerify(exactly = 0) { workerScheduler.cancelSessionTimers() }
        coVerify(exactly = 0) { runtimeController.stopRuntime() }
    }
}
