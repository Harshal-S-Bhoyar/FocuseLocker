package com.focuslock.domain.usecase

import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.BlockingRepository
import com.focuslock.domain.repository.BackgroundSyncScheduler
import javax.inject.Inject

/**
 * Runs once on first launch.
 * Seeds default app settings and domain/category data.
 */
class InitializeAppUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val blockingRepository: BlockingRepository,
    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase,
    private val backgroundSyncScheduler: BackgroundSyncScheduler
) {
    suspend operator fun invoke() {
        cleanupStaleSessionUseCase()  // Purge any stale ACTIVE sessions from DB before routing
        appSettingsRepository.initializeDefaultSettings()
        
        // ponytail: skip 3-8s blocking I/O if domains are already seeded
        if (!appSettingsRepository.isDomainSeeded()) {
            blockingRepository.seedDomainDataIfNeeded()
            appSettingsRepository.setDomainSeeded()
        }
        
        backgroundSyncScheduler.schedulePeriodicSync()
    }
}
