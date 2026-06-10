package com.focuslock.domain.model

sealed class FetchOutcome {
    data class Success(val signals: Map<String, String>) : FetchOutcome()
    data class Failed(val reason: String) : FetchOutcome()
}
