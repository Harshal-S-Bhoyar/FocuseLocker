package com.focuslock.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.usecase.HasVerifiedContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the TimerSetupScreen.
 * Manages the duration logic and validation (minimum 1 min, maximum 7 days).
 */
@HiltViewModel
class TimerSetupViewModel @Inject constructor(
    private val hasVerifiedContactUseCase: HasVerifiedContactUseCase
) : ViewModel() {

    private val _hours = MutableStateFlow(0)
    val hours: StateFlow<Int> = _hours.asStateFlow()

    private val _minutes = MutableStateFlow(1)
    val minutes: StateFlow<Int> = _minutes.asStateFlow()

    private val _hasVerifiedContact = MutableStateFlow(false)
    val hasVerifiedContact: StateFlow<Boolean> = _hasVerifiedContact.asStateFlow()

    init {
        Timber.d("TimerSetupViewModel: init called")
        viewModelScope.launch {
            val hasContact = hasVerifiedContactUseCase()
            Timber.d("TimerSetupViewModel: hasVerifiedContact = $hasContact")
            _hasVerifiedContact.value = hasContact
        }
    }

    fun updateHours(h: Int) {
        Timber.d("TimerSetupViewModel: updateHours called with $h")
        if (h in 0..168) { // 7 days = 168 hours
            _hours.value = h
        }
    }

    fun updateMinutes(m: Int) {
        Timber.d("TimerSetupViewModel: updateMinutes called with $m")
        if (m in 0..59) {
            _minutes.value = m
        }
    }
    
    fun getDurationMillis(): Long {
        val totalMinutes = (_hours.value * 60) + _minutes.value
        // Enforce 1 min minimum
        val validMinutes = totalMinutes.coerceAtLeast(1)
        // Enforce 7-day maximum
        val boundedMinutes = validMinutes.coerceAtMost(168 * 60)
        val durationMillis = boundedMinutes * 60 * 1000L
        Timber.d("TimerSetupViewModel: computed durationMillis = $durationMillis from hours = ${_hours.value}, minutes = ${_minutes.value}")
        return durationMillis
    }
    
    fun isValid(): Boolean {
        val totalMinutes = (_hours.value * 60) + _minutes.value
        val valid = totalMinutes in 1..(168 * 60)
        Timber.d("TimerSetupViewModel: isValid check returned $valid (total minutes = $totalMinutes)")
        return valid
    }
}
