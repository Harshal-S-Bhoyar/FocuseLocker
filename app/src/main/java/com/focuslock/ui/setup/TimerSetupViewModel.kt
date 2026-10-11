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
 * Hours roll over into days past 23 and borrow symmetrically; 7 days needs hours/minutes at 0.
 */
@HiltViewModel
class TimerSetupViewModel @Inject constructor(
    private val hasVerifiedContactUseCase: HasVerifiedContactUseCase
) : ViewModel() {

    private val _days = MutableStateFlow(0)
    val days: StateFlow<Int> = _days.asStateFlow()

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

    fun updateDays(d: Int) {
        if (d !in 0..MAX_DAYS) return
        // 7 days exact needs hours/minutes at 0; lowering days is always fine
        val total = d * MINUTES_PER_DAY + _hours.value * 60 + _minutes.value
        if (total <= MAX_TOTAL_MINUTES) {
            _days.value = d
        }
    }

    fun updateHours(h: Int) {
        Timber.d("TimerSetupViewModel: updateHours called with $h")
        // Normalize through the total so 24 rolls into days and -1 borrows; out-of-range totals refused
        val total = _days.value * MINUTES_PER_DAY + h * 60 + _minutes.value
        if (total in 1..MAX_TOTAL_MINUTES) {
            _days.value = total / MINUTES_PER_DAY
            _hours.value = (total % MINUTES_PER_DAY) / 60
        }
    }

    fun updateMinutes(m: Int) {
        Timber.d("TimerSetupViewModel: updateMinutes called with $m")
        if (m in 0..59) {
            _minutes.value = m
        }
    }

    fun totalMinutes(): Int = _days.value * MINUTES_PER_DAY + _hours.value * 60 + _minutes.value
    
    fun getDurationMillis(): Long {
        // Enforce 1 min minimum, 7-day maximum
        val boundedMinutes = totalMinutes().coerceIn(1, MAX_TOTAL_MINUTES)
        val durationMillis = boundedMinutes * 60 * 1000L
        Timber.d("TimerSetupViewModel: computed durationMillis = $durationMillis from days = ${_days.value}, hours = ${_hours.value}, minutes = ${_minutes.value}")
        return durationMillis
    }
    
    fun isValid(): Boolean {
        val valid = totalMinutes() in 1..MAX_TOTAL_MINUTES
        Timber.d("TimerSetupViewModel: isValid check returned $valid (total minutes = ${totalMinutes()})")
        return valid
    }

    private companion object {
        const val MAX_DAYS = 7
        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_TOTAL_MINUTES = 7 * 24 * 60
    }
}
