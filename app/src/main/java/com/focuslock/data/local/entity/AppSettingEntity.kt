package com.focuslock.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey
    val key: String,
    
    @ColumnInfo(name = "value")
    val value: String
)

object AppSettingKey {
    const val ONBOARDING_COMPLETE = "onboarding_complete"
    const val DEVICE_ADMIN_ACTIVE = "device_admin_active"
    const val ACCESSIBILITY_ACTIVE = "accessibility_active"
    const val VPN_ACTIVE = "vpn_active"
    const val FIRST_INSTALL_DATE = "first_install_date"
    const val DOMAINS_SEEDED = "domains_seeded"
}
