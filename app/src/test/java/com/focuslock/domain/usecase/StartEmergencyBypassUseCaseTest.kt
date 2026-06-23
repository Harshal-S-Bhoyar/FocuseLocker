package com.focuslock.domain.usecase

import com.focuslock.domain.model.UnlockRequest
import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.data.repository.UnlockRepository
import com.focuslock.data.worker.UnlockWorkerScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartEmergencyBypassUseCaseTest {

    private lateinit var unlockRepository: UnlockRepository
    private lateinit var unlockWorkerScheduler: UnlockWorkerScheduler
    private lateinit var useCase: StartEmergencyBypassUseCase

    @Before
    fun setup() {
        unlockRepository = mockk(relaxed = true)
        unlockWorkerScheduler = mockk(relaxed = true)
        useCase = StartEmergencyBypassUseCase(
            unlockRepository = unlockRepository,
            unlockWorkerScheduler = unlockWorkerScheduler
        )
    }

    @Test
    fun `invoke with no existing emergency request returns success and schedules 24h timer`() = runTest {
        // Arrange
        val sessionId = 1L
        val newRequestId = 10L
        coEvery { unlockRepository.getLatestRequestSync(sessionId) } returns null
        coEvery { unlockRepository.createRequest(sessionId) } returns newRequestId

        // Act
        val result = useCase(sessionId)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { unlockRepository.createRequest(sessionId) }
        coVerify(exactly = 1) { unlockRepository.updateStatus(newRequestId, UnlockRequestStatus.EMERGENCY) }
        coVerify(exactly = 1) { unlockRepository.setCooldownEndsAt(eq(newRequestId), any()) }
        coVerify(exactly = 1) { unlockWorkerScheduler.scheduleEmergencyBypass(any()) }
    }

    @Test
    fun `invoke when emergency is already active returns failure`() = runTest {
        // Arrange
        val sessionId = 1L
        val activeEmergency = UnlockRequest(
            id = 5L,
            sessionId = sessionId,
            requestedAt = System.currentTimeMillis(),
            status = UnlockRequestStatus.EMERGENCY,
            otpAttempts = 0,
            cooldownEndsAt = System.currentTimeMillis() + 86400000L
        )
        coEvery { unlockRepository.getLatestRequestSync(sessionId) } returns activeEmergency

        // Act
        val result = useCase(sessionId)

        // Assert
        assertTrue(result.isFailure)
        assertEquals("Emergency bypass already active", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { unlockRepository.createRequest(any()) }
    }

    @Test
    fun `invoke when repository throws returns failure`() = runTest {
        // Arrange
        val sessionId = 1L
        coEvery { unlockRepository.getLatestRequestSync(sessionId) } throws Exception("Database error")

        // Act
        val result = useCase(sessionId)

        // Assert
        assertTrue(result.isFailure)
        assertEquals("Database error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke sets endsAt to approximately 24 hours from now`() = runTest {
        // Arrange
        val sessionId = 1L
        val newRequestId = 10L
        coEvery { unlockRepository.getLatestRequestSync(sessionId) } returns null
        coEvery { unlockRepository.createRequest(sessionId) } returns newRequestId
        
        val currentTime = System.currentTimeMillis()
        val expectedEndsAt = currentTime + (24 * 60 * 60 * 1000L)

        // Act
        val result = useCase(sessionId)

        // Assert
        assertTrue(result.isSuccess)
        
        val slot = io.mockk.slot<Long>()
        coVerify { unlockRepository.setCooldownEndsAt(newRequestId, capture(slot)) }
        
        val capturedEndsAt = slot.captured
        val diff = Math.abs(capturedEndsAt - expectedEndsAt)
        
        // Assert diff is less than 1 second (1000ms) to account for execution time
        assertTrue("EndsAt difference was $diff ms, expected < 1000ms", diff < 1000L)
    }
}
