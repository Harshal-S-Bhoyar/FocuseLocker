package com.focuslock.domain.usecase

import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.data.repository.UnlockRepository
import com.focuslock.data.repository.SessionRepository
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
class VerifyUnlockOtpUseCaseTest {

    private lateinit var unlockRepository: UnlockRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var unlockWorkerScheduler: UnlockWorkerScheduler
    private lateinit var useCase: VerifyUnlockOtpUseCase

    @Before
    fun setup() {
        unlockRepository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        unlockWorkerScheduler = mockk(relaxed = true)
        useCase = VerifyUnlockOtpUseCase(
            unlockRepository = unlockRepository,
            sessionRepository = sessionRepository,
            unlockWorkerScheduler = unlockWorkerScheduler
        )
    }

    @Test
    fun `invoke with OTP less than 6 digits returns Invalid`() = runTest {
        // Arrange
        val requestId = 1L
        val contactNumber = "+1234567890"
        val otp = "12345" // 5 digits

        // Act
        val result = useCase(requestId, contactNumber, otp)

        // Assert
        assertEquals(VerifyUnlockResult.Invalid, result)
        coVerify(exactly = 0) { unlockRepository.incrementOtpAttempts(any()) }
    }

    @Test
    fun `invoke with OTP containing non-digits returns Invalid`() = runTest {
        // Arrange
        val requestId = 1L
        val contactNumber = "+1234567890"
        val otp = "123a56" // contains letter

        // Act
        val result = useCase(requestId, contactNumber, otp)

        // Assert
        assertEquals(VerifyUnlockResult.Invalid, result)
        coVerify(exactly = 0) { unlockRepository.incrementOtpAttempts(any()) }
    }

    @Test
    fun `invoke with valid OTP and backend success returns Valid and schedules cooldown`() = runTest {
        // Arrange
        val requestId = 1L
        val contactNumber = "+1234567890"
        val otp = "123456"
        
        coEvery { unlockRepository.incrementOtpAttempts(requestId) } returns 1
        coEvery { unlockRepository.verifyOtp(contactNumber, otp) } returns Result.success(true)

        // Act
        val result = useCase(requestId, contactNumber, otp)

        // Assert
        assertEquals(VerifyUnlockResult.Valid, result)
        coVerify { unlockRepository.updateStatus(requestId, UnlockRequestStatus.COOLDOWN) }
        coVerify { unlockRepository.setCooldownEndsAt(eq(requestId), any()) }
        coVerify { unlockWorkerScheduler.scheduleCooldownTimer(any()) }
    }

    @Test
    fun `invoke with valid OTP and backend failure and attempts less than 3 returns Invalid`() = runTest {
        // Arrange
        val requestId = 1L
        val contactNumber = "+1234567890"
        val otp = "123456"
        
        coEvery { unlockRepository.incrementOtpAttempts(requestId) } returns 2
        coEvery { unlockRepository.verifyOtp(contactNumber, otp) } returns Result.success(false)

        // Act
        val result = useCase(requestId, contactNumber, otp)

        // Assert
        assertEquals(VerifyUnlockResult.Invalid, result)
        coVerify(exactly = 0) { unlockRepository.updateStatus(any(), any()) }
    }

    @Test
    fun `invoke with valid OTP and backend failure and attempts equals 3 returns MaxAttemptsReached and status FAILED`() = runTest {
        // Arrange
        val requestId = 1L
        val contactNumber = "+1234567890"
        val otp = "123456"
        
        coEvery { unlockRepository.incrementOtpAttempts(requestId) } returns 3
        coEvery { unlockRepository.verifyOtp(contactNumber, otp) } returns Result.success(false)

        // Act
        val result = useCase(requestId, contactNumber, otp)

        // Assert
        assertEquals(VerifyUnlockResult.MaxAttemptsReached, result)
        coVerify { unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED) }
    }

    @Test
    fun `invoke when attempts already greater than 3 returns MaxAttemptsReached without API call`() = runTest {
        // Arrange
        val requestId = 1L
        val contactNumber = "+1234567890"
        val otp = "123456"
        
        coEvery { unlockRepository.incrementOtpAttempts(requestId) } returns 4

        // Act
        val result = useCase(requestId, contactNumber, otp)

        // Assert
        assertEquals(VerifyUnlockResult.MaxAttemptsReached, result)
        coVerify { unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED) }
        coVerify(exactly = 0) { unlockRepository.verifyOtp(any(), any()) }
    }

    @Test
    fun `invoke when network error occurs returns Error`() = runTest {
        // Arrange
        val requestId = 1L
        val contactNumber = "+1234567890"
        val otp = "123456"
        
        coEvery { unlockRepository.incrementOtpAttempts(requestId) } returns 1
        coEvery { unlockRepository.verifyOtp(contactNumber, otp) } returns Result.failure(java.io.IOException("Network timeout"))

        // Act
        val result = useCase(requestId, contactNumber, otp)

        // Assert
        assertTrue(result is VerifyUnlockResult.Error)
        assertEquals("Network error. Please try again.", (result as VerifyUnlockResult.Error).message)
    }
}
