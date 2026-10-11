package com.focuslock.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.data.apps.AppBlockSelectionStore
import com.focuslock.domain.model.InstalledApp
import com.focuslock.domain.usecase.GetLaunchableAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppBlockUi(
    val app: InstalledApp,
    val blocked: Boolean
)

@HiltViewModel
class AppBlockViewModel @Inject constructor(
    private val getLaunchableAppsUseCase: GetLaunchableAppsUseCase,
    private val selectionStore: AppBlockSelectionStore
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // ponytail: [300ms debounce, no icon cache; upgrade to cached icons + paging past ~500 apps]
    // Handle errors inside the transform: a failed query emits empty but keeps later queries alive
    @OptIn(FlowPreview::class)
    private val apps: StateFlow<List<InstalledApp>> = _query
        .debounce(300)
        .onEach { _loading.value = true }
        .mapLatest { q ->
            try {
                getLaunchableAppsUseCase(q)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        .onEach { _loading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // O(N) map over bounded app list; O(1) Set lookup per item
    val items: StateFlow<List<AppBlockUi>> = combine(apps, selectionStore.selected) { list, selected ->
        list.map { AppBlockUi(it, selected.contains(it.packageName)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChanged(q: String) {
        _query.value = q
    }

    fun onAppToggled(packageName: String, blocked: Boolean) {
        selectionStore.toggle(packageName, blocked)
    }
}
