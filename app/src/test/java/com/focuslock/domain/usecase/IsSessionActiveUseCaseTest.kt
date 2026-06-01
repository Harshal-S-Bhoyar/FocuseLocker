package com.focuslock.domain.usecase

import com.focuslock.domain.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IsSessionActiveUseCaseTest {

    private val sessionRepository = mockk<SessionRepository>()
    private val useCase = IsSessionActiveUseCase(sessionRepository)

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
}
