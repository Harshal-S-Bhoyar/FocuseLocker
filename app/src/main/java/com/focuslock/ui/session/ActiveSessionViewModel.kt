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
import javax.inject.Inject

/**
 * ViewModel for the ActiveSessionScreen.
 * Provides the live countdown timer by exposing the remaining time flow.
 */
@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    getSessionRemainingTimeUseCase: GetSessionRemainingTimeUseCase,
    private val protectionStatusProvider: SessionProtectionStatusProvider
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

    fun refreshProtectionStatus() {
        _protectionStatus.value = protectionStatusProvider.getStatus()
    }
}
