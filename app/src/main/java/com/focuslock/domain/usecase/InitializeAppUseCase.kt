package com.focuslock.domain.usecase

import com.focuslock.domain.repository.AppSettingsRepository
import com.focuslock.domain.repository.BlockingRepository
import javax.inject.Inject

/**
 * Runs once on first launch.
 * Seeds default app settings and domain/category data.
 */
class InitializeAppUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val blockingRepository: BlockingRepository,
    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase
) {
    suspend operator fun invoke() {
        cleanupStaleSessionUseCase()  // Purge any stale ACTIVE sessions from DB before routing
        appSettingsRepository.initializeDefaultSettings()
        blockingRepository.seedDomainDataIfNeeded()
    }
}
