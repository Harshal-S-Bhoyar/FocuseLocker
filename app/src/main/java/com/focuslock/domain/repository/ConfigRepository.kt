package com.focuslock.domain.repository

interface ConfigRepository {
    suspend fun getNextDnsProfileId(): String
    suspend fun isAutoModeEnabled(): Boolean
}
