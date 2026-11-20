package com.focuslock.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_windows",
    indices = [Index(value = ["weekday"])]
)
data class ScheduleWindowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "weekday")
    val weekday: Int,

    @ColumnInfo(name = "start_minutes")
    val startMinutes: Int,

    @ColumnInfo(name = "end_minutes")
    val endMinutes: Int
)
