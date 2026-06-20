package com.focuslock.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_domains",
    indices = [
        Index(value = ["domain"], unique = true),
        Index(value = ["category_key"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = BlockedCategoryEntity::class,
            parentColumns = ["category_key"],
            childColumns = ["category_key"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BlockedDomainEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "domain")
    val domain: String,
    
    @ColumnInfo(name = "category_key")
    val categoryKey: String,
    
    @ColumnInfo(name = "is_auto_blocked", defaultValue = "0")
    val isAutoBlocked: Boolean = false,
    
    @ColumnInfo(name = "matched_keyword")
    val matchedKeyword: String? = null,
    
    @ColumnInfo(name = "auto_blocked_at")
    val autoBlockedAt: Long? = null,

    @ColumnInfo(name = "block_status", defaultValue = "'PERMANENT'")
    val blockStatus: String = "PERMANENT",  // PERMANENT, TEMPORARY, CLEAN_CACHE

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "source", defaultValue = "NULL")
    val source: String? = null
)
