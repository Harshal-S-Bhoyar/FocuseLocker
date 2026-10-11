package com.focuslock.data.apps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// ponytail: [in-memory only, lost on process death; persist to DataStore if selection must survive kills]
@Singleton
class AppBlockSelectionStore @Inject constructor() {
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    fun toggle(packageName: String, blocked: Boolean) {
        _selected.value = if (blocked) _selected.value + packageName else _selected.value - packageName
    }

    fun clear() {
        _selected.value = emptySet()
    }
}
