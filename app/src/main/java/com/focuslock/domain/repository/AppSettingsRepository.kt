package com.focuslock.domain.repository

import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    fun getSetting(key: String): Flow<String?>
    suspend fun getSettingValue(key: String): String?
    suspend fun setSetting(key: String, value: String)
    suspend fun initializeDefaultSettings()
    suspend fun isOnboardingComplete(): Boolean
    suspend fun setOnboardingComplete()
}
