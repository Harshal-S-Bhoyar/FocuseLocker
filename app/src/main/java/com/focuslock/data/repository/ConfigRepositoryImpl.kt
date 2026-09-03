package com.focuslock.data.repository

import android.content.Context
import com.focuslock.domain.repository.ConfigRepository
import com.focuslock.util.PrefsKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ConfigRepository {

    override suspend fun getNextDnsProfileId(): String {
        val sharedPreferences = context.getSharedPreferences(PrefsKeys.FILE, Context.MODE_PRIVATE)
        return sharedPreferences.getString("NEXTDNS_PROFILE_ID", com.focuslock.BuildConfig.NEXTDNS_PROFILE_ID) ?: ""
    }

    override suspend fun isAutoModeEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PrefsKeys.FILE, Context.MODE_PRIVATE)
        return prefs.getBoolean(PrefsKeys.AUTO_MODE_ENABLED, false)
    }
}
