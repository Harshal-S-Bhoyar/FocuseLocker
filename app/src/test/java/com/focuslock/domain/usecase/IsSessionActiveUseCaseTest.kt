package com.focuslock.domain.usecase

import com.focuslock.data.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IsSessionActiveUseCaseTest {

    private val sessionRepository = mockk<SessionRepository>()
    private val cleanupStaleSessionUseCase = mockk<CleanupStaleSessionUseCase>(relaxed = true)
    private val useCase = IsSessionActiveUseCase(sessionRepository, cleanupStaleSessionUseCase)

    @Test
    fun `invoke returns true when session is active`() = runTest {
        coEvery { sessionRepository.hasActiveSession() } returns true

        val result = useCase()
        assertEquals(true, result)
    }

    @Test
    fun `invoke returns false when session is not active`() = runTest {
        coEvery { sessionRepository.hasActiveSession() } returns false

        val result = useCase()
        assertEquals(false, result)
    }

    @Test
    fun `stale cleanup short circuits active-session lookup`() = runTest {
        coEvery { cleanupStaleSessionUseCase() } returns true

        val result = useCase()

        assertEquals(false, result)
        coVerify(exactly = 0) { sessionRepository.hasActiveSession() }
    }

    @Test
    fun `cleanup failure is propagated without querying repository`() = runTest {
        val failure = IllegalStateException("cleanup failed")
        coEvery { cleanupStaleSessionUseCase() } throws failure

        try {
            useCase()
            throw AssertionError("Expected cleanup failure")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error)
        }
        coVerify(exactly = 0) { sessionRepository.hasActiveSession() }
    }

    @Test
    fun `repository failure is propagated after cleanup`() = runTest {
        val failure = IllegalStateException("session lookup failed")
        coEvery { cleanupStaleSessionUseCase() } returns false
        coEvery { sessionRepository.hasActiveSession() } throws failure

        try {
            useCase()
            throw AssertionError("Expected repository failure")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error)
        }
    }
}
