package com.focuslock.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.domain.usecase.AddKeywordUseCase
import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import com.focuslock.domain.usecase.RemoveKeywordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the KeywordSelectionScreen.
 * Manages the state of custom keywords, enforces limits (via UseCase),
 * and provides clear error feedback to the UI.
 */
@HiltViewModel
class KeywordSelectionViewModel @Inject constructor(
    getAllKeywordsUseCase: GetAllKeywordsUseCase,
    private val addKeywordUseCase: AddKeywordUseCase,
    private val removeKeywordUseCase: RemoveKeywordUseCase
) : ViewModel() {

    // Expose the current list of keywords
    val keywords: StateFlow<List<BlockedKeyword>> = getAllKeywordsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Manage error state to show feedback (e.g., limit reached)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun addKeyword(keyword: String) {
        viewModelScope.launch {
            _error.value = null // Clear previous errors
            addKeywordUseCase(keyword).onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun removeKeyword(id: Long) {
        viewModelScope.launch {
            _error.value = null
            removeKeywordUseCase(id)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
