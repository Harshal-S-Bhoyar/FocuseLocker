package com.focuslock.ui.common

/**
 * Generic UI state wrapper used across all screens.
 * ViewModels expose StateFlow<UiState<T>>.
 */
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
