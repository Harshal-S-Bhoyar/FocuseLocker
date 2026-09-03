package com.focuslock.di

import com.focuslock.data.permission.AndroidAppPermissionStatusProvider
import com.focuslock.data.session.AndroidSessionProtectionStatusProvider
import com.focuslock.data.session.PreferencesSessionEnforcementStateStore
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.domain.session.SessionEnforcementStateStore
import com.focuslock.domain.session.SessionProtectionStatusProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSessionEnforcementStateStore(
        impl: PreferencesSessionEnforcementStateStore
    ): SessionEnforcementStateStore

    @Binds
    @Singleton
    abstract fun bindSessionProtectionStatusProvider(
        impl: AndroidSessionProtectionStatusProvider
    ): SessionProtectionStatusProvider

    @Binds
    @Singleton
    abstract fun bindAppPermissionStatusProvider(
        impl: AndroidAppPermissionStatusProvider
    ): AppPermissionStatusProvider

    @Binds
    @Singleton
    abstract fun bindPageSignalFetcher(
        impl: com.focuslock.data.repository.PageSignalFetcherImpl
    ): com.focuslock.domain.repository.PageSignalFetcher

    @Binds
    @Singleton
    abstract fun bindSynonymRepository(
        impl: com.focuslock.data.repository.SynonymRepositoryImpl
    ): com.focuslock.domain.repository.SynonymRepository

    @Binds
    @Singleton
    abstract fun bindConfigRepository(
        impl: com.focuslock.data.repository.ConfigRepositoryImpl
    ): com.focuslock.domain.repository.ConfigRepository

    @Binds
    @Singleton
    abstract fun bindDoHRepository(
        impl: com.focuslock.data.repository.DoHRepositoryImpl
    ): com.focuslock.domain.repository.DoHRepository

    @Binds
    @Singleton
    abstract fun bindBackgroundSyncScheduler(
        impl: com.focuslock.data.scheduler.WorkManagerSyncScheduler
    ): com.focuslock.domain.repository.BackgroundSyncScheduler
}
