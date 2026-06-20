package com.focuslock.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.focuslock.data.local.dao.AppSettingDao
import com.focuslock.data.local.dao.BlockedCategoryDao
import com.focuslock.data.local.dao.BlockedDomainDao
import com.focuslock.data.local.dao.BlockedKeywordDao
import com.focuslock.data.local.dao.SessionDao
import com.focuslock.data.local.dao.TrustedContactDao
import com.focuslock.data.local.dao.UnlockRequestDao
import com.focuslock.data.local.entity.AppSettingEntity
import com.focuslock.data.local.entity.BlockedCategoryEntity
import com.focuslock.data.local.entity.BlockedDomainEntity
import com.focuslock.data.local.entity.BlockedKeywordEntity
import com.focuslock.data.local.entity.SessionEntity
import com.focuslock.data.local.entity.TrustedContactEntity
import com.focuslock.data.local.entity.UnlockRequestEntity

@Database(
    entities = [
        SessionEntity::class,
        BlockedKeywordEntity::class,
        BlockedCategoryEntity::class,
        BlockedDomainEntity::class,
        TrustedContactEntity::class,
        UnlockRequestEntity::class,
        AppSettingEntity::class
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        androidx.room.AutoMigration(from = 4, to = 5),
        androidx.room.AutoMigration(from = 5, to = 6),
        androidx.room.AutoMigration(from = 6, to = 7)
    ]
)
abstract class FocusDatabase : RoomDatabase() {
    
    abstract fun sessionDao(): SessionDao
    abstract fun blockedKeywordDao(): BlockedKeywordDao
    abstract fun blockedCategoryDao(): BlockedCategoryDao
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun unlockRequestDao(): UnlockRequestDao
    abstract fun appSettingDao(): AppSettingDao
    
    companion object {
        const val DATABASE_NAME = "focus_lock.db"
    }
}
