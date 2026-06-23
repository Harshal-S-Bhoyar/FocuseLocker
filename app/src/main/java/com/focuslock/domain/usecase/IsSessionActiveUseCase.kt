package com.focuslock.domain.usecase

import com.focuslock.data.repository.SessionRepository
import javax.inject.Inject

class IsSessionActiveUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase
) {
    suspend operator fun invoke(): Boolean {
        // Clean up any stale session first. If cleaned, session is no longer active.
        val cleaned = cleanupStaleSessionUseCase()
        if (cleaned) return false

        // Check if a genuinely active (non-expired) session exists in DB
        return sessionRepository.hasActiveSession()
    }
}
