package com.focuslock.data.session

import android.content.Context
import com.focuslock.domain.session.SessionEnforcementStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesSessionEnforcementStateStore @Inject constructor(
    @ApplicationContext context: Context
) : SessionEnforcementStateStore {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun isSessionActive(): Boolean = preferences.getBoolean(KEY_SESSION_ACTIVE, false)

    override fun setSessionActive(isActive: Boolean) {
        preferences.edit().putBoolean(KEY_SESSION_ACTIVE, isActive).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "focuslock_enforcement_state"
        const val KEY_SESSION_ACTIVE = "session_active"
    }
}
