package com.focuslock.domain.usecase

import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.BlockingRepository
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focuslock.worker.ListSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Runs once on first launch.
 * Seeds default app settings and domain/category data.
 */
class InitializeAppUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val blockingRepository: BlockingRepository,
    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase,
    @ApplicationContext private val context: Context
) {
    suspend operator fun invoke() {
        cleanupStaleSessionUseCase()  // Purge any stale ACTIVE sessions from DB before routing
        appSettingsRepository.initializeDefaultSettings()
        blockingRepository.seedDomainDataIfNeeded()
        
        scheduleListSyncWorker(context)
    }

    private fun scheduleListSyncWorker(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
                
            val syncRequest = PeriodicWorkRequestBuilder<ListSyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
                
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "stevenblack_list_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        } catch (e: Exception) {
            // Ignore if WorkManager is not initialized (e.g. in unit tests)
        }
    }
}
