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

    private val preferences = try {
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            androidx.security.crypto.MasterKey.Builder(context).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build(),
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to standard if keystore is corrupted or on old device
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    override fun isSessionActive(): Boolean = preferences.getBoolean(KEY_SESSION_ACTIVE, false)

    override fun setSessionActive(isActive: Boolean) {
        preferences.edit().putBoolean(KEY_SESSION_ACTIVE, isActive).apply()
    }

    override fun getBreachCount(): Int = preferences.getInt(KEY_BREACH_COUNT, 0)

    override fun incrementBreachCount() {
        val current = getBreachCount()
        preferences.edit().putInt(KEY_BREACH_COUNT, current + 1).apply()
    }

    override fun resetBreachCount() {
        preferences.edit().putInt(KEY_BREACH_COUNT, 0).apply()
    }

    override fun getPenaltyEndTime(): Long = preferences.getLong(KEY_PENALTY_END_TIME, 0L)

    override fun setPenaltyEndTime(timeMillis: Long) {
        preferences.edit().putLong(KEY_PENALTY_END_TIME, timeMillis).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "focuslock_enforcement_state"
        const val KEY_SESSION_ACTIVE = "session_active"
        const val KEY_BREACH_COUNT = "breach_count"
        const val KEY_PENALTY_END_TIME = "penalty_end_time"
    }
}
