package com.focuslock.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focuslock.data.local.entity.ScheduleWindowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule_windows ORDER BY weekday, start_minutes")
    fun getAllWindows(): Flow<List<ScheduleWindowEntity>>

    @Query("SELECT * FROM schedule_windows ORDER BY weekday, start_minutes")
    suspend fun getAllWindowsSync(): List<ScheduleWindowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWindow(window: ScheduleWindowEntity): Long

    @Query("DELETE FROM schedule_windows WHERE id = :id")
    suspend fun deleteWindow(id: Long)

    @Query("DELETE FROM schedule_windows")
    suspend fun deleteAllWindows()
}
