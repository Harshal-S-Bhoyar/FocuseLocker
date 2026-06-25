package com.focuslock.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.data.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionState(
    val accessibilityGranted: Boolean = false,
    val deviceAdminGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val vpnGranted: Boolean = false,
    val notificationGranted: Boolean = false
) {
    val allCriticalGranted: Boolean
        get() = accessibilityGranted && deviceAdminGranted && overlayGranted && vpnGranted && batteryOptimizationIgnored
}

data class PermissionSetupUiState(
    val permissions: PermissionState = PermissionState(),
    val navigateToTrustedContact: Boolean = false
)

@HiltViewModel
class PermissionSetupViewModel @Inject constructor(
    private val permissionStatusProvider: AppPermissionStatusProvider,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionSetupUiState())
    val uiState: StateFlow<PermissionSetupUiState> = _uiState.asStateFlow()

    fun refreshPermissions() {
        val status = permissionStatusProvider.getStatus()
        val permissions = PermissionState(
            accessibilityGranted = status.accessibilityGranted,
            deviceAdminGranted = status.deviceAdminGranted,
            overlayGranted = status.overlayGranted,
            batteryOptimizationIgnored = status.batteryOptimizationIgnored,
            vpnGranted = status.vpnGranted,
            notificationGranted = status.notificationGranted
        )
        _uiState.value = _uiState.value.copy(permissions = permissions)
    }

    fun onContinueClicked() {
        if (_uiState.value.permissions.allCriticalGranted) {
            viewModelScope.launch {
                appSettingsRepository.setSetting("permissions_granted", "true")
                _uiState.value = _uiState.value.copy(navigateToTrustedContact = true)
            }
        }
    }

    fun onNavigationConsumed() {
        _uiState.value = _uiState.value.copy(navigateToTrustedContact = false)
    }
}
