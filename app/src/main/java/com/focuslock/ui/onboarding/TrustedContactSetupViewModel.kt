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
    val email: String = "",
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

    fun onEmailChanged(value: String) {
        // Accept any character — this is an email field now
        _uiState.value = _uiState.value.copy(
            email = value,
            errorMessage = null
        )
    }

    fun onSendOtpClicked() {
        val email = _uiState.value.email.trim()
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$".toRegex()
        if (email.isBlank() || !emailRegex.matches(email)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Enter a valid email address (e.g. contact@example.com)"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = saveTrustedContactUseCase(email)) {
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
