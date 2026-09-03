package com.focuslock.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "status")
    val status: String, // ACTIVE | COMPLETED | CANCELLED
    
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,
    
    @ColumnInfo(name = "unlocked_early")
    val unlockedEarly: Boolean = false,
    
    @ColumnInfo(name = "unlock_requested_at")
    val unlockRequestedAt: Long? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "selected_category_keys", defaultValue = "''")
    val selectedCategoryKeys: String = ""
)

object SessionStatus {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
}
