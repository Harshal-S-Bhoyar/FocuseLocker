package com.focuslock.ui.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.model.BlockedCategory
import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.domain.model.TrustedContact
import com.focuslock.domain.repository.TrustedContactRepository
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val activateSessionUseCase: ActivateSessionUseCase
) : ViewModel() {

    // Retrieve duration passed via navigation arguments.
    // Default to 1 minute if not provided (fallback).
    val durationMillis: Long = savedStateHandle.get<Long>("durationMillis") ?: (1 * 60 * 1000L)

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

    fun lockInSession() {
        if (_activationState.value is ActivationState.Loading) return
        
        viewModelScope.launch {
            _activationState.value = ActivationState.Loading
            
            // This starts the VPN, accessibility tracking, and timer
            activateSessionUseCase(durationMillis).fold(
                onSuccess = { _ ->
                    _activationState.value = ActivationState.Success
                },
                onFailure = { error ->
                    _activationState.value = ActivationState.Error(
                        error.message ?: "An unknown error occurred while activating the session."
                    )
                }
            )
        }
    }

    sealed class ActivationState {
        object Idle : ActivationState()
        object Loading : ActivationState()
        object Success : ActivationState()
        data class Error(val message: String) : ActivationState()
    }
}
