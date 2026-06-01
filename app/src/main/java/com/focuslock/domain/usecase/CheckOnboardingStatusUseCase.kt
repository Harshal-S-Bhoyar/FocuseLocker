package com.focuslock.domain.usecase

import com.focuslock.domain.repository.AppSettingsRepository
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.repository.TrustedContactRepository
import javax.inject.Inject

data class OnboardingStatus(
    val isOnboardingComplete: Boolean,
    val hasVerifiedContact: Boolean,
    val hasActiveSession: Boolean
)

/**
 * Determines where to route the user on app launch.
 * Called from MainActivity to pick the correct start destination.
 */
class CheckOnboardingStatusUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val trustedContactRepository: TrustedContactRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): OnboardingStatus {
        return OnboardingStatus(
            isOnboardingComplete = appSettingsRepository.isOnboardingComplete(),
            hasVerifiedContact = trustedContactRepository.hasVerifiedContact(),
            hasActiveSession = sessionRepository.hasActiveSession()
        )
    }
}
