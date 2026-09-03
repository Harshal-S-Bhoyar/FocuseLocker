package com.focuslock.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = FocusDatabase.Migration7To8Spec::class)
    ]
)
abstract class FocusDatabase : RoomDatabase() {
    
    // ponytail: backfill selected_category_keys for existing active sessions from currently active categories
    class Migration7To8Spec : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                UPDATE sessions 
                SET selected_category_keys = (
                    SELECT COALESCE(GROUP_CONCAT(category_key, ','), '') 
                    FROM blocked_categories 
                    WHERE is_active = 1
                )
                WHERE status = 'ACTIVE' AND (selected_category_keys IS NULL OR selected_category_keys = '')
                """.trimIndent()
            )
        }
    }
    
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
