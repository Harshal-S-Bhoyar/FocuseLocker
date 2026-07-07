package com.focuslock.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.data.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.focuslock.util.OemAutostartHelper
import timber.log.Timber

data class PermissionState(
    val accessibilityGranted: Boolean = false,
    val deviceAdminGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val vpnGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val hasAutostartIntent: Boolean = false,
    val autostartAcknowledged: Boolean = false
) {
    val allCriticalGranted: Boolean
        get() = accessibilityGranted && deviceAdminGranted && overlayGranted && vpnGranted && batteryOptimizationIgnored &&
                (!hasAutostartIntent || autostartAcknowledged)
}

data class PermissionSetupUiState(
    val permissions: PermissionState = PermissionState(),
    val navigateToTrustedContact: Boolean = false
)

@HiltViewModel
class PermissionSetupViewModel @Inject constructor(
    private val permissionStatusProvider: AppPermissionStatusProvider,
    private val appSettingsRepository: AppSettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionSetupUiState())
    val uiState: StateFlow<PermissionSetupUiState> = _uiState.asStateFlow()

    fun refreshPermissions() {
        Timber.d("PermissionSetupViewModel: refreshPermissions() called")
        val status = permissionStatusProvider.getStatus()
        viewModelScope.launch {
            val autostartAck = appSettingsRepository.getSetting("autostart_acknowledged").firstOrNull() == "true"
            val permissions = PermissionState(
                accessibilityGranted = status.accessibilityGranted,
                deviceAdminGranted = status.deviceAdminGranted,
                overlayGranted = status.overlayGranted,
                batteryOptimizationIgnored = status.batteryOptimizationIgnored,
                vpnGranted = status.vpnGranted,
                notificationGranted = status.notificationGranted,
                hasAutostartIntent = OemAutostartHelper.getAutostartIntent(context) != null,
                autostartAcknowledged = autostartAck
            )
            Timber.d("PermissionSetupViewModel: State updated - $permissions")
            _uiState.value = _uiState.value.copy(permissions = permissions)
        }
    }

    fun refreshPermissionsDelayed(delayMs: Long = 1500L) {
        Timber.d("PermissionSetupViewModel: refreshPermissionsDelayed() called with delayMs=$delayMs")
        viewModelScope.launch {
            delay(delayMs)
            refreshPermissions()
        }
    }

    fun onAutostartGranted() {
        Timber.d("PermissionSetupViewModel: onAutostartGranted() called")
        viewModelScope.launch {
            appSettingsRepository.setSetting("autostart_acknowledged", "true")
            refreshPermissions()
        }
    }

    fun onContinueClicked() {
        Timber.d("PermissionSetupViewModel: onContinueClicked() called")
        if (_uiState.value.permissions.allCriticalGranted) {
            Timber.d("PermissionSetupViewModel: allCriticalGranted is true, proceeding to trusted contact")
            viewModelScope.launch {
                appSettingsRepository.setSetting("permissions_granted", "true")
                _uiState.value = _uiState.value.copy(navigateToTrustedContact = true)
            }
        } else {
            Timber.w("PermissionSetupViewModel: Cannot continue, not all critical permissions are granted")
        }
    }

    fun onNavigationConsumed() {
        Timber.d("PermissionSetupViewModel: onNavigationConsumed() called")
        _uiState.value = _uiState.value.copy(navigateToTrustedContact = false)
    }
}
