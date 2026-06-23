package com.focuslock.domain.usecase

import com.focuslock.data.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetSessionRemainingTimeUseCaseTest {

    private val sessionRepository = mockk<SessionRepository>()
    private val useCase = GetSessionRemainingTimeUseCase(sessionRepository)

    @Test
    fun `invoke emits zero when there is no active session`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns null

        assertEquals(0L, useCase().first())
    }
}
