package com.focuslock.domain.usecase

import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * Checks if the currently active session has expired.
 * If yes, performs full cleanup: DB status update + cancel WorkManager timers + stop runtime.
 * Returns true if a stale session was found and cleaned, false otherwise.
 *
 * Use this instead of calling DeactivateSessionUseCase directly from activation
 * or routing logic, to avoid tight coupling.
 */
class CleanupStaleSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val deactivateSessionUseCase: DeactivateSessionUseCase
) {
    suspend operator fun invoke(): Boolean {
        // Get any session currently marked as ACTIVE in the database
        val activeSession = sessionRepository.getActiveSessionSync() ?: return false

        // If the session hasn't expired yet, it's legitimately active — do nothing
        if (activeSession.expiresAt > System.currentTimeMillis()) return false

        // Session is stale — clean up DB + WorkManager timers + VPN runtime
        deactivateSessionUseCase(activeSession.id, SessionStatus.COMPLETED)
        return true
    }
}
