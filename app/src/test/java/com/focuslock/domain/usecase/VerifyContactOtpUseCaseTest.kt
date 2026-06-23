package com.focuslock.domain.usecase


import com.focuslock.domain.model.TrustedContact
import com.focuslock.data.repository.TrustedContactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VerifyContactOtpUseCaseTest {

    private lateinit var useCase: VerifyContactOtpUseCase
    private val trustedContactRepository: TrustedContactRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        useCase = VerifyContactOtpUseCase(trustedContactRepository)
    }

    @Test
    fun `otp shorter than 6 digits returns InvalidOtp`() = runTest {
        val result = useCase("+919876543210", "12345")
        assertTrue(result is VerifyOtpResult.InvalidOtp)
    }

    @Test
    fun `otp with letters returns InvalidOtp`() = runTest {
        val result = useCase("+919876543210", "12345a")
        assertTrue(result is VerifyOtpResult.InvalidOtp)
    }

    @Test
    fun `valid otp verified by backend marks contact verified`() = runTest {
        val contact = TrustedContact(
            id = 1L,
            email = "+919876543210",
            isVerified = false,
            verifiedAt = null,
            createdAt = 0L
        )
        coEvery { trustedContactRepository.confirmOtp(any(), any()) } returns true
        coEvery { trustedContactRepository.getContactSync() } returns contact

        val result = useCase("+919876543210", "123456")

        assertTrue(result is VerifyOtpResult.Verified)
        coVerify { trustedContactRepository.markVerified(1L) }
    }

    @Test
    fun `backend returns not verified gives InvalidOtp`() = runTest {
        coEvery { trustedContactRepository.confirmOtp(any(), any()) } returns false

        val result = useCase("+919876543210", "123456")

        assertTrue(result is VerifyOtpResult.InvalidOtp)
    }

    @Test
    fun `api exception returns Error`() = runTest {
        coEvery { trustedContactRepository.confirmOtp(any(), any()) } throws RuntimeException("timeout")

        val result = useCase("+919876543210", "123456")

        assertTrue(result is VerifyOtpResult.Error)
    }
}
