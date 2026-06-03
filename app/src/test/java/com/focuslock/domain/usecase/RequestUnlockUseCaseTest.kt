package com.focuslock.domain.usecase


import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.UnlockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RequestUnlockUseCaseTest {

    private lateinit var unlockRepository: UnlockRepository
    private lateinit var useCase: RequestUnlockUseCase

    @Before
    fun setup() {
        unlockRepository = mockk(relaxed = true)
        useCase = RequestUnlockUseCase(unlockRepository)
    }

    @Test
    fun `invoke success creates request and calls api`() = runTest {
        val sessionId = 1L
        val trustedContactEmail = "+1234567890"
        val blockedCategories = listOf("Social", "Games")
        val blockedKeywords = listOf("Reddit")
        val timeRemaining = "1h 30m"

        coEvery { unlockRepository.createRequest(sessionId) } returns 100L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns Result.success(true)

        val result = useCase(sessionId, trustedContactEmail, blockedCategories, blockedKeywords, timeRemaining)

        assertTrue(result.isSuccess)
        assertEquals(100L, result.getOrNull())
        
        coVerify { unlockRepository.createRequest(sessionId) }
        coVerify { unlockRepository.requestUnlock(any(), any(), any(), any()) }
        coVerify { unlockRepository.updateStatus(100L, UnlockRequestStatus.OTP_SENT) }
    }

    @Test
    fun `invoke failure updates status to FAILED`() = runTest {
        val sessionId = 1L
        val trustedContactEmail = "+1234567890"

        coEvery { unlockRepository.createRequest(sessionId) } returns 100L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns Result.failure(Exception("Network Error"))

        val result = useCase(sessionId, trustedContactEmail, emptyList(), emptyList(), "1h")

        assertTrue(result.isFailure)
        
        coVerify { unlockRepository.updateStatus(100L, UnlockRequestStatus.FAILED) }
    }
}
