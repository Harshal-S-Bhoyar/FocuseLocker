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
