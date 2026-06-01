package com.focuslock.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_categories",
    indices = [Index(value = ["category_key"], unique = true)]
)
data class BlockedCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "category_key")
    val categoryKey: String, // SOCIAL_MEDIA | ADULT | GAMBLING | NEWS | GAMING | SHOPPING
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false
)

object CategoryKey {
    const val SOCIAL_MEDIA = "SOCIAL_MEDIA"
    const val ADULT = "ADULT"
    const val GAMBLING = "GAMBLING"
    const val NEWS = "NEWS"
    const val GAMING = "GAMING"
    const val SHOPPING = "SHOPPING"
    
    val ALL = listOf(SOCIAL_MEDIA, ADULT, GAMBLING, NEWS, GAMING, SHOPPING)
}
