package com.focuslock.di

import com.focuslock.data.repository.AppSettingsRepositoryImpl
import com.focuslock.data.permission.AndroidAppPermissionStatusProvider
import com.focuslock.data.repository.BlockingRepositoryImpl
import com.focuslock.data.repository.SessionRepositoryImpl
import com.focuslock.data.repository.TrustedContactRepositoryImpl
import com.focuslock.data.repository.UnlockRepositoryImpl
import com.focuslock.data.session.AndroidSessionRuntimeController
import com.focuslock.data.session.AndroidSessionProtectionStatusProvider
import com.focuslock.data.session.PreferencesSessionEnforcementStateStore
import com.focuslock.domain.repository.AppSettingsRepository
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.domain.repository.BlockingRepository
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.repository.TrustedContactRepository
import com.focuslock.domain.repository.UnlockRepository
import com.focuslock.domain.session.SessionEnforcementStateStore
import com.focuslock.domain.session.SessionRuntimeController
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
    abstract fun bindSessionRepository(
        impl: SessionRepositoryImpl
    ): SessionRepository

    @Binds
    @Singleton
    abstract fun bindBlockingRepository(
        impl: BlockingRepositoryImpl
    ): BlockingRepository

    @Binds
    @Singleton
    abstract fun bindTrustedContactRepository(
        impl: TrustedContactRepositoryImpl
    ): TrustedContactRepository

    @Binds
    @Singleton
    abstract fun bindUnlockRepository(
        impl: UnlockRepositoryImpl
    ): UnlockRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(
        impl: AppSettingsRepositoryImpl
    ): AppSettingsRepository

    @Binds
    @Singleton
    abstract fun bindSessionWorkerScheduler(
        impl: com.focuslock.data.worker.SessionWorkerSchedulerImpl
    ): com.focuslock.domain.worker.SessionWorkerScheduler

    @Binds
    @Singleton
    abstract fun bindSessionRuntimeController(
        impl: AndroidSessionRuntimeController
    ): SessionRuntimeController

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
}
