package com.focuslock.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.usecase.HasVerifiedContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the TimerSetupScreen.
 * Manages the duration logic and validation (minimum 15 mins, maximum 72 hours).
 */
@HiltViewModel
class TimerSetupViewModel @Inject constructor(
    private val hasVerifiedContactUseCase: HasVerifiedContactUseCase
) : ViewModel() {

    private val _hours = MutableStateFlow(0)
    val hours: StateFlow<Int> = _hours.asStateFlow()

    private val _minutes = MutableStateFlow(15)
    val minutes: StateFlow<Int> = _minutes.asStateFlow()

    private val _hasVerifiedContact = MutableStateFlow(false)
    val hasVerifiedContact: StateFlow<Boolean> = _hasVerifiedContact.asStateFlow()

    init {
        viewModelScope.launch {
            _hasVerifiedContact.value = hasVerifiedContactUseCase()
        }
    }

    fun updateHours(h: Int) {
        if (h in 0..72) {
            _hours.value = h
        }
    }

    fun updateMinutes(m: Int) {
        if (m in 0..59) {
            _minutes.value = m
        }
    }
    
    fun getDurationMillis(): Long {
        val totalMinutes = (_hours.value * 60) + _minutes.value
        // Enforce 15 min minimum
        val validMinutes = totalMinutes.coerceAtLeast(15)
        // Enforce 72 hours maximum
        val boundedMinutes = validMinutes.coerceAtMost(72 * 60)
        return boundedMinutes * 60 * 1000L
    }
    
    fun isValid(): Boolean {
        val totalMinutes = (_hours.value * 60) + _minutes.value
        return totalMinutes in 15..(72 * 60)
    }
}
