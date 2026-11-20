package com.focuslock.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.usecase.CheckOnboardingStatusUseCase
import com.focuslock.domain.usecase.InitializeAppUseCase
import com.focuslock.domain.usecase.OnboardingStatus
import com.focuslock.domain.permission.AppPermissionStatus
import com.focuslock.domain.permission.AppPermissionStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import com.focuslock.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    val event: OnboardingEvent? = null,
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val initializeAppUseCase: InitializeAppUseCase,
    private val checkOnboardingStatusUseCase: CheckOnboardingStatusUseCase,
    private val permissionStatusProvider: AppPermissionStatusProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var initJob: Job? = null

    init {
        Timber.d("OnboardingViewModel: init called")
        initialize()
    }

    private fun initialize() {
        Timber.d("OnboardingViewModel: starting initialize sequence")
        // Single-flight: a retry cancels the prior run so two sequences
        // can never race on _uiState.
        initJob?.cancel()
        initJob = viewModelScope.launch {
            try {
                initializeAppUseCase()
                // Status read and permission probe share no data: run together so slow binder IPC
                // never blocks routing behind the database read. supervisorScope is what keeps a
                // failing child from cancelling its sibling; awaiting status first preserves the
                // sequential contract this replaced, so a status error still takes precedence.
                val status: OnboardingStatus
                var permissions: AppPermissionStatus
                supervisorScope {
                    val statusDeferred = async { checkOnboardingStatusUseCase() }
                    val permissionsDeferred = async { probePermissions() }
                    status = statusDeferred.await()
                    permissions = permissionsDeferred.await()
                }
                
                // If accessibility appears disabled, wait and re-check (OS binding delay)
                if (!permissions.accessibilityGranted) {
                    Timber.d("OnboardingViewModel: Accessibility not granted initially, delaying and checking again...")
                    delay(1500L)
                    permissions = probePermissions()
                }

                Timber.d("OnboardingViewModel: Status = $status, Permissions = $permissions")

                val allPermissionsOk = permissions.accessibilityGranted && 
                    permissions.deviceAdminGranted && 
                    permissions.overlayGranted && 
                    permissions.vpnGranted

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
                // A wedged init step timed out: surface retry instead of hanging on black.
                if (e is TimeoutCancellationException) {
                    Timber.w("OnboardingViewModel: init step timed out, showing retry")
                    _uiState.value = OnboardingUiState(isLoading = false, error = "Startup timed out. Check your connection and try again.")
                    return@launch
                }
                Timber.d("OnboardingViewModel: initialize job cancelled (normal lifecycle event)")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "OnboardingViewModel: Error completing onboarding")
                _uiState.value = OnboardingUiState(isLoading = false, event = null)
            }
        }
    }

    private suspend fun probePermissions(): AppPermissionStatus {
        // Binder IPC off Main, with a ceiling. A single timeout re-probes before degrading: one slow
        // probe previously reported every permission as missing and routed the user into the repair
        // flow, which is a worse answer than a second attempt. Degrading after two means genuine
        // slowness still ends in the repair flow rather than a hang.
        return probePermissionsOnce() ?: probePermissionsOnce() ?: NOT_GRANTED
    }

    private suspend fun probePermissionsOnce(): AppPermissionStatus? {
        return try {
            withTimeout(PERMISSION_TIMEOUT_MS) {
                withContext(ioDispatcher) { permissionStatusProvider.getStatus() }
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w("OnboardingViewModel: permission probe timed out")
            null
        }
    }

    fun retry() {
        _uiState.value = OnboardingUiState(isLoading = true)
        initialize()
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

    companion object {
        const val PERMISSION_TIMEOUT_MS = 8_000L

        // Result used when the probe could not be completed, so the repair flow owns the outcome.
        private val NOT_GRANTED = AppPermissionStatus(false, false, false, false, false, false)
    }
}
