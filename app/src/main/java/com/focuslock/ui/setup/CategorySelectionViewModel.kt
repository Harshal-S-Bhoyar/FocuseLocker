package com.focuslock.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.model.BlockedCategory
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.ToggleCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the CategorySelectionScreen.
 * Manages the state of categories and handles toggling their active status.
 */
@HiltViewModel
class CategorySelectionViewModel @Inject constructor(
    getAllCategoriesUseCase: GetAllCategoriesUseCase,
    private val toggleCategoryUseCase: ToggleCategoryUseCase
) : ViewModel() {

    // Expose the list of categories as a StateFlow for Compose to observe
    val categories: StateFlow<List<BlockedCategory>> = getAllCategoriesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Toggles a category on or off and updates the database.
     * We use viewModelScope to ensure the update happens safely without blocking the main thread.
     */
    fun onCategoryToggled(categoryKey: String, isActive: Boolean) {
        viewModelScope.launch {
            toggleCategoryUseCase(categoryKey, isActive)
        }
    }
}
