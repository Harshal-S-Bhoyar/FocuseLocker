package com.focuslock.domain.usecase

import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.UnlockRepository
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
class RequestUnlockUseCaseTest {

    private lateinit var unlockRepository: UnlockRepository
    private lateinit var useCase: RequestUnlockUseCase

    @Before
    fun setup() {
        unlockRepository = mockk(relaxed = true)
        useCase = RequestUnlockUseCase(unlockRepository)
    }

    // --- HAPPY PATH ---

    @Test
    fun `success - creates request, calls backend, updates status to OTP_SENT`() = runTest {
        // Fact: backend returns success=true, repository returns requestId=100
        coEvery { unlockRepository.createRequest(1L) } returns 100L
        coEvery { unlockRepository.requestUnlock("test@test.com", any(), any(), any()) } returns Result.success(true)

        val result = useCase(
            sessionId = 1L,
            trustedContactEmail = "test@test.com",
            blockedCategories = listOf("Social"),
            blockedKeywords = listOf("reddit"),
            timeRemaining = "30m"
        )

        // Verify the result is success with the correct requestId
        assertTrue(result.isSuccess)
        assertEquals(100L, result.getOrNull())

        // Verify the DB was updated to OTP_SENT — this is what triggers UI navigation
        coVerify(exactly = 1) { unlockRepository.updateStatus(100L, UnlockRequestStatus.OTP_SENT) }
        // Verify FAILED was never called on success
        coVerify(exactly = 0) { unlockRepository.updateStatus(100L, UnlockRequestStatus.FAILED) }
    }

    // --- BACKEND FAILURE PATH ---

    @Test
    fun `backend returns success=false - updates status to FAILED, returns failure`() = runTest {
        // Fact: backend sends HTTP 200 but success=false (e.g., Brevo API error)
        coEvery { unlockRepository.createRequest(1L) } returns 100L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns Result.success(false)

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "30m")

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { unlockRepository.updateStatus(100L, UnlockRequestStatus.FAILED) }
        coVerify(exactly = 0) { unlockRepository.updateStatus(100L, UnlockRequestStatus.OTP_SENT) }
    }

    @Test
    fun `backend throws network exception - updates status to FAILED, error message contains network info`() = runTest {
        // Fact: this is what happens when DEBUG_BACKEND_URL="http://127.0.0.1:3000" on a real device
        coEvery { unlockRepository.createRequest(1L) } returns 100L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } throws
            java.net.ConnectException("Failed to connect to /127.0.0.1:3000")

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "30m")

        assertTrue(result.isFailure)
        // Verify the error message contains the network reason
        assertTrue(result.exceptionOrNull()?.message?.contains("Network error") == true)
        // Verify DB is cleaned up to FAILED
        coVerify(exactly = 1) { unlockRepository.updateStatus(100L, UnlockRequestStatus.FAILED) }
    }

    @Test
    fun `backend returns exception result - updates status to FAILED, returns failure`() = runTest {
        coEvery { unlockRepository.createRequest(1L) } returns 100L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns
            Result.failure(java.io.IOException("Timeout"))

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "30m")

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { unlockRepository.updateStatus(100L, UnlockRequestStatus.FAILED) }
    }

    // --- DB INSERT FAILURE PATH (the previously-unhandled null requestId case) ---

    @Test
    fun `createRequest throws - returns failure immediately, NO updateStatus called`() = runTest {
        // Fact: UnlockRequestDao uses OnConflictStrategy.ABORT — can throw SQLiteConstraintException
        coEvery { unlockRepository.createRequest(any()) } throws
            android.database.sqlite.SQLiteConstraintException("FOREIGN KEY constraint failed")

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "30m")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("local database") == true)
        // Verify updateStatus is NEVER called — there is no requestId to update
        coVerify(exactly = 0) { unlockRepository.updateStatus(any(), any()) }
        // Verify the backend was NEVER called — no point if we have no record
        coVerify(exactly = 0) { unlockRepository.requestUnlock(any(), any(), any(), any()) }
    }

    // --- CORRECTNESS CHECKS ---

    @Test
    fun `all four parameters are forwarded to requestUnlock correctly`() = runTest {
        coEvery { unlockRepository.createRequest(5L) } returns 200L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns Result.success(true)

        useCase(
            sessionId = 5L,
            trustedContactEmail = "contact@example.com",
            blockedCategories = listOf("Adult", "Gambling"),
            blockedKeywords = listOf("casino"),
            timeRemaining = "2h 15m"
        )

        // Verify all four args passed correctly — none dropped or reordered
        coVerify(exactly = 1) {
            unlockRepository.requestUnlock(
                trustedContactEmail = "contact@example.com",
                blockedCategories = listOf("Adult", "Gambling"),
                blockedKeywords = listOf("casino"),
                timeRemaining = "2h 15m"
            )
        }
    }

    @Test
    fun `empty categories and keywords are valid - request still goes through`() = runTest {
        coEvery { unlockRepository.createRequest(1L) } returns 10L
        coEvery { unlockRepository.requestUnlock(any(), any(), any(), any()) } returns Result.success(true)

        val result = useCase(1L, "test@test.com", emptyList(), emptyList(), "45m")

        assertTrue(result.isSuccess)
    }
}
