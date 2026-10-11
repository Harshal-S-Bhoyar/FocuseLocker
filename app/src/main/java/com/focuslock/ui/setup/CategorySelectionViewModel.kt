package com.focuslock.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.domain.model.BlockedCategory
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.ToggleCategoryUseCase
import com.focuslock.data.local.entity.CategoryKey
import com.focuslock.data.apps.AppBlockSelectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.focuslock.util.PrefsKeys

/**
 * ViewModel for the CategorySelectionScreen.
 * Manages the state of categories and handles toggling their active status.
 */
@HiltViewModel
class CategorySelectionViewModel @Inject constructor(
    getAllCategoriesUseCase: GetAllCategoriesUseCase,
    private val toggleCategoryUseCase: ToggleCategoryUseCase,
    private val appBlockSelectionStore: AppBlockSelectionStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    init {
        // New setup entry discards selections abandoned by a previous incomplete setup.
        // Back-navigation reuses this instance, so in-setup selections are preserved.
        appBlockSelectionStore.clear()
    }

    // Expose the list of categories as a StateFlow for Compose to observe
    val categories: StateFlow<List<BlockedCategory>> = getAllCategoriesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _autoMode = MutableStateFlow(
        context.getSharedPreferences(PrefsKeys.FILE, Context.MODE_PRIVATE)
            .getBoolean(PrefsKeys.AUTO_MODE_ENABLED, false)
    )
    val autoMode: StateFlow<Boolean> = _autoMode.asStateFlow()

    /**
     * Toggles a category on or off and updates the database.
     * We use viewModelScope to ensure the update happens safely without blocking the main thread.
     */
    fun onCategoryToggled(categoryKey: String, isActive: Boolean) {
        viewModelScope.launch {
            toggleCategoryUseCase(categoryKey, isActive)
        }
    }

    fun onAutoModeToggled(enabled: Boolean) {
        context.getSharedPreferences(PrefsKeys.FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(PrefsKeys.AUTO_MODE_ENABLED, enabled).apply()
        _autoMode.value = enabled

        if (enabled) {
            // Activate all user-facing categories. AUTO_BLOCKED excluded — always on.
            viewModelScope.launch {
                CategoryKey.ALL
                    .filter { it != CategoryKey.AUTO_BLOCKED }
                    .forEach { toggleCategoryUseCase(it, true) }
            }
        }
    }
}
