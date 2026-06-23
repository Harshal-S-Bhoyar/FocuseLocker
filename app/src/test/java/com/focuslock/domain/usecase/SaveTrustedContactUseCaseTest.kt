package com.focuslock.domain.usecase


import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.repository.TrustedContactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveTrustedContactUseCaseTest {

    private lateinit var useCase: SaveTrustedContactUseCase
    private val trustedContactRepository: TrustedContactRepository = mockk(relaxed = true)
    private val sessionRepository: SessionRepository = mockk()

    @Before
    fun setUp() {
        useCase = SaveTrustedContactUseCase(trustedContactRepository, sessionRepository)
    }

    @Test
    fun `blank phone number returns error`() = runTest {
        val result = useCase("   ")
        assertTrue(result is SaveContactResult.Error)
    }

    @Test
    fun `cannot save contact during active session`() = runTest {
        coEvery { sessionRepository.hasActiveSession() } returns true

        val result = useCase("+919876543210")

        assertTrue(result is SaveContactResult.Error)
        val error = result as SaveContactResult.Error
        assertTrue(error.message.contains("active session"))
    }

    @Test
    fun `successful OTP send returns OtpSent`() = runTest {
        coEvery { sessionRepository.hasActiveSession() } returns false
        coEvery { trustedContactRepository.saveContact(any()) } returns 1L
        coEvery { trustedContactRepository.sendVerificationOtp(any()) } returns true

        val result = useCase("+919876543210")

        assertTrue(result is SaveContactResult.OtpSent)
        coVerify { trustedContactRepository.saveContact("+919876543210") }
    }

    @Test
    fun `api exception returns error`() = runTest {
        coEvery { sessionRepository.hasActiveSession() } returns false
        coEvery { trustedContactRepository.saveContact(any()) } returns 1L
        coEvery { trustedContactRepository.sendVerificationOtp(any()) } throws RuntimeException("Network error")

        val result = useCase("+919876543210")

        assertTrue(result is SaveContactResult.Error)
    }

    @Test
    fun `phone number is trimmed before saving`() = runTest {
        coEvery { sessionRepository.hasActiveSession() } returns false
        coEvery { trustedContactRepository.saveContact(any()) } returns 1L
        coEvery { trustedContactRepository.sendVerificationOtp(any()) } returns true

        useCase("  +919876543210  ")

        coVerify { trustedContactRepository.saveContact("+919876543210") }
    }
}
