package com.focuslock.data.repository

import com.focuslock.data.local.dao.AppSettingDao
import com.focuslock.data.local.entity.AppSettingEntity
import com.focuslock.data.local.entity.AppSettingKey
import com.focuslock.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepositoryImpl @Inject constructor(
    private val appSettingDao: AppSettingDao
) : AppSettingsRepository {

    override fun getSetting(key: String): Flow<String?> {
        return appSettingDao.getSetting(key).map { it?.value }
    }

    override suspend fun getSettingValue(key: String): String? {
        return appSettingDao.getSettingValue(key)
    }

    override suspend fun setSetting(key: String, value: String) {
        appSettingDao.insertSetting(AppSettingEntity(key = key, value = value))
    }

    override suspend fun initializeDefaultSettings() {
        val defaults = listOf(
            AppSettingEntity(AppSettingKey.ONBOARDING_COMPLETE, "false"),
            AppSettingEntity(AppSettingKey.DEVICE_ADMIN_ACTIVE, "false"),
            AppSettingEntity(AppSettingKey.ACCESSIBILITY_ACTIVE, "false"),
            AppSettingEntity(AppSettingKey.VPN_ACTIVE, "false"),
            AppSettingEntity(AppSettingKey.FIRST_INSTALL_DATE, System.currentTimeMillis().toString())
        )
        // Only insert if not already present — REPLACE handles upsert
        appSettingDao.insertSettings(defaults)
    }

    override suspend fun isOnboardingComplete(): Boolean {
        return appSettingDao.getSettingValue(AppSettingKey.ONBOARDING_COMPLETE) == "true"
    }

    override suspend fun setOnboardingComplete() {
        appSettingDao.updateSettingValue(AppSettingKey.ONBOARDING_COMPLETE, "true")
    }
}
