package com.focuslock.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.repository.AppSettingsRepository
import com.focuslock.domain.repository.TrustedContactRepository
import com.focuslock.domain.usecase.SaveContactResult
import com.focuslock.domain.usecase.SaveTrustedContactUseCase
import com.focuslock.domain.usecase.VerifyContactOtpUseCase
import com.focuslock.domain.usecase.VerifyOtpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerifyContactUiState(
    val otp: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val attemptsRemaining: Int = 3,
    val isResending: Boolean = false,
    val resendSuccess: Boolean = false,
    val navigateToSetup: Boolean = false
)

@HiltViewModel
class VerifyContactViewModel @Inject constructor(
    private val verifyContactOtpUseCase: VerifyContactOtpUseCase,
    private val saveTrustedContactUseCase: SaveTrustedContactUseCase,
    private val trustedContactRepository: TrustedContactRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyContactUiState())
    val uiState: StateFlow<VerifyContactUiState> = _uiState.asStateFlow()

    init {
        loadPhoneNumber()
    }

    private fun loadPhoneNumber() {
        viewModelScope.launch {
            val contact = trustedContactRepository.getContact().first()
            _uiState.value = _uiState.value.copy(
                email = contact?.email ?: ""
            )
        }
    }

    fun onOtpChanged(value: String) {
        // Only digits, max 6 characters
        val filtered = value.filter { it.isDigit() }.take(6)
        _uiState.value = _uiState.value.copy(otp = filtered, errorMessage = null)
    }

    fun onVerifyClicked() {
        val state = _uiState.value
        if (state.otp.length != 6) {
            _uiState.value = state.copy(errorMessage = "Enter the 6-digit OTP")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (verifyContactOtpUseCase(state.email, state.otp)) {
                is VerifyOtpResult.Verified -> {
                    appSettingsRepository.setOnboardingComplete()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        navigateToSetup = true
                    )
                }
                is VerifyOtpResult.InvalidOtp -> {
                    val remaining = (_uiState.value.attemptsRemaining - 1).coerceAtLeast(0)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        otp = "",
                        attemptsRemaining = remaining,
                        errorMessage = if (remaining > 0)
                            "Incorrect OTP. $remaining attempt${if (remaining == 1) "" else "s"} remaining."
                        else
                            "Too many failed attempts. Request a new OTP."
                    )
                }
                is VerifyOtpResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Verification failed. Check your connection and try again."
                    )
                }
            }
        }
    }

    fun onResendOtpClicked() {
        val email = _uiState.value.email
        if (email.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isResending = true, errorMessage = null)

            when (saveTrustedContactUseCase(email)) {
                is SaveContactResult.OtpSent -> {
                    _uiState.value = _uiState.value.copy(
                        isResending = false,
                        resendSuccess = true,
                        attemptsRemaining = 3,
                        otp = ""
                    )
                }
                is SaveContactResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isResending = false,
                        errorMessage = "Failed to resend OTP. Try again."
                    )
                }
            }
        }
    }

    fun onResendBannerDismissed() {
        _uiState.value = _uiState.value.copy(resendSuccess = false)
    }

    fun onNavigationConsumed() {
        _uiState.value = _uiState.value.copy(navigateToSetup = false)
    }
}
