package com.focuslock.ui.onboarding

import com.focuslock.domain.model.TrustedContact
import com.focuslock.domain.repository.AppSettingsRepository
import com.focuslock.domain.repository.TrustedContactRepository
import com.focuslock.domain.usecase.SaveContactResult
import com.focuslock.domain.usecase.SaveTrustedContactUseCase
import com.focuslock.domain.usecase.VerifyContactOtpUseCase
import com.focuslock.domain.usecase.VerifyOtpResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyContactViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: VerifyContactViewModel
    private val verifyContactOtpUseCase: VerifyContactOtpUseCase = mockk()
    private val saveTrustedContactUseCase: SaveTrustedContactUseCase = mockk()
    private val trustedContactRepository: TrustedContactRepository = mockk()
    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)

    private val testPhone = "+919876543210"
    private val testContact = TrustedContact(
        id = 1L,
        email = testPhone,
        isVerified = false,
        verifiedAt = null,
        createdAt = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { trustedContactRepository.getContact() } returns flowOf(testContact)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when initialized phone number is loaded from repository`() = runTest {
        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testPhone, viewModel.uiState.value.email)
        assertEquals(3, viewModel.uiState.value.attemptsRemaining)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `when contact is null phone number defaults to empty`() = runTest {
        coEvery { trustedContactRepository.getContact() } returns flowOf(null)
        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.email)
    }

    @Test
    fun `onOtpChanged filters non-digits and limits to 6 characters`() = runTest {
        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onOtpChanged("12a34")
        assertEquals("1234", viewModel.uiState.value.otp)

        viewModel.onOtpChanged("1234567")
        assertEquals("123456", viewModel.uiState.value.otp)
    }

    @Test
    fun `onOtpChanged clears previous error message`() = runTest {
        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onVerifyClicked() // sets empty error
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onOtpChanged("1")
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onVerifyClicked with incomplete OTP sets error message`() = runTest {
        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onOtpChanged("12345")
        viewModel.onVerifyClicked()

        assertEquals("Enter the 6-digit OTP", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `successful verification marks onboarding complete and navigates`() = runTest {
        coEvery { verifyContactOtpUseCase(testPhone, "123456") } returns VerifyOtpResult.Verified

        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onOtpChanged("123456")
        viewModel.onVerifyClicked()
        
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.navigateToSetup)
        coVerify(exactly = 1) { appSettingsRepository.setOnboardingComplete() }
    }

    @Test
    fun `failed verification decrements attempts and shows message`() = runTest {
        coEvery { verifyContactOtpUseCase(testPhone, "111111") } returns VerifyOtpResult.InvalidOtp

        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onOtpChanged("111111")
        viewModel.onVerifyClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(2, viewModel.uiState.value.attemptsRemaining)
        assertEquals("", viewModel.uiState.value.otp)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("2 attempts"))
    }

    @Test
    fun `failed verification at zero attempts shows final error message`() = runTest {
        coEvery { verifyContactOtpUseCase(testPhone, "111111") } returns VerifyOtpResult.InvalidOtp

        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Attempt 1 -> 2 remaining
        viewModel.onOtpChanged("111111")
        viewModel.onVerifyClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        // Attempt 2 -> 1 remaining
        viewModel.onOtpChanged("111111")
        viewModel.onVerifyClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        // Attempt 3 -> 0 remaining
        viewModel.onOtpChanged("111111")
        viewModel.onVerifyClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.attemptsRemaining)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Request a new OTP"))
    }

    @Test
    fun `api exception during verification shows network error message`() = runTest {
        coEvery { verifyContactOtpUseCase(testPhone, "123456") } returns VerifyOtpResult.Error("Verification failed")

        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onOtpChanged("123456")
        viewModel.onVerifyClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(3, viewModel.uiState.value.attemptsRemaining) // attempts shouldn't change for server errors
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Verification failed"))
    }

    @Test
    fun `successful resend OTP resets attempts and clears inputs`() = runTest {
        coEvery { saveTrustedContactUseCase(testPhone) } returns SaveContactResult.OtpSent

        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate 1 failed attempt first
        coEvery { verifyContactOtpUseCase(testPhone, "111111") } returns VerifyOtpResult.InvalidOtp
        viewModel.onOtpChanged("111111")
        viewModel.onVerifyClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.onOtpChanged("123")

        viewModel.onResendOtpClicked()

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isResending)
        assertTrue(viewModel.uiState.value.resendSuccess)
        assertEquals(3, viewModel.uiState.value.attemptsRemaining)
        assertEquals("", viewModel.uiState.value.otp)
    }

    @Test
    fun `failed resend OTP shows error message`() = runTest {
        coEvery { saveTrustedContactUseCase(testPhone) } returns SaveContactResult.Error("API failed")

        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResendOtpClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isResending)
        assertFalse(viewModel.uiState.value.resendSuccess)
        assertEquals("Failed to resend OTP. Try again.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onResendBannerDismissed clears success flag`() = runTest {
        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { saveTrustedContactUseCase(testPhone) } returns SaveContactResult.OtpSent
        viewModel.onResendOtpClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResendBannerDismissed()
        assertFalse(viewModel.uiState.value.resendSuccess)
    }

    @Test
    fun `onNavigationConsumed resets navigation flag`() = runTest {
        viewModel = VerifyContactViewModel(
            verifyContactOtpUseCase,
            saveTrustedContactUseCase,
            trustedContactRepository,
            appSettingsRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { verifyContactOtpUseCase(testPhone, "123456") } returns VerifyOtpResult.Verified
        viewModel.onOtpChanged("123456")
        viewModel.onVerifyClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNavigationConsumed()
        assertFalse(viewModel.uiState.value.navigateToSetup)
    }
}
