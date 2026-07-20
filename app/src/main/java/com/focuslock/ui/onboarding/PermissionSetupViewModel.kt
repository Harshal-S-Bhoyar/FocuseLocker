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
import kotlinx.coroutines.Job
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.focuslock.util.OemAutostartHelper
import com.focuslock.util.OemBatteryOptimizationHelper
import timber.log.Timber

data class PermissionState(
    val accessibilityGranted: Boolean = false,
    val deviceAdminGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val vpnGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val hasAutostartIntent: Boolean = false,
    val autostartAcknowledged: Boolean = false,
    val hasBatteryOptimizationIntent: Boolean = false,
    val batteryOptimizationAcknowledged: Boolean = false
) {
    val allCriticalGranted: Boolean
        get() = accessibilityGranted && deviceAdminGranted && overlayGranted && vpnGranted &&
                (!hasAutostartIntent || autostartAcknowledged) &&
                (!hasBatteryOptimizationIntent || batteryOptimizationAcknowledged)
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

    private var refreshJob: Job? = null
    private var delayJob: Job? = null

    fun refreshPermissions() {
        Timber.d("PermissionSetupViewModel: refreshPermissions() called")
        refreshJob?.cancel()
        // ponytail: don't cancel delayJob here — launcher callbacks schedule delayed
        // re-reads that must survive the ON_RESUME immediate read
        refreshJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // ponytail: retry loop — OS permission state can lag 1-2s after Settings return.
            // ceiling: 3 attempts × 800ms = 2.4s max. upgrade: ContentObserver on Settings.Secure
            repeat(3) { attempt ->
                val status = permissionStatusProvider.getStatus()
                val autostartAck = appSettingsRepository.getSetting("autostart_acknowledged").firstOrNull() == "true"
                val batteryAck = appSettingsRepository.getSetting("battery_optimization_acknowledged").firstOrNull() == "true"
                val permissions = PermissionState(
                    accessibilityGranted = status.accessibilityGranted,
                    deviceAdminGranted = status.deviceAdminGranted,
                    overlayGranted = status.overlayGranted,
                    batteryOptimizationIgnored = status.batteryOptimizationIgnored,
                    vpnGranted = status.vpnGranted,
                    notificationGranted = status.notificationGranted,
                    hasAutostartIntent = OemAutostartHelper.getAutostartIntentsList(context).isNotEmpty(),
                    autostartAcknowledged = autostartAck,
                    hasBatteryOptimizationIntent = OemBatteryOptimizationHelper.getBatteryOptimizationIntentsList(context).isNotEmpty(),
                    batteryOptimizationAcknowledged = batteryAck
                )
                Timber.d("PermissionSetupViewModel: attempt=$attempt State=$permissions")
                _uiState.value = _uiState.value.copy(permissions = permissions)

                if (permissions.allCriticalGranted) return@launch
                if (attempt < 2) delay(800L)
            }
        }
    }

    fun refreshPermissionsDelayed(delayMs: Long = 1500L) {
        Timber.d("PermissionSetupViewModel: refreshPermissionsDelayed() called with delayMs=$delayMs")
        delayJob?.cancel()
        // ponytail: don't cancel refreshJob — let both immediate and delayed reads coexist
        delayJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

    fun onBatteryOptimizationGranted() {
        Timber.d("PermissionSetupViewModel: onBatteryOptimizationGranted() called")
        viewModelScope.launch {
            appSettingsRepository.setSetting("battery_optimization_acknowledged", "true")
            refreshPermissions()
        }
    }

    fun onContinueClicked() {
        Timber.d("PermissionSetupViewModel: onContinueClicked() called")
        if (_uiState.value.permissions.allCriticalGranted) {
            Timber.d("PermissionSetupViewModel: allCriticalGranted is true, proceeding to trusted contact")
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

    override fun onCleared() {
        super.onCleared()
        delayJob?.cancel()
        refreshJob?.cancel()
    }
}
