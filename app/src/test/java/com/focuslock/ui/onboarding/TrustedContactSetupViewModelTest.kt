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
    fun `email change updates state`() {
        viewModel.onEmailChanged("test@example.com")
        assertEquals("test@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun `email change clears error`() {
        // Trigger an error first by clicking send with invalid email
        viewModel.onSendOtpClicked()
        viewModel.onEmailChanged("test@example")
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `send otp with empty email sets error`() {
        viewModel.onEmailChanged("")
        viewModel.onSendOtpClicked()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.navigateToVerify)
    }

    @Test
    fun `successful send navigates to verify`() = runTest(testDispatcher) {
        coEvery { saveTrustedContactUseCase(any()) } returns SaveContactResult.OtpSent
        viewModel.onEmailChanged("test@example.com")
        viewModel.onSendOtpClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.navigateToVerify)
    }

    @Test
    fun `failed send shows error message`() = runTest(testDispatcher) {
        coEvery { saveTrustedContactUseCase(any()) } returns SaveContactResult.Error("Network error")
        viewModel.onEmailChanged("test@example.com")
        viewModel.onSendOtpClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.navigateToVerify)
    }

    @Test
    fun `navigation consumed resets flag`() = runTest(testDispatcher) {
        coEvery { saveTrustedContactUseCase(any()) } returns SaveContactResult.OtpSent
        viewModel.onEmailChanged("test@example.com")
        viewModel.onSendOtpClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onNavigationConsumed()
        assertFalse(viewModel.uiState.value.navigateToVerify)
    }
}
