package com.focuslock.data.repository

import com.focuslock.data.local.dao.AppSettingDao
import com.focuslock.data.local.dao.ScheduleDao
import com.focuslock.data.local.entity.AppSettingEntity
import com.focuslock.data.local.entity.AppSettingKey
import com.focuslock.data.local.entity.ScheduleWindowEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val appSettingDao: AppSettingDao
) {

    fun getWindows(): Flow<List<ScheduleWindowEntity>> = scheduleDao.getAllWindows()

    suspend fun getWindowsSync(): List<ScheduleWindowEntity> = scheduleDao.getAllWindowsSync()

    suspend fun upsertWindow(window: ScheduleWindowEntity): Long = scheduleDao.upsertWindow(window)

    suspend fun deleteWindow(id: Long) = scheduleDao.deleteWindow(id)

    suspend fun deleteAllWindows() = scheduleDao.deleteAllWindows()

    fun isScheduleEnabled(): Flow<Boolean> =
        appSettingDao.getSetting(AppSettingKey.SCHEDULE_ENABLED).map { it?.value == "true" }

    suspend fun isScheduleEnabledSync(): Boolean =
        appSettingDao.getSettingValue(AppSettingKey.SCHEDULE_ENABLED) == "true"

    suspend fun setScheduleEnabled(enabled: Boolean) {
        appSettingDao.insertSetting(
            AppSettingEntity(key = AppSettingKey.SCHEDULE_ENABLED, value = enabled.toString())
        )
    }

    suspend fun getLastFiredSchedule(): String? =
        appSettingDao.getSettingValue(AppSettingKey.SCHEDULE_LAST_FIRED)

    suspend fun setLastFiredSchedule(value: String) {
        appSettingDao.insertSetting(
            AppSettingEntity(key = AppSettingKey.SCHEDULE_LAST_FIRED, value = value)
        )
    }
}
