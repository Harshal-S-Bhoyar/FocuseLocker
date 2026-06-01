package com.focuslock.ui.onboarding

import com.focuslock.domain.usecase.SaveContactResult
import com.focuslock.domain.usecase.SaveTrustedContactUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class TrustedContactSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TrustedContactSetupViewModel
    private val saveTrustedContactUseCase: SaveTrustedContactUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TrustedContactSetupViewModel(saveTrustedContactUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `phone number change filters non-phone characters`() {
        viewModel.onPhoneNumberChanged("+91abc98765")
        assertEquals("+9198765", viewModel.uiState.value.phoneNumber)
    }

    @Test
    fun `phone number change clears error`() {
        // Trigger an error first by clicking send with short number
        viewModel.onSendOtpClicked()
        viewModel.onPhoneNumberChanged("+91")
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `send otp with short number sets error`() {
        viewModel.onPhoneNumberChanged("+91")
        viewModel.onSendOtpClicked()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.navigateToVerify)
    }

    @Test
    fun `successful send navigates to verify`() = runTest {
        coEvery { saveTrustedContactUseCase(any()) } returns SaveContactResult.OtpSent
        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.onSendOtpClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.navigateToVerify)
    }

    @Test
    fun `failed send shows error message`() = runTest {
        coEvery { saveTrustedContactUseCase(any()) } returns SaveContactResult.Error("Network error")
        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.onSendOtpClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.navigateToVerify)
    }

    @Test
    fun `navigation consumed resets flag`() = runTest {
        coEvery { saveTrustedContactUseCase(any()) } returns SaveContactResult.OtpSent
        viewModel.onPhoneNumberChanged("+919876543210")
        viewModel.onSendOtpClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onNavigationConsumed()
        assertFalse(viewModel.uiState.value.navigateToVerify)
    }
}
