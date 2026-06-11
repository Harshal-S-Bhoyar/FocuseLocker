package com.focuslock.domain.model

sealed class FetchOutcome {
    data class Success(val signals: Map<String, String>) : FetchOutcome()
    object FetchTimeout : FetchOutcome()
    object FetchEmpty : FetchOutcome()
    data class FetchError(val reason: String) : FetchOutcome()
}
