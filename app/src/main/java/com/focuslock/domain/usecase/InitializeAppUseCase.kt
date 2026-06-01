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
    private val blockingRepository: BlockingRepository
) {
    suspend operator fun invoke() {
        appSettingsRepository.initializeDefaultSettings()
        blockingRepository.seedDomainDataIfNeeded()
    }
}
