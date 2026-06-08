package com.focuslock.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.session.SessionProtectionStatus
import com.focuslock.domain.session.SessionProtectionStatusProvider
import com.focuslock.domain.usecase.GetSessionRemainingTimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.repository.UnlockRepository
import com.focuslock.domain.usecase.DeactivateSessionUseCase
import com.focuslock.domain.model.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel for the ActiveSessionScreen.
 * Provides the live countdown timer by exposing the remaining time flow.
 */
data class ActiveUnlockInfo(
    val status: com.focuslock.domain.model.UnlockRequestStatus,
    val cooldownEndsAt: Long?
)

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    getSessionRemainingTimeUseCase: GetSessionRemainingTimeUseCase,
    private val protectionStatusProvider: SessionProtectionStatusProvider,
    private val sessionRepository: SessionRepository,
    unlockRepository: UnlockRepository,
    private val deactivateSessionUseCase: DeactivateSessionUseCase
) : ViewModel() {

    // A state flow that emits the remaining time in milliseconds every second.
    // If the time is <= 0, the session is over.
    val remainingTimeMillis: StateFlow<Long> = getSessionRemainingTimeUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = -1L // Use -1 to indicate initial loading state
        )

    private val _protectionStatus = MutableStateFlow(protectionStatusProvider.getStatus())
    val protectionStatus: StateFlow<SessionProtectionStatus> = _protectionStatus.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeUnlockInfo: StateFlow<ActiveUnlockInfo?> =
        sessionRepository.getActiveSession()
            .flatMapLatest { session ->
                if (session == null) flowOf(null)
                else unlockRepository.getLatestRequest(session.id).map { req -> 
                    req?.let { ActiveUnlockInfo(it.status, it.cooldownEndsAt) } 
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    val sessionActive: StateFlow<Boolean?> =
        sessionRepository.getActiveSession()
            .map { it != null }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    fun refreshProtectionStatus() {
        _protectionStatus.value = protectionStatusProvider.getStatus()
    }

    fun completeCooldownAndKillSession() {
        viewModelScope.launch {
            val session = sessionRepository.getActiveSessionSync()
            if (session != null) {
                deactivateSessionUseCase(session.id, SessionStatus.COMPLETED)
            }
        }
    }
}
