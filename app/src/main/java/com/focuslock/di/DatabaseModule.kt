package com.focuslock.di

import android.content.Context
import androidx.room.Room
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.dao.AppSettingDao
import com.focuslock.data.local.dao.BlockedCategoryDao
import com.focuslock.data.local.dao.BlockedDomainDao
import com.focuslock.data.local.dao.BlockedKeywordDao
import com.focuslock.data.local.dao.SessionDao
import com.focuslock.data.local.dao.TrustedContactDao
import com.focuslock.data.local.dao.UnlockRequestDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Safe column rename for SQLite < 3.25.0 (Android < 11 / API < 30)
        // 1. Create temporary table with the exact schema of version 3
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `trusted_contacts_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `email` TEXT NOT NULL, 
                `is_verified` INTEGER NOT NULL, 
                `verified_at` INTEGER, 
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // 2. Copy data mapping old `phone_number` to `email`
        db.execSQL(
            """
            INSERT INTO `trusted_contacts_new` (`id`, `email`, `is_verified`, `verified_at`, `created_at`)
            SELECT `id`, `phone_number`, `is_verified`, `verified_at`, `created_at` FROM `trusted_contacts`
            """.trimIndent()
        )
        // 3. Drop old table
        db.execSQL("DROP TABLE `trusted_contacts`")
        // 4. Rename new table to match the old table name
        db.execSQL("ALTER TABLE `trusted_contacts_new` RENAME TO `trusted_contacts`")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_trusted_contacts_email ON trusted_contacts(email)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_sessions_single_active ON sessions(status) WHERE status = 'ACTIVE'"
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideFocusDatabase(
        @ApplicationContext context: Context
    ): FocusDatabase {
        return Room.databaseBuilder(
            context,
            FocusDatabase::class.java,
            FocusDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideSessionDao(database: FocusDatabase): SessionDao {
        return database.sessionDao()
    }
    
    @Provides
    @Singleton
    fun provideBlockedKeywordDao(database: FocusDatabase): BlockedKeywordDao {
        return database.blockedKeywordDao()
    }
    
    @Provides
    @Singleton
    fun provideBlockedCategoryDao(database: FocusDatabase): BlockedCategoryDao {
        return database.blockedCategoryDao()
    }
    
    @Provides
    @Singleton
    fun provideBlockedDomainDao(database: FocusDatabase): BlockedDomainDao {
        return database.blockedDomainDao()
    }
    
    @Provides
    @Singleton
    fun provideTrustedContactDao(database: FocusDatabase): TrustedContactDao {
        return database.trustedContactDao()
    }
    
    @Provides
    @Singleton
    fun provideUnlockRequestDao(database: FocusDatabase): UnlockRequestDao {
        return database.unlockRequestDao()
    }
    
    @Provides
    @Singleton
    fun provideAppSettingDao(database: FocusDatabase): AppSettingDao {
        return database.appSettingDao()
    }
}
