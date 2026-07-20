package com.focuslock.domain.repository

import com.focuslock.domain.model.FetchOutcome

interface PageSignalFetcher {
    suspend fun fetchPageSignals(urlStr: String): FetchOutcome
}
