package com.focuslock.domain.usecase

import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetSessionRemainingTimeUseCaseTest {

    private val sessionRepository = mockk<SessionRepository>()
    private val useCase = GetSessionRemainingTimeUseCase(sessionRepository)

    @Test
    fun `invoke emits zero when there is no active session`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns null

        assertEquals(0L, useCase().first())
    }

    @Test
    fun `invoke emits zero immediately for an expired session`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns session(expiresAt = System.currentTimeMillis() - 1_000L)

        assertEquals(0L, useCase().first())
    }

    @Test
    fun `invoke emits positive remaining time for a future session`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns session(expiresAt = System.currentTimeMillis() + 5_000L)

        assertTrue(useCase().first() > 0L)
    }

    @Test
    fun `invoke emits final zero after a short session expires`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns session(expiresAt = System.currentTimeMillis() + 100L)

        val emissions = useCase().toList()

        assertTrue(emissions.first() > 0L)
        assertEquals(0L, emissions.last())
    }

    @Test
    fun `invoke propagates repository failure`() = runTest {
        val failure = IllegalStateException("session lookup failed")
        coEvery { sessionRepository.getActiveSessionSync() } throws failure

        try {
            useCase().first()
            throw AssertionError("Expected repository failure")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error)
        }
    }

    private fun session(expiresAt: Long) = Session(
        id = 1L,
        status = SessionStatus.ACTIVE,
        startedAt = expiresAt - 1_000L,
        expiresAt = expiresAt,
        unlockedEarly = false,
        unlockRequestedAt = null,
        createdAt = expiresAt - 1_000L
    )
}
