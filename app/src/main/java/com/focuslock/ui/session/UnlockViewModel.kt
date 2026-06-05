package com.focuslock.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.BlockingRepository
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.repository.TrustedContactRepository
import com.focuslock.domain.repository.UnlockRepository
import com.focuslock.domain.usecase.GetSessionRemainingTimeUseCase
import com.focuslock.domain.usecase.RequestUnlockUseCase
import com.focuslock.domain.usecase.StartEmergencyBypassUseCase
import com.focuslock.domain.usecase.VerifyUnlockOtpUseCase
import com.focuslock.domain.usecase.VerifyUnlockResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UnlockUiState {
    object Idle : UnlockUiState()
    object Requesting : UnlockUiState()
    data class WaitingForOtp(val requestId: Long, val attemptCount: Int, val requestedAt: Long) : UnlockUiState()
    data class VerifyingOtp(val requestId: Long) : UnlockUiState()
    data class CooldownActive(val requestId: Long, val endsAt: Long) : UnlockUiState()
    data class EmergencyBypassActive(val requestId: Long, val endsAt: Long) : UnlockUiState()
    object Unlocked : UnlockUiState()
    data class Error(val message: String) : UnlockUiState()
}

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val unlockRepository: UnlockRepository,
    private val trustedContactRepository: TrustedContactRepository,
    private val blockingRepository: BlockingRepository,
    private val requestUnlockUseCase: RequestUnlockUseCase,
    private val verifyUnlockOtpUseCase: VerifyUnlockOtpUseCase,
    private val startEmergencyBypassUseCase: StartEmergencyBypassUseCase,
    private val getSessionRemainingTimeUseCase: GetSessionRemainingTimeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UnlockUiState>(UnlockUiState.Idle)
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private val _trustedContactEmail = MutableStateFlow("")
    val trustedContactEmail: StateFlow<String> = _trustedContactEmail.asStateFlow()

    private val _timeRemainingFormatted = MutableStateFlow("")
    val timeRemainingFormatted: StateFlow<String> = _timeRemainingFormatted.asStateFlow()

    init {
        viewModelScope.launch {
            val contact = trustedContactRepository.getContactSync()
            _trustedContactEmail.value = contact?.email ?: ""

            sessionRepository.getActiveSession().collect { session ->
                if (session == null) {
                    _uiState.value = UnlockUiState.Unlocked
                } else {
                    // Launch inner collection for this session
                    unlockRepository.getLatestRequest(session.id).collect { request ->
                        if (request == null) {
                            if (_uiState.value !is UnlockUiState.Requesting && _uiState.value !is UnlockUiState.Error) {
                                _uiState.value = UnlockUiState.Idle
                            }
                            return@collect
                        }

                        // Do not overwrite ephemeral states like VerifyingOtp unless necessary
                        if (_uiState.value is UnlockUiState.VerifyingOtp && request.status == UnlockRequestStatus.OTP_SENT) {
                            return@collect
                        }

                        // Do not overwrite specific Error states with the generic FAILED state from DB
                        if (_uiState.value is UnlockUiState.Error && request.status == UnlockRequestStatus.FAILED) {
                            return@collect
                        }

                        _uiState.value = when (request.status) {
                            UnlockRequestStatus.PENDING -> UnlockUiState.Requesting
                            UnlockRequestStatus.OTP_SENT -> UnlockUiState.WaitingForOtp(request.id, request.otpAttempts, request.requestedAt)
                            UnlockRequestStatus.OTP_VERIFIED -> UnlockUiState.VerifyingOtp(request.id)
                            UnlockRequestStatus.COOLDOWN -> UnlockUiState.CooldownActive(request.id, request.cooldownEndsAt ?: 0L)
                            UnlockRequestStatus.EMERGENCY -> UnlockUiState.EmergencyBypassActive(request.id, request.cooldownEndsAt ?: 0L)
                            UnlockRequestStatus.COMPLETED -> UnlockUiState.Unlocked
                            UnlockRequestStatus.FAILED -> UnlockUiState.Error("Previous request failed. You can try again.")
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            getSessionRemainingTimeUseCase().collect { millis ->
                if (millis <= 0) {
                    _timeRemainingFormatted.value = "00:00"
                } else {
                    val hours = millis / (1000 * 60 * 60)
                    val minutes = (millis % (1000 * 60 * 60)) / (1000 * 60)
                    _timeRemainingFormatted.value = if (hours > 0) {
                        String.format("%dh %02dm", hours, minutes)
                    } else {
                        String.format("%dm", minutes)
                    }
                }
            }
        }
    }

    fun requestUnlock() {
        viewModelScope.launch {
            // Guard: ensure session exists before touching DB
            val session = sessionRepository.getActiveSessionSync() ?: run {
                _uiState.value = UnlockUiState.Error("No active session found. Cannot request unlock.")
                return@launch
            }
            // Guard: ensure trusted contact is configured
            val contact = trustedContactRepository.getContactSync() ?: run {
                _uiState.value = UnlockUiState.Error("No trusted contact configured.")
                return@launch
            }

            _uiState.value = UnlockUiState.Requesting

            val categories = blockingRepository.getActiveCategoryKeys()
            val keywords = blockingRepository.getActiveKeywords().firstOrNull()?.map { it.keyword } ?: emptyList()

            val result = requestUnlockUseCase(
                sessionId = session.id,
                trustedContactEmail = contact.email,
                blockedCategories = categories,
                blockedKeywords = keywords,
                timeRemaining = _timeRemainingFormatted.value
            )

            // Only set Error here if the Flow collector hasn't already done so
            // (flow may have fired FAILED state already — that is fine, both say "failed")
            if (result.isFailure) {
                _uiState.value = UnlockUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to send OTP. Check your internet connection."
                )
            }
        }
    }

    fun verifyOtp(otp: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is UnlockUiState.WaitingForOtp) return@launch
            
            _uiState.value = UnlockUiState.VerifyingOtp(currentState.requestId)
            
            val contact = trustedContactRepository.getContactSync() ?: return@launch
            
            val result = verifyUnlockOtpUseCase(
                requestId = currentState.requestId,
                trustedContactEmail = contact.email,
                otp = otp
            )
            
            when (result) {
                is VerifyUnlockResult.Valid -> {
                    // Handled by repository collector
                }
                is VerifyUnlockResult.Invalid -> {
                    _uiState.value = UnlockUiState.Error("Invalid OTP. Please try again.")
                    // Fallback to waiting state after showing error? Handled by resetError
                }
                is VerifyUnlockResult.MaxAttemptsReached -> {
                    _uiState.value = UnlockUiState.Error("Max attempts reached. Please request a new OTP.")
                }
                is VerifyUnlockResult.Error -> {
                    _uiState.value = UnlockUiState.Error(result.message)
                }
            }
        }
    }

    fun startEmergencyBypass() {
        viewModelScope.launch {
            val session = sessionRepository.getActiveSessionSync() ?: return@launch
            val result = startEmergencyBypassUseCase(session.id)
            if (result.isFailure) {
                _uiState.value = UnlockUiState.Error(result.exceptionOrNull()?.message ?: "Failed to start emergency bypass")
            }
        }
    }
    
    fun resetToIdle() {
        _uiState.value = UnlockUiState.Idle
    }
}
