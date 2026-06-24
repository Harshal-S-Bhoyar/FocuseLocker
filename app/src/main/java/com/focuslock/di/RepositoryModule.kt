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
}
