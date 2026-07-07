package com.focuslock.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.usecase.CheckOnboardingStatusUseCase
import com.focuslock.domain.usecase.InitializeAppUseCase
import com.focuslock.domain.permission.AppPermissionStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

sealed class OnboardingEvent {
    object NavigateToPermissions : OnboardingEvent()
    object NavigateToActiveSession : OnboardingEvent()
    object NavigateToCategorySelection : OnboardingEvent()
    object NavigateToPermissionRepair : OnboardingEvent()
    object StartPermissionEnforcement : OnboardingEvent()
}

data class OnboardingUiState(
    val isLoading: Boolean = true,
    val event: OnboardingEvent? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val initializeAppUseCase: InitializeAppUseCase,
    private val checkOnboardingStatusUseCase: CheckOnboardingStatusUseCase,
    private val permissionStatusProvider: AppPermissionStatusProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        Timber.d("OnboardingViewModel: init called")
        initialize()
    }

    private fun initialize() {
        Timber.d("OnboardingViewModel: starting initialize sequence")
        viewModelScope.launch {
            try {
                initializeAppUseCase()
                val status = checkOnboardingStatusUseCase()
                var permissions = permissionStatusProvider.getStatus()
                
                // If accessibility appears disabled, wait and re-check (OS binding delay)
                if (!permissions.accessibilityGranted) {
                    Timber.d("OnboardingViewModel: Accessibility not granted initially, delaying and checking again...")
                    delay(1500L)
                    permissions = permissionStatusProvider.getStatus()
                }

                Timber.d("OnboardingViewModel: Status = $status, Permissions = $permissions")

                val allPermissionsOk = permissions.accessibilityGranted && 
                    permissions.deviceAdminGranted && 
                    permissions.overlayGranted && 
                    permissions.vpnGranted && 
                    permissions.batteryOptimizationIgnored

                val event = when {
                    status.hasActiveSession && !permissions.accessibilityGranted && permissions.overlayGranted -> {
                        Timber.i("OnboardingViewModel: active session exists but accessibility is missing, routing to StartPermissionEnforcement")
                        OnboardingEvent.StartPermissionEnforcement
                    }
                    status.hasActiveSession && !allPermissionsOk -> {
                        Timber.i("OnboardingViewModel: active session exists but permissions incomplete, routing to NavigateToPermissionRepair")
                        OnboardingEvent.NavigateToPermissionRepair
                    }
                    status.hasActiveSession -> {
                        Timber.i("OnboardingViewModel: active session exists and all permissions OK, routing to NavigateToActiveSession")
                        OnboardingEvent.NavigateToActiveSession
                    }
                    status.isOnboardingComplete && allPermissionsOk -> {
                        Timber.i("OnboardingViewModel: onboarding complete and permissions OK, routing to NavigateToCategorySelection")
                        OnboardingEvent.NavigateToCategorySelection
                    }
                    status.isOnboardingComplete && !allPermissionsOk -> {
                        Timber.i("OnboardingViewModel: onboarding complete but permissions incomplete, routing to NavigateToPermissionRepair")
                        OnboardingEvent.NavigateToPermissionRepair
                    }
                    else -> {
                        Timber.i("OnboardingViewModel: onboarding not complete, staying on welcome screen")
                        null // Stay on onboarding — show welcome screen
                    }
                }

                _uiState.value = OnboardingUiState(isLoading = false, event = event)
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("OnboardingViewModel: initialize job cancelled (normal lifecycle event)")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "OnboardingViewModel: Error completing onboarding")
                _uiState.value = OnboardingUiState(isLoading = false, event = null)
            }
        }
    }

    fun onGetStartedClicked() {
        Timber.d("OnboardingViewModel: onGetStartedClicked called")
        _uiState.value = _uiState.value.copy(
            event = OnboardingEvent.NavigateToPermissions
        )
    }

    fun onEventConsumed() {
        Timber.d("OnboardingViewModel: onEventConsumed called")
        _uiState.value = _uiState.value.copy(event = null)
    }
}
