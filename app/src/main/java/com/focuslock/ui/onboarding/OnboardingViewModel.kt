package com.focuslock.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.usecase.CheckOnboardingStatusUseCase
import com.focuslock.domain.usecase.InitializeAppUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OnboardingEvent {
    object NavigateToPermissions : OnboardingEvent()
    object NavigateToActiveSession : OnboardingEvent()
}

data class OnboardingUiState(
    val isLoading: Boolean = true,
    val event: OnboardingEvent? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val initializeAppUseCase: InitializeAppUseCase,
    private val checkOnboardingStatusUseCase: CheckOnboardingStatusUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            try {
                initializeAppUseCase()
                val status = checkOnboardingStatusUseCase()

                val event = when {
                    status.hasActiveSession -> OnboardingEvent.NavigateToActiveSession
                    else -> null // Stay on onboarding — show welcome screen
                }

                _uiState.value = OnboardingUiState(isLoading = false, event = event)
            } catch (e: Exception) {
                _uiState.value = OnboardingUiState(isLoading = false, event = null)
            }
        }
    }

    fun onGetStartedClicked() {
        _uiState.value = _uiState.value.copy(
            event = OnboardingEvent.NavigateToPermissions
        )
    }

    fun onEventConsumed() {
        _uiState.value = _uiState.value.copy(event = null)
    }
}
