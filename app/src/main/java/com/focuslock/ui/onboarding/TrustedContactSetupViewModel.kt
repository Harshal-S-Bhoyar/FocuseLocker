package com.focuslock.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.usecase.SaveContactResult
import com.focuslock.domain.usecase.SaveTrustedContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrustedContactSetupUiState(
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val navigateToVerify: Boolean = false
)

@HiltViewModel
class TrustedContactSetupViewModel @Inject constructor(
    private val saveTrustedContactUseCase: SaveTrustedContactUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrustedContactSetupUiState())
    val uiState: StateFlow<TrustedContactSetupUiState> = _uiState.asStateFlow()

    fun onPhoneNumberChanged(value: String) {
        // Only allow digits, +, spaces, dashes — no letters
        val filtered = value.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        _uiState.value = _uiState.value.copy(
            phoneNumber = filtered,
            errorMessage = null
        )
    }

    fun onSendOtpClicked() {
        val phone = _uiState.value.phoneNumber.trim()
        if (phone.length < 8) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Enter a valid phone number with country code (e.g. +91...)"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = saveTrustedContactUseCase(phone)) {
                is SaveContactResult.OtpSent -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        navigateToVerify = true
                    )
                }
                is SaveContactResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun onNavigationConsumed() {
        _uiState.value = _uiState.value.copy(navigateToVerify = false)
    }
}
