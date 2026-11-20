package com.focuslock.domain.usecase

import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.BlockingRepository
import com.focuslock.domain.repository.BackgroundSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
        // Stale cleanup gates everything: a failure here aborts the whole init.
        cleanupStaleSessionUseCase()
        coroutineScope {
            val settings = async { withTimeout(STEP_TIMEOUT_MS) { appSettingsRepository.initializeDefaultSettings() } }
            val seed = async { seedOnceWithinBudget() }
            settings.await()
            seed.await()
        }
        backgroundSyncScheduler.schedulePeriodicSync()
    }

    private suspend fun seedOnceWithinBudget() {
        // Fast path: seeded launches skip the lock entirely.
        if (appSettingsRepository.isDomainSeeded()) return
        // Single-flight: concurrent inits serialize here; the second sees the flag set.
        // Timeout covers the lock wait too, so queued inits stay bounded.
        withTimeout(SEED_TIMEOUT_MS) {
            SEED_MUTEX.withLock {
                if (appSettingsRepository.isDomainSeeded()) return@withTimeout
                blockingRepository.seedDomainDataIfNeeded()
                appSettingsRepository.setDomainSeeded()
            }
        }
    }

    companion object {
        const val STEP_TIMEOUT_MS = 15_000L
        const val SEED_TIMEOUT_MS = 120_000L
        private val SEED_MUTEX = Mutex()
    }
}
