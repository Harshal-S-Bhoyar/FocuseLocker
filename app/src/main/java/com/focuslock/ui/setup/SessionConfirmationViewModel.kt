package com.focuslock.ui.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.model.BlockedCategory
import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.domain.model.TrustedContact
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.TrustedContactRepository
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.ActivationResult
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import com.focuslock.domain.usecase.ScheduleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * ViewModel for confirming and locking in a focus session.
 * It gathers all setup details (categories, keywords, duration, contact)
 * and handles the critical activation flow.
 */
@HiltViewModel
class SessionConfirmationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getAllCategoriesUseCase: GetAllCategoriesUseCase,
    getAllKeywordsUseCase: GetAllKeywordsUseCase,
    trustedContactRepository: TrustedContactRepository,
    private val activateSessionUseCase: ActivateSessionUseCase,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleEngine: ScheduleEngine
) : ViewModel() {

    // Retrieve duration passed via navigation arguments.
    // Default to 1 minute if not provided (fallback).
    val durationMillis: Long = savedStateHandle.get<Long>("durationMillis") ?: (1 * 60 * 1000L)

    // True only when the schedule hand-off opened this screen for a window that is already running.
    // Scopes the already-started shortcut: without it a one-shot start would silently adopt an
    // unrelated running session and drop the duration the user chose.
    private val liveWindow: Boolean = savedStateHandle.get<Boolean>("live") ?: false

    // Only active categories are shown
    val activeCategories: StateFlow<List<BlockedCategory>> = getAllCategoriesUseCase()
        .map { list -> list.filter { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val keywords: StateFlow<List<BlockedKeyword>> = getAllKeywordsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trustedContact: StateFlow<TrustedContact?> = trustedContactRepository.getVerifiedContact()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _activationState = MutableStateFlow<ActivationState>(ActivationState.Idle)
    val activationState = _activationState.asStateFlow()

    fun lockInSession(now: LocalDateTime = LocalDateTime.now()) {
        if (_activationState.value is ActivationState.Loading) return

        viewModelScope.launch {
            _activationState.value = ActivationState.Loading

            if (liveWindow) {
                // The hand-off remaining goes stale while this screen is open: re-resolve the
                // window now. Starting a full-duration replacement for an ended window is wrong,
                // and activating with the original duration overruns a nearly-done window.
                val remaining = currentLiveRemainingMillis(now)
                if (remaining == null) {
                    _activationState.value = ActivationState.Error("The scheduled window has ended.")
                    return@launch
                }
                // AlreadyActive is a success here too: the use case answers atomically that a
                // session is running, and this screen simply enters it.
                when (val result = activateSessionUseCase(remaining)) {
                    is ActivationResult.Activated,
                    is ActivationResult.AlreadyActive -> _activationState.value = ActivationState.Success
                    is ActivationResult.Failed -> _activationState.value = ActivationState.Error(
                        result.error.message ?: "An unknown error occurred while activating the session."
                    )
                }
                return@launch
            }

            // This starts the VPN, accessibility tracking, and timer
            when (val result = activateSessionUseCase(durationMillis)) {
                is ActivationResult.Activated,
                is ActivationResult.AlreadyActive -> _activationState.value = ActivationState.Success
                is ActivationResult.Failed -> _activationState.value = ActivationState.Error(
                    result.error.message ?: "An unknown error occurred while activating the session."
                )
            }
        }
    }

    private suspend fun currentLiveRemainingMillis(now: LocalDateTime): Long? {
        val windows = scheduleRepository.getWindowsSync()
        if (windows.isEmpty()) return null
        val weekdayIso = now.dayOfWeek.value
        val minutesNow = now.hour * 60 + now.minute
        val due = scheduleEngine.dueWindow(windows, weekdayIso, minutesNow) ?: return null
        val remaining = scheduleEngine.remainingMillis(due, now)
        // A window this short cannot host a valid session; treat it as ended.
        if (remaining < ActivateSessionUseCase.MIN_DURATION_MILLIS) return null
        return remaining
    }

    sealed class ActivationState {
        object Idle : ActivationState()
        object Loading : ActivationState()
        object Success : ActivationState()
        data class Error(val message: String) : ActivationState()
    }
}
